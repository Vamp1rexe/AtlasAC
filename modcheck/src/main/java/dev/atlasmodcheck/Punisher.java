package dev.atlasmodcheck;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Kicks (mit Eskalation) und Bans fuer die Mod-Pruefung. Gespeichert in bans.yml. */
public final class Punisher implements Listener {
	public record Ban(String id, String target, String name, String reason, long created, long expires, boolean ip) {
		boolean active(long now) {
			return expires < 0 || now < expires;
		}
	}

	private static final String ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private final AtlasModCheck plugin;
	private final File file;
	private final SecureRandom random = new SecureRandom();
	private final Map<String, Ban> bans = new ConcurrentHashMap<>();
	private final Map<UUID, List<Long>> kicks = new ConcurrentHashMap<>();

	public Punisher(AtlasModCheck plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "bans.yml");
	}

	public void load() {
		bans.clear();
		kicks.clear();
		YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection b = y.getConfigurationSection("bans");
		if (b != null) {
			for (String id : b.getKeys(false)) {
				ConfigurationSection s = b.getConfigurationSection(id);
				if (s == null) continue;
				bans.put(id, new Ban(id, s.getString("target", ""), s.getString("name", "?"), s.getString("reason", ""),
						s.getLong("created"), s.getLong("expires", -1), s.getBoolean("ip", false)));
			}
		}
		ConfigurationSection k = y.getConfigurationSection("kicks");
		if (k != null) {
			for (String id : k.getKeys(false)) {
				try {
					kicks.put(UUID.fromString(id), new ArrayList<>(k.getLongList(id)));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
	}

	private synchronized void save() {
		YamlConfiguration y = new YamlConfiguration();
		long now = System.currentTimeMillis();
		for (Ban b : bans.values()) {
			if (!b.active(now)) continue;
			String p = "bans." + b.id() + ".";
			y.set(p + "target", b.target());
			y.set(p + "name", b.name());
			y.set(p + "reason", b.reason());
			y.set(p + "created", b.created());
			y.set(p + "expires", b.expires());
			y.set(p + "ip", b.ip());
		}
		for (Map.Entry<UUID, List<Long>> e : kicks.entrySet()) y.set("kicks." + e.getKey(), e.getValue());
		try {
			y.save(file);
		} catch (IOException ex) {
			plugin.getLogger().severe("bans.yml: " + ex.getMessage());
		}
	}

	private void saveAsync() {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
	}

	public static String ipOf(Player p) {
		InetSocketAddress a = p.getAddress();
		return a == null ? null : a.getAddress().getHostAddress();
	}

	private String newId() {
		String id;
		do {
			StringBuilder b = new StringBuilder(6);
			for (int i = 0; i < 6; i++) b.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
			id = b.toString();
		} while (bans.containsKey(id));
		return id;
	}

	// ───────────────────────── Strafen ─────────────────────────

	public void kick(Player p, String reason, boolean announce) {
		kicks.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(System.currentTimeMillis());
		saveAsync();
		p.kick(Text.mm(fill(plugin.getConfig().getString("screens.kick", "<red>Kicked\n%reason%"), reason, "", "", "")));
		plugin.alerts().send("%prefix%<red>" + p.getName() + " <gray>wurde gekickt. <dark_gray>(" + Text.escape(reason) + ")");
	}

	/** Kick – der x-te Kick im Zeitfenster wird zum Ban (escalation in der config). */
	public void kickEscalating(Player p, String reason) {
		ConfigurationSection c = plugin.getConfig().getConfigurationSection("escalation");
		if (c != null && c.getBoolean("enabled", true)) {
			long window = Durations.parse(c.getString("within", "24h"));
			int needed = Math.max(1, c.getInt("kicks", 3));
			long now = System.currentTimeMillis();
			List<Long> list = kicks.getOrDefault(p.getUniqueId(), List.of());
			long recent = list.stream().filter(t -> window <= 0 || now - t <= window).count();
			if (recent + 1 >= needed) {
				long d = Durations.parse(c.getString("duration", "7d"));
				ban(p, c.getString("reason", "Wiederholt verbotene Mods") , d == 0 ? 7L * 86_400_000L : d, false);
				return;
			}
		}
		kick(p, reason, true);
	}

	public void ban(Player p, String reason, long duration, boolean ip) {
		long now = System.currentTimeMillis();
		String target = ip ? ipOf(p) : p.getUniqueId().toString();
		if (target == null) target = p.getUniqueId().toString();
		Ban b = new Ban(newId(), target, p.getName(), reason, now, duration < 0 ? -1 : now + duration, ip);
		bans.put(b.id(), b);
		if (ip) {
			// Account zusaetzlich bannen
			Ban acc = new Ban(newId(), p.getUniqueId().toString(), p.getName(), reason, now, b.expires(), false);
			bans.put(acc.id(), acc);
		}
		kicks.remove(p.getUniqueId());
		saveAsync();
		p.kick(screen(b));
		String dur = duration < 0 ? "Permanent" : Durations.format(duration);
		plugin.alerts().send("%prefix%<red>" + p.getName() + " <gray>wurde " + (ip ? "IP-" : "") + "gebannt (" + dur + "). <dark_gray>("
				+ Text.escape(reason) + ", #" + b.id() + ")");
		String pub = plugin.getConfig().getString("public-ban-broadcast", "");
		if (!pub.isBlank()) plugin.alerts().broadcast(pub.replace("%player%", p.getName()).replace("%duration%", dur));
	}

	public int unban(String nameOrUuid) {
		int n = 0;
		for (Ban b : List.copyOf(bans.values())) {
			if (b.target().equalsIgnoreCase(nameOrUuid) || b.name().equalsIgnoreCase(nameOrUuid)) {
				bans.remove(b.id());
				n++;
			}
		}
		if (n > 0) saveAsync();
		return n;
	}

	private net.kyori.adventure.text.Component screen(Ban b) {
		String dur = b.expires() < 0 ? "Permanent" : Durations.format(b.expires() - System.currentTimeMillis());
		return Text.mm(fill(plugin.getConfig().getString("screens.ban", "<red>Banned\n%reason%"), b.reason(), dur,
				Durations.date(b.expires()), b.id()));
	}

	private static String fill(String t, String reason, String duration, String expires, String id) {
		return t.replace("%reason%", Text.escape(reason)).replace("%duration%", duration)
				.replace("%expires%", expires).replace("%id%", id);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPreLogin(AsyncPlayerPreLoginEvent e) {
		long now = System.currentTimeMillis();
		String uuid = e.getUniqueId().toString().toLowerCase(Locale.ROOT);
		String ip = e.getAddress().getHostAddress();
		for (Ban b : bans.values()) {
			if (!b.active(now)) continue;
			if (b.target().equalsIgnoreCase(uuid) || (b.ip() && b.target().equals(ip))) {
				e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, screen(b));
				return;
			}
		}
	}
}
