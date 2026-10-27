package dev.atlasac.punish;

import dev.atlasac.AtlasAC;
import dev.atlasac.punish.Punishment.Type;
import dev.atlasac.util.Durations;
import dev.atlasac.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Eigenes Strafsystem: Ban, Tempban, IP-Ban, Temp-IP-Ban, Mute, Kick, Historie,
 * Eskalation bei wiederholten Atlas-Kicks und Alt-Account-Erkennung ueber die IP.
 * Gespeichert in plugins/AtlasAC/data.yml.
 */
public final class PunishmentManager implements Listener {
	private static final Pattern IP = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$|^[0-9a-fA-F:]+:[0-9a-fA-F:]*$");
	private static final String ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	public static final String STAFF_ATLAS = "Atlas";

	private final AtlasAC plugin;
	private final File file;
	private final SecureRandom random = new SecureRandom();

	private final Map<String, Punishment> punishments = new ConcurrentHashMap<>();
	private final Map<UUID, String> names = new ConcurrentHashMap<>();
	private final Map<UUID, String> ips = new ConcurrentHashMap<>();
	private final Map<String, UUID> byName = new ConcurrentHashMap<>();
	private final Map<String, Set<UUID>> accountsByIp = new ConcurrentHashMap<>();

	public PunishmentManager(AtlasAC plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "data.yml");
	}

	// ───────────────────────── Laden / Speichern ─────────────────────────

	public void load() {
		YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection players = y.getConfigurationSection("players");
		if (players != null) {
			for (String key : players.getKeys(false)) {
				try {
					UUID uuid = UUID.fromString(key);
					remember(uuid, players.getString(key + ".name"), players.getString(key + ".ip"));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		ConfigurationSection ps = y.getConfigurationSection("punishments");
		if (ps != null) {
			for (String id : ps.getKeys(false)) {
				ConfigurationSection s = ps.getConfigurationSection(id);
				if (s == null) continue;
				try {
					punishments.put(id, new Punishment(id, Type.valueOf(s.getString("type", "BAN")), s.getString("target", ""),
							s.getString("name", "?"), s.getString("reason", ""), s.getString("staff", "?"),
							s.getLong("created"), s.getLong("expires", -1), s.getBoolean("active", true)));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
	}

	public synchronized void save() {
		YamlConfiguration y = new YamlConfiguration();
		for (Map.Entry<UUID, String> e : names.entrySet()) {
			y.set("players." + e.getKey() + ".name", e.getValue());
			String ip = ips.get(e.getKey());
			if (ip != null) y.set("players." + e.getKey() + ".ip", ip);
		}
		for (Punishment p : punishments.values()) {
			String b = "punishments." + p.id() + ".";
			y.set(b + "type", p.type().name());
			y.set(b + "target", p.target());
			y.set(b + "name", p.name());
			y.set(b + "reason", p.reason());
			y.set(b + "staff", p.staff());
			y.set(b + "created", p.created());
			y.set(b + "expires", p.expires());
			y.set(b + "active", p.active());
		}
		try {
			y.save(file);
		} catch (IOException ex) {
			plugin.getLogger().severe("data.yml konnte nicht gespeichert werden: " + ex.getMessage());
		}
	}

	private void saveAsync() {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
	}

	private void remember(UUID uuid, String name, String ip) {
		if (name != null) {
			String old = names.put(uuid, name);
			if (old != null && !old.equalsIgnoreCase(name)) byName.remove(old.toLowerCase(Locale.ROOT));
			byName.put(name.toLowerCase(Locale.ROOT), uuid);
		}
		if (ip != null) {
			String old = ips.put(uuid, ip);
			if (old != null && !old.equals(ip)) {
				Set<UUID> set = accountsByIp.get(old);
				if (set != null) set.remove(uuid);
			}
			accountsByIp.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(uuid);
		}
	}

	// ───────────────────────── Ziele aufloesen ─────────────────────────

	public record Target(UUID uuid, String name, String ip) {
	}

	public static boolean isIp(String s) {
		return IP.matcher(s).matches();
	}

	public static String ipOf(Player p) {
		InetSocketAddress a = p.getAddress();
		return a == null ? null : a.getAddress().getHostAddress();
	}

	/** Spielername (online/offline) oder IP. */
	public Target resolve(String input) {
		if (isIp(input)) return new Target(null, input, input);
		Player online = Bukkit.getPlayerExact(input);
		if (online != null) return new Target(online.getUniqueId(), online.getName(), ipOf(online));
		UUID known = byName.get(input.toLowerCase(Locale.ROOT));
		if (known != null) return new Target(known, names.getOrDefault(known, input), ips.get(known));
		OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(input);
		if (cached != null) return new Target(cached.getUniqueId(), cached.getName() != null ? cached.getName() : input, ips.get(cached.getUniqueId()));
		return null;
	}

	// ───────────────────────── Strafen ─────────────────────────

	private String newId() {
		String id;
		do {
			StringBuilder b = new StringBuilder(6);
			for (int i = 0; i < 6; i++) b.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
			id = b.toString();
		} while (punishments.containsKey(id));
		return id;
	}

	private Punishment create(Type type, String target, String name, String reason, String staff, long duration) {
		long now = System.currentTimeMillis();
		long expires = type == Type.KICK ? 0 : duration < 0 ? -1 : now + duration;
		Punishment p = new Punishment(newId(), type, target, name, reason, staff, now, expires, type != Type.KICK);
		punishments.put(p.id(), p);
		saveAsync();
		return p;
	}

	public Punishment ban(Target t, String reason, String staff, long duration) {
		return ban(t, reason, staff, duration, true);
	}

	public Punishment ban(Target t, String reason, String staff, long duration, boolean announce) {
		// alte Bans ersetzen
		for (Punishment old : List.copyOf(punishments.values())) {
			if (old.type() == Type.BAN && old.active() && old.target().equals(t.uuid().toString())) {
				punishments.put(old.id(), old.deactivate());
			}
		}
		Punishment p = create(Type.BAN, t.uuid().toString(), t.name(), reason, staff, duration);
		Player online = Bukkit.getPlayer(t.uuid());
		if (online != null) online.kick(screen(p));
		if (announce) announce(t.name(), staff, duration < 0 ? "permanent gebannt" : "fuer " + Durations.format(duration) + " gebannt", reason);
		return p;
	}

	public Punishment ipBan(Target t, String reason, String staff, long duration) {
		String ip = t.ip();
		for (Punishment old : List.copyOf(punishments.values())) {
			if (old.type() == Type.IPBAN && old.active() && old.target().equals(ip)) punishments.put(old.id(), old.deactivate());
		}
		Punishment p = create(Type.IPBAN, ip, t.name(), reason, staff, duration);
		Component screen = screen(p);
		for (Player online : Bukkit.getOnlinePlayers()) {
			if (ip.equals(ipOf(online))) online.kick(screen);
		}
		announce(t.name(), staff, duration < 0 ? "permanent IP-gebannt" : "fuer " + Durations.format(duration) + " IP-gebannt", reason);
		return p;
	}

	public Punishment mute(Target t, String reason, String staff, long duration) {
		for (Punishment old : List.copyOf(punishments.values())) {
			if (old.type() == Type.MUTE && old.active() && old.target().equals(t.uuid().toString())) punishments.put(old.id(), old.deactivate());
		}
		Punishment p = create(Type.MUTE, t.uuid().toString(), t.name(), reason, staff, duration);
		Player online = Bukkit.getPlayer(t.uuid());
		if (online != null) online.sendMessage(muteMessage(p));
		announce(t.name(), staff, duration < 0 ? "permanent gemutet" : "fuer " + Durations.format(duration) + " gemutet", reason);
		return p;
	}

	public Punishment kick(Player target, String reason, String staff, boolean announce) {
		Punishment p = create(Type.KICK, target.getUniqueId().toString(), target.getName(), reason, staff, 0);
		target.kick(screen(p));
		if (announce) announce(target.getName(), staff, "gekickt", reason);
		return p;
	}

	/** Automatische Strafe durch einen Check: Kick + Eskalation zum Tempban. */
	public void autoPunish(Player player, String check) {
		String reason = "Cheating erkannt (" + check + ")";
		ConfigurationSection ab = plugin.getConfig().getConfigurationSection("punishment.auto-ban");
		if (ab != null && ab.getBoolean("enabled", true)) {
			long window = Durations.parse(ab.getString("within", "24h"));
			int needed = Math.max(1, ab.getInt("kicks", 3));
			long now = System.currentTimeMillis();
			long kicks = punishments.values().stream()
					.filter(p -> p.type() == Type.KICK && STAFF_ATLAS.equals(p.staff())
							&& p.target().equals(player.getUniqueId().toString())
							&& (window <= 0 || now - p.created() <= window))
					.count();
			if (kicks + 1 >= needed) {
				long duration = Durations.parse(ab.getString("duration", "7d"));
				if (duration == 0) duration = 7L * 86_400_000L;
				ban(new Target(player.getUniqueId(), player.getName(), ipOf(player)),
						ab.getString("reason", "Wiederholte Cheat-Erkennung (%check%)").replace("%check%", check),
						STAFF_ATLAS, duration);
				return;
			}
		}
		kick(player, reason, STAFF_ATLAS, false);
		plugin.alerts().broadcast(plugin.getConfig().getString("punishment.broadcast", "")
				.replace("%player%", player.getName()).replace("%check%", check));
	}

	public int unban(UUID uuid) {
		return deactivate(Type.BAN, uuid.toString());
	}

	public int unbanIp(String ip) {
		return deactivate(Type.IPBAN, ip);
	}

	public int unmute(UUID uuid) {
		return deactivate(Type.MUTE, uuid.toString());
	}

	private int deactivate(Type type, String target) {
		int n = 0;
		for (Punishment p : List.copyOf(punishments.values())) {
			if (p.type() == type && p.active() && p.target().equals(target)) {
				punishments.put(p.id(), p.deactivate());
				n++;
			}
		}
		if (n > 0) saveAsync();
		return n;
	}

	public Punishment active(Type type, String target) {
		long now = System.currentTimeMillis();
		for (Punishment p : punishments.values()) {
			if (p.type() == type && p.target().equals(target) && p.inForce(now)) return p;
		}
		return null;
	}

	public List<Punishment> history(Target t) {
		List<Punishment> list = new ArrayList<>();
		for (Punishment p : punishments.values()) {
			boolean match = (t.uuid() != null && p.target().equals(t.uuid().toString()))
					|| (t.ip() != null && p.type() == Type.IPBAN && p.target().equals(t.ip()));
			if (match) list.add(p);
		}
		list.sort(Comparator.comparingLong(Punishment::created).reversed());
		return list;
	}

	public Punishment byId(String id) {
		return punishments.get(id.toUpperCase(Locale.ROOT).replace("#", ""));
	}

	// ───────────────────────── Texte ─────────────────────────

	public Component screen(Punishment p) {
		String key = switch (p.type()) {
			case BAN, MUTE -> "screens.ban";
			case IPBAN -> "screens.ipban";
			case KICK -> "screens.kick";
		};
		String template = plugin.getConfig().getString(key, "<red>Kicked by Atlas AntiCheat\n<gray>%reason%");
		return Text.mm(fill(template, p));
	}

	private Component muteMessage(Punishment p) {
		return Text.mm(fill(plugin.getConfig().getString("mute.message", "%prefix%<red>Du bist gemutet."), p)
				.replace("%prefix%", plugin.alerts().prefix()));
	}

	private static String fill(String template, Punishment p) {
		long remaining = p.permanent() ? -1 : Math.max(0, p.expires() - System.currentTimeMillis());
		return Text.apply(template, Map.of(
				"%reason%", Text.escape(p.reason()),
				"%staff%", Text.escape(p.staff()),
				"%duration%", p.permanent() ? "Permanent" : Durations.format(remaining),
				"%expires%", Durations.date(p.expires()),
				"%id%", p.id(),
				"%player%", p.name()));
	}

	private void announce(String player, String staff, String action, String reason) {
		String t = plugin.getConfig().getString("bans.broadcast", "");
		if (t.isBlank()) return;
		plugin.alerts().broadcast(Text.apply(t, Map.of("%player%", player, "%staff%", Text.escape(staff),
				"%action%", action, "%reason%", Text.escape(reason))));
	}

	// ───────────────────────── Events ─────────────────────────

	@EventHandler(priority = EventPriority.LOW)
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		Punishment ban = active(Type.BAN, event.getUniqueId().toString());
		if (ban == null) ban = active(Type.IPBAN, event.getAddress().getHostAddress());
		if (ban != null) event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, screen(ban));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		String ip = ipOf(p);
		remember(p.getUniqueId(), p.getName(), ip);
		saveAsync();
		if (ip == null || !plugin.getConfig().getBoolean("bans.alt-alerts", true)) return;
		Set<UUID> accounts = accountsByIp.get(ip);
		if (accounts == null) return;
		List<String> banned = new ArrayList<>();
		for (UUID other : accounts) {
			if (other.equals(p.getUniqueId())) continue;
			if (active(Type.BAN, other.toString()) != null) banned.add(names.getOrDefault(other, other.toString()));
		}
		if (!banned.isEmpty()) {
			plugin.alerts().send("%prefix%<gold>Alt-Account? <white>" + p.getName() + " <gray>teilt die IP mit gebannten Accounts: <red>"
					+ String.join(", ", banned));
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onChat(AsyncChatEvent event) {
		Punishment mute = active(Type.MUTE, event.getPlayer().getUniqueId().toString());
		if (mute == null) return;
		event.setCancelled(true);
		event.getPlayer().sendMessage(muteMessage(mute));
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onCommand(PlayerCommandPreprocessEvent event) {
		Punishment mute = active(Type.MUTE, event.getPlayer().getUniqueId().toString());
		if (mute == null) return;
		String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
		int colon = label.indexOf(':');
		if (colon >= 0) label = label.substring(colon + 1);
		if (plugin.getConfig().getStringList("mute.blocked-commands").contains(label)) {
			event.setCancelled(true);
			event.getPlayer().sendMessage(muteMessage(mute));
		}
	}
}
