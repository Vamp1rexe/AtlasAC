package dev.atlasac.mods;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import dev.atlasac.AtlasAC;
import dev.atlasac.punish.PunishmentManager;
import dev.atlasac.util.Durations;
import dev.atlasac.util.Text;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mod-/Hack-Client-Erkennung:
 * 1. Client-Brand (z.B. "fabric", "LiquidBounce") und registrierte Plugin-Channels (Mod-IDs) –
 *    unscharf verglichen, damit auch "AuToTootem_x3097" als "autototem" erkannt wird.
 * 2. Translation-Key-Probe: ein unsichtbares Schild mit Uebersetzungs-Keys von Mods wird kurz geoeffnet.
 *    Vanilla antwortet mit dem Fallback, Clients mit dem Mod geben den uebersetzten Text zurueck.
 */
public final class ModDetector implements Listener {
	private static final String FALLBACK = "#";
	private static final String SEP = "|";
	private static final int KEYS_PER_LINE = 10;

	public record ModRule(String id, String display, List<String> names, List<String> keys, int maxDistance,
						  String action, long duration, String reason) {
	}

	private record Probe(Location location, List<String> keys, long sentAt) {
	}

	/** Darf mit verbotenen Mods/Clients joinen (wird trotzdem erkannt und von allen Cheat-Checks geprueft). */
	public static final String PERM_BYPASS_MODS = "atlasac.bypass.mods";

	private final AtlasAC plugin;
	private final Map<String, ModRule> rules = new LinkedHashMap<>();
	private final Map<UUID, Probe> probes = new ConcurrentHashMap<>();
	private final Map<UUID, Set<String>> channels = new ConcurrentHashMap<>();
	private final Set<UUID> punished = ConcurrentHashMap.newKeySet();
	private final Set<UUID> warned = ConcurrentHashMap.newKeySet();
	/** Letztes Ergebnis der Schild-Probe pro Spieler (fuer die Uebersicht). */
	private final Map<UUID, ProbeResult> probeResults = new ConcurrentHashMap<>();

	public enum ProbeStatus {NOT_RUN, PENDING, CLEAN, DETECTED, NO_ANSWER}

	public record ProbeResult(ProbeStatus status, List<String> resolved, long time) {
	}

	public ModDetector(AtlasAC plugin) {
		this.plugin = plugin;
	}

	// ───────────────────────── Config ─────────────────────────

	public void reload() {
		loadWarned();
		rules.clear();
		ConfigurationSection mods = plugin.getConfig().getConfigurationSection("mod-detection.mods");
		if (mods == null) return;
		for (String id : mods.getKeys(false)) {
			ConfigurationSection m = mods.getConfigurationSection(id);
			if (m == null || !m.getBoolean("enabled", true)) continue;
			List<String> names = new ArrayList<>();
			for (String n : m.getStringList("names")) {
				String norm = normalize(n);
				if (!norm.isEmpty()) names.add(norm);
			}
			String action = m.getString("punishment", "ban").toLowerCase(Locale.ROOT);
			long duration = Durations.parse(m.getString("duration", "30d"));
			if (duration == 0) duration = -1;
			rules.put(id, new ModRule(id, m.getString("display", id), names, m.getStringList("translation-keys"),
					m.getInt("max-distance", -1), action, duration, m.getString("reason", "Unerlaubter Mod (%mod%)")));
		}
	}

	public Map<String, ModRule> rules() {
		return rules;
	}

	private boolean enabled() {
		return plugin.getConfig().getBoolean("mod-detection.enabled", true);
	}

	// ───────────────────────── Unscharfer Namensvergleich ─────────────────────────

	/** Kleinbuchstaben, Leetspeak aufloesen, alles ausser a-z entfernen. */
	public static String normalize(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
			switch (c) {
				case '0' -> b.append('o');
				case '1' -> b.append('i');
				case '3' -> b.append('e');
				case '4', '@' -> b.append('a');
				case '5', '$' -> b.append('s');
				case '7' -> b.append('t');
				default -> {
					if (c >= 'a' && c <= 'z') b.append(c);
				}
			}
		}
		return b.toString();
	}

	/** Kleinste Edit-Distanz zwischen dem Muster und irgendeinem Teilstring des Textes (Sellers). */
	static int substringDistance(String pattern, String text) {
		int m = pattern.length();
		int[] prev = new int[m + 1], cur = new int[m + 1];
		for (int i = 0; i <= m; i++) prev[i] = i;
		int best = prev[m];
		for (int j = 1; j <= text.length(); j++) {
			cur[0] = 0;
			char t = text.charAt(j - 1);
			for (int i = 1; i <= m; i++) {
				int cost = pattern.charAt(i - 1) == t ? 0 : 1;
				cur[i] = Math.min(Math.min(cur[i - 1] + 1, prev[i] + 1), prev[i - 1] + cost);
			}
			best = Math.min(best, cur[m]);
			int[] tmp = prev;
			prev = cur;
			cur = tmp;
		}
		return best;
	}

	private static int defaultDistance(String pattern) {
		return pattern.length() <= 4 ? 0 : pattern.length() <= 8 ? 1 : 2;
	}

	/** Prueft einen Namen (Brand, Channel, Mod-ID) gegen alle Regeln. */
	public ModRule match(String raw) {
		String text = normalize(raw);
		if (text.isEmpty()) return null;
		for (ModRule r : rules.values()) {
			for (String pattern : r.names()) {
				int max = r.maxDistance() >= 0 ? r.maxDistance() : defaultDistance(pattern);
				if (substringDistance(pattern, text) <= max) return r;
			}
		}
		return null;
	}

	// ───────────────────────── Ablauf ─────────────────────────

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChannel(PlayerRegisterChannelEvent event) {
		channels.computeIfAbsent(event.getPlayer().getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(event.getChannel());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		punished.remove(p.getUniqueId());
		if (!enabled()) return;
		long delay = Math.max(20, plugin.getConfig().getLong("mod-detection.delay-ticks", 60));
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (!p.isOnline() || p.hasPermission(AtlasAC.PERM_BYPASS)) return;
			scanIdentity(p);
			if (p.isOnline() && !punished.contains(p.getUniqueId())
					&& plugin.getConfig().getBoolean("mod-detection.translation-probe.enabled", true)) {
				sendProbe(p);
			}
		}, delay);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		channels.remove(id);
		probes.remove(id);
	}

	/** Brand + Channels pruefen. */
	public void scanIdentity(Player p) {
		String brand = p.getClientBrandName();
		Set<String> all = new LinkedHashSet<>(p.getListeningPluginChannels());
		Set<String> extra = channels.get(p.getUniqueId());
		if (extra != null) all.addAll(extra);

		if (brand != null) {
			ModRule r = match(brand);
			if (r != null) {
				detected(p, r, "Client-Brand \"" + brand + "\"");
				return;
			}
		}
		Set<String> namespaces = new LinkedHashSet<>();
		for (String ch : all) {
			int colon = ch.indexOf(':');
			namespaces.add(colon > 0 ? ch.substring(0, colon) : ch);
		}
		for (String ns : namespaces) {
			if (ns.equals("minecraft") || ns.equals("fabric") || ns.equals("c") || ns.equals("forge") || ns.equals("neoforge")) continue;
			ModRule r = match(ns);
			if (r != null) {
				detected(p, r, "Mod-Channel \"" + ns + "\"");
				return;
			}
		}
		// Gefaelschte Brand: "vanilla" melden, aber Mod-Loader-Channels registrieren
		if (brand != null && brand.equalsIgnoreCase("vanilla")) {
			for (String ns : namespaces) {
				if (ns.equals("fabric") || ns.equals("forge") || ns.equals("neoforge") || ns.contains("fabric")) {
					plugin.alerts().send("%prefix%<gold>Mods? <white>" + p.getName() + " <gray>meldet Brand \"vanilla\", hat aber Mod-Loader-Channels (" + Text.escape(ns) + ")");
					break;
				}
			}
		}
	}

	// ───────────────────────── Translation-Probe ─────────────────────────

	private List<String> probeKeys() {
		List<String> keys = new ArrayList<>();
		for (ModRule r : rules.values()) {
			for (String k : r.keys()) if (!keys.contains(k)) keys.add(k);
		}
		return keys.size() > KEYS_PER_LINE * 4 ? keys.subList(0, KEYS_PER_LINE * 4) : keys;
	}

	private void sendProbe(Player p) {
		List<String> keys = probeKeys();
		if (keys.isEmpty()) return;
		Location eye = p.getEyeLocation();
		int y = Math.min(p.getWorld().getMaxHeight() - 1, eye.getBlockY() + 2);
		Location loc = new Location(p.getWorld(), eye.getBlockX(), y, eye.getBlockZ());

		List<Component> lines = new ArrayList<>();
		for (int line = 0; line < 4; line++) {
			TextComponent.Builder b = Component.text();
			int from = line * KEYS_PER_LINE;
			for (int i = from; i < Math.min(keys.size(), from + KEYS_PER_LINE); i++) {
				if (i > from) b.append(Component.text(SEP));
				b.append(Component.translatable(keys.get(i), FALLBACK));
			}
			lines.add(b.build());
		}
		probes.put(p.getUniqueId(), new Probe(loc, keys, System.currentTimeMillis()));
		probeResults.put(p.getUniqueId(), new ProbeResult(ProbeStatus.PENDING, List.of(), System.currentTimeMillis()));
		p.sendBlockChange(loc, Material.OAK_SIGN.createBlockData());
		p.sendSignChange(loc, lines);
		p.openVirtualSign(Position.block(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), Side.FRONT);
		// Editor sofort wieder schliessen – der Client schickt dabei den aufgeloesten Text zurueck
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (p.isOnline() && probes.containsKey(p.getUniqueId())) {
				PacketEvents.getAPI().getPlayerManager().sendPacket(p, new WrapperPlayServerCloseWindow(0));
			}
		}, 1L);
		long timeout = Math.max(2, plugin.getConfig().getLong("mod-detection.translation-probe.timeout-seconds", 10)) * 20L;
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			Probe pr = probes.remove(p.getUniqueId());
			if (pr == null || !p.isOnline()) return;
			restore(p, pr.location());
			probeResults.put(p.getUniqueId(), new ProbeResult(ProbeStatus.NO_ANSWER, List.of(), System.currentTimeMillis()));
			antiDetection(p);
		}, timeout);
	}

	private static void restore(Player p, Location loc) {
		Block real = loc.getBlock();
		p.sendBlockChange(loc, real.getBlockData());
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onSign(UncheckedSignChangeEvent event) {
		Player p = event.getPlayer();
		Probe probe = probes.get(p.getUniqueId());
		if (probe == null) return;
		var pos = event.getEditedBlockPosition();
		Location l = probe.location();
		if (pos.blockX() != l.getBlockX() || pos.blockY() != l.getBlockY() || pos.blockZ() != l.getBlockZ()) return;
		event.setCancelled(true);
		probes.remove(p.getUniqueId());
		List<Component> lines = event.lines();
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (!p.isOnline()) return;
			restore(p, l);
			if (!evaluate(p, probe.keys(), lines)) clearWarning(p);
		});
	}

	/** @return true, wenn ein Mod erkannt wurde */
	private boolean evaluate(Player p, List<String> keys, List<Component> lines) {
		List<String> resolved = new ArrayList<>();
		for (int line = 0; line < 4; line++) {
			int from = line * KEYS_PER_LINE;
			if (from >= keys.size()) break;
			String text = line < lines.size() ? Text.plain(lines.get(line)) : "";
			String[] parts = text.split("\\|", -1);
			for (int i = from; i < Math.min(keys.size(), from + KEYS_PER_LINE); i++) {
				int idx = i - from;
				String part = idx < parts.length ? parts[idx] : FALLBACK;
				if (!part.isEmpty() && !part.equals(FALLBACK)) resolved.add(keys.get(i) + " = " + part);
			}
		}
		probeResults.put(p.getUniqueId(), new ProbeResult(resolved.isEmpty() ? ProbeStatus.CLEAN : ProbeStatus.DETECTED,
				resolved, System.currentTimeMillis()));
		for (int line = 0; line < 4; line++) {
			int from = line * KEYS_PER_LINE;
			if (from >= keys.size()) break;
			String text = line < lines.size() ? Text.plain(lines.get(line)) : "";
			String[] parts = text.split("\\|", -1);
			for (int i = from; i < Math.min(keys.size(), from + KEYS_PER_LINE); i++) {
				int idx = i - from;
				String part = idx < parts.length ? parts[idx] : FALLBACK;
				if (part.isEmpty() || part.equals(FALLBACK)) continue;
				String key = keys.get(i);
				for (ModRule r : rules.values()) {
					if (r.keys().contains(key)) {
						detected(p, r, "Translation-Key \"" + key + "\" -> \"" + part + "\"");
						return true;
					}
				}
			}
		}
		return false;
	}

	// ───────────────────────── Anti-Detection ─────────────────────────

	private java.io.File warnedFile() {
		return new java.io.File(plugin.getDataFolder(), "antidetect-warnings.yml");
	}

	private void loadWarned() {
		warned.clear();
		var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(warnedFile());
		for (String s : y.getStringList("warned")) {
			try {
				warned.add(UUID.fromString(s));
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	private void saveWarned() {
		var y = new org.bukkit.configuration.file.YamlConfiguration();
		List<String> list = new ArrayList<>();
		for (UUID id : warned) list.add(id.toString());
		y.set("warned", list);
		try {
			y.save(warnedFile());
		} catch (java.io.IOException ex) {
			plugin.getLogger().warning("antidetect-warnings.yml: " + ex.getMessage());
		}
	}

	/** Bedrock-Spieler (Geyser/Floodgate) koennen die Probe nicht beantworten. */
	private static boolean bedrock(Player p) {
		String brand = p.getClientBrandName();
		if (brand != null && (brand.toLowerCase(Locale.ROOT).contains("geyser") || brand.toLowerCase(Locale.ROOT).contains("floodgate"))) return true;
		return p.getUniqueId().getMostSignificantBits() == 0L;
	}

	/**
	 * Keine Antwort auf die Probe = Anti-Detection-Mod (z.B. ExploitPreventer, OpSec).
	 * Erstes Mal: Kick mit Aufforderung, den Mod zu entfernen. Wiederholt: Ban (Standard 30 Tage).
	 */
	private void antiDetection(Player p) {
		ConfigurationSection c = plugin.getConfig().getConfigurationSection("mod-detection.anti-detection");
		String action = c == null ? "kick-then-ban" : c.getString("action", "kick-then-ban").toLowerCase(Locale.ROOT);
		plugin.alerts().send("%prefix%<gold>Anti-Detection? <white>" + p.getName()
				+ " <gray>hat nicht auf die Mod-Pruefung geantwortet" + (warned.contains(p.getUniqueId()) ? " <red>(bereits verwarnt)" : ""));
		if (action.equals("alert") || bedrock(p) || p.hasPermission(AtlasAC.PERM_BYPASS) || p.hasPermission(PERM_BYPASS_MODS)) return;

		PunishmentManager pm = plugin.punishments();
		if (warned.contains(p.getUniqueId()) || action.equals("ban")) {
			long duration = Durations.parse(c == null ? "30d" : c.getString("ban-duration", "30d"));
			if (duration == 0) duration = 30L * 86_400_000L;
			warned.remove(p.getUniqueId());
			saveWarned();
			pm.ban(new PunishmentManager.Target(p.getUniqueId(), p.getName(), PunishmentManager.ipOf(p)),
					c == null ? "Anti-Detection-Mod nach Verwarnung" : c.getString("ban-reason", "Anti-Detection-Mod nach Verwarnung"),
					PunishmentManager.STAFF_ATLAS, duration);
			return;
		}
		warned.add(p.getUniqueId());
		saveWarned();
		pm.kick(p, c == null ? "Anti-Detection-Mod erkannt" : c.getString("kick-reason", "Anti-Detection-Mod erkannt"),
				PunishmentManager.STAFF_ATLAS, false);
	}

	/** Spieler hat die Probe sauber beantwortet -> Verwarnung loeschen. */
	private void clearWarning(Player p) {
		if (warned.remove(p.getUniqueId())) {
			saveWarned();
			plugin.alerts().send("%prefix%<green>" + p.getName() + " <gray>hat den Anti-Detection-Mod entfernt.");
		}
	}

	// ───────────────────────── Strafe ─────────────────────────

	private void detected(Player p, ModRule r, String evidence) {
		if (!punished.add(p.getUniqueId())) return;
		boolean bypass = p.hasPermission(PERM_BYPASS_MODS);
		plugin.getLogger().warning("Mod erkannt: " + p.getName() + " -> " + r.display() + " (" + evidence + ")" + (bypass ? " [Mod-Bypass]" : ""));
		plugin.alerts().send("%prefix%<dark_red><bold>MOD</bold> <white>" + p.getName() + " <gray>nutzt <red>" + Text.escape(r.display())
				+ " <dark_gray>(" + Text.escape(evidence) + ")" + (bypass ? " <green>[Mod-Bypass – keine Strafe]" : ""));
		if (bypass) {
			punished.remove(p.getUniqueId());
			return;
		}
		String reason = r.reason().replace("%mod%", r.display());
		PunishmentManager pm = plugin.punishments();
		PunishmentManager.Target target = new PunishmentManager.Target(p.getUniqueId(), p.getName(), PunishmentManager.ipOf(p));
		switch (r.action()) {
			case "alert" -> punished.remove(p.getUniqueId());
			// Kick zaehlt zur Eskalation: 3. Atlas-Kick in 24h = Tempban (punishment.auto-ban)
			case "kick" -> pm.autoPunish(p, r.display(), reason);
			case "ipban" -> {
				pm.ipBan(target, reason, PunishmentManager.STAFF_ATLAS, r.duration());
				pm.ban(target, reason, PunishmentManager.STAFF_ATLAS, r.duration(), false);
			}
			default -> pm.ban(target, reason, PunishmentManager.STAFF_ATLAS, r.duration());
		}
	}

	/** Fuer /atlas mods check <Spieler>: erneut pruefen (Brand, Channels, Probe). */
	public void recheck(Player p) {
		punished.remove(p.getUniqueId());
		scanIdentity(p);
		if (p.isOnline() && !punished.contains(p.getUniqueId())) sendProbe(p);
	}

	// ───────────────────────── Uebersicht ─────────────────────────

	/** Ein Mod (Channel-Namespace) mit seinen Channels und ggf. der Regel, die zutrifft. */
	public record ModEntry(String id, List<String> channels, ModRule rule) {
	}

	public record Report(String brand, String loader, List<ModEntry> mods, ProbeResult probe, boolean warned) {
	}

	private static final Set<String> LOADER_NAMESPACES = Set.of("minecraft", "fabric", "c", "forge", "neoforge", "quilt", "fml");

	public Report report(Player p) {
		Set<String> all = new LinkedHashSet<>(p.getListeningPluginChannels());
		Set<String> extra = channels.get(p.getUniqueId());
		if (extra != null) all.addAll(extra);
		Map<String, List<String>> byNs = new java.util.TreeMap<>();
		boolean fabric = false, forge = false, neoforge = false, quilt = false;
		for (String ch : all) {
			int colon = ch.indexOf(':');
			String ns = colon > 0 ? ch.substring(0, colon) : ch;
			String path = colon > 0 ? ch.substring(colon + 1) : "";
			if (ns.startsWith("fabric")) fabric = true;
			if (ns.equals("forge") || ns.equals("fml")) forge = true;
			if (ns.equals("neoforge")) neoforge = true;
			if (ns.startsWith("quilt")) quilt = true;
			if (LOADER_NAMESPACES.contains(ns) || ns.startsWith("fabric")) continue;
			byNs.computeIfAbsent(ns, k -> new ArrayList<>()).add(path);
		}
		String brand = p.getClientBrandName();
		String b = brand == null ? "" : brand.toLowerCase(Locale.ROOT);
		String loader = neoforge || b.contains("neoforge") ? "NeoForge"
				: forge || b.contains("forge") ? "Forge"
				: quilt || b.contains("quilt") ? "Quilt"
				: fabric || b.contains("fabric") ? "Fabric"
				: b.equals("vanilla") ? "Vanilla" : brand == null ? "unbekannt" : brand;
		List<ModEntry> mods = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : byNs.entrySet()) mods.add(new ModEntry(e.getKey(), e.getValue(), match(e.getKey())));
		ProbeResult probe = probeResults.getOrDefault(p.getUniqueId(), new ProbeResult(ProbeStatus.NOT_RUN, List.of(), 0));
		return new Report(brand == null ? "unbekannt" : brand, loader, mods, probe, warned.contains(p.getUniqueId()));
	}

	public static String probeText(ProbeResult r) {
		return switch (r.status()) {
			case NOT_RUN -> "<gray>noch nicht geprueft";
			case PENDING -> "<yellow>laeuft …";
			case CLEAN -> "<green>sauber (keine Mod-Keys)";
			case DETECTED -> "<red>Mod-Keys gefunden (" + r.resolved().size() + ")";
			case NO_ANSWER -> "<gold>keine Antwort (Anti-Detection?)";
		};
	}

	/** Mehrzeilige Chat-Uebersicht fuer /atlas mods info. */
	public List<String> describe(Player p) {
		Report r = report(p);
		List<String> out = new ArrayList<>();
		out.add("<dark_gray>━━━━━━━━ <white><bold>" + p.getName() + "</bold> <dark_gray>━━━━━━━━");
		out.add("<gray>Client: <white>" + Text.escape(r.brand()) + " <dark_gray>| <gray>Loader: <white>" + r.loader());
		out.add("<gray>Schild-Probe: " + probeText(r.probe()) + (r.warned() ? " <red>(verwarnt)" : ""));
		for (String k : r.probe().resolved()) out.add("<dark_gray>  » <red>" + Text.escape(k));
		if (r.mods().isEmpty()) {
			out.add("<gray>Mods mit Channels: <white>keine");
		} else {
			out.add("<gray>Mods mit Channels <dark_gray>(" + r.mods().size() + ")<gray>:");
			for (ModEntry m : r.mods()) {
				String state = m.rule() == null ? "<green>✔" : "<red>✘ " + Text.escape(m.rule().display());
				out.add("<dark_gray>  » " + state + " <white>" + Text.escape(m.id()) + " <dark_gray>(" + m.channels().size() + " Channel"
						+ (m.channels().size() == 1 ? "" : "s") + ")");
			}
		}
		out.add("<dark_gray>Hinweis: Mods ohne Netzwerk-Channel sind nur ueber die Schild-Probe sichtbar.");
		return out;
	}
}
