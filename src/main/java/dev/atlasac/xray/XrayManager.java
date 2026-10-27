package dev.atlasac.xray;

import dev.atlasac.AtlasAC;
import dev.atlasac.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Anti-XRay durch Abbau-Statistik: werden in kurzer Zeit zu viele Erze einer Gruppe abgebaut,
 * bekommt das Team einen Alert (mit Stein/Erz-Verhaeltnis und Teleport-Klick).
 */
public final class XrayManager implements Listener {
	private static final int MAX_PLACED = 200_000;

	private final AtlasAC plugin;
	private final Map<String, OreGroup> groups = new LinkedHashMap<>();
	private final Map<Material, OreGroup> byMaterial = new HashMap<>();
	/** Grundgestein -> Kategorie (stone, deepslate, netherrack) fuer das Erz-pro-Gestein-Verhaeltnis. */
	private final Map<Material, String> baseOf = new HashMap<>();
	public static final List<String> BASES = List.of("stone", "deepslate", "netherrack");
	private static final Map<String, String> BASE_NAMES = Map.of("stone", "Stein", "deepslate", "Tiefenschiefer", "netherrack", "Netherrack");
	private final Map<UUID, Map<String, ArrayDeque<Long>>> mined = new HashMap<>();
	private final Map<UUID, Map<String, ArrayDeque<Long>>> baseMined = new HashMap<>();
	private final Map<UUID, Map<String, Long>> lastAlert = new HashMap<>();
	private final Set<String> placed = Collections.newSetFromMap(new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
			return size() > MAX_PLACED;
		}
	});
	private boolean enabled;
	private boolean ignorePlaced;
	private long cooldownMs;

	public XrayManager(AtlasAC plugin) {
		this.plugin = plugin;
	}

	public void reload() {
		ConfigurationSection x = plugin.getConfig().getConfigurationSection("xray");
		groups.clear();
		byMaterial.clear();
		baseOf.clear();
		if (x == null) {
			enabled = false;
			return;
		}
		enabled = x.getBoolean("enabled", true);
		ignorePlaced = x.getBoolean("ignore-placed-blocks", true);
		cooldownMs = x.getLong("alert-cooldown-seconds", 60) * 1000L;
		ConfigurationSection bases = x.getConfigurationSection("base-blocks");
		for (String base : BASES) {
			List<String> list = bases != null ? bases.getStringList(base) : List.of();
			for (String s : list) {
				Material m = Material.matchMaterial(s);
				if (m != null) baseOf.put(m, base);
			}
		}
		ConfigurationSection gs = x.getConfigurationSection("groups");
		if (gs == null) return;
		for (String id : gs.getKeys(false)) {
			ConfigurationSection g = gs.getConfigurationSection(id);
			if (g == null) continue;
			Set<Material> mats = new LinkedHashSet<>();
			for (String s : g.getStringList("materials")) {
				Material m = Material.matchMaterial(s);
				if (m != null && m.isBlock()) mats.add(m);
				else plugin.getLogger().warning("XRay: unbekanntes Material '" + s + "' in Gruppe " + id);
			}
			if (mats.isEmpty()) continue;
			Material icon = Material.matchMaterial(g.getString("icon", ""));
			if (icon == null || !icon.isItem()) icon = mats.iterator().next();
			OreGroup group = new OreGroup(id, g.getString("display", id), icon, mats,
					g.getBoolean("enabled", true), Math.max(1, g.getInt("max-blocks", 10)), Math.max(1, g.getInt("window-minutes", 10)));
			groups.put(id, group);
			for (Material m : mats) byMaterial.put(m, group);
		}
	}

	/** Schreibt eine (per GUI geaenderte) Gruppe zurueck in die config.yml. */
	public void save(OreGroup g) {
		String path = "xray.groups." + g.id + ".";
		plugin.getConfig().set(path + "enabled", g.enabled);
		plugin.getConfig().set(path + "max-blocks", g.maxBlocks);
		plugin.getConfig().set(path + "window-minutes", g.windowMinutes);
		plugin.saveConfig();
	}

	public Map<String, OreGroup> groups() {
		return groups;
	}

	private static String key(Block b) {
		return b.getWorld().getName() + ':' + b.getX() + ':' + b.getY() + ':' + b.getZ();
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		if (enabled && ignorePlaced && byMaterial.containsKey(event.getBlockPlaced().getType())) {
			placed.add(key(event.getBlockPlaced()));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		if (!enabled) return;
		Player p = event.getPlayer();
		Block b = event.getBlock();
		Material type = b.getType();
		long now = System.currentTimeMillis();

		String base = baseOf.get(type);
		if (base != null) {
			ArrayDeque<Long> q = baseMined.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
					.computeIfAbsent(base, k -> new ArrayDeque<>());
			q.addLast(now);
			trim(q, now, 60);
			return;
		}
		OreGroup g = byMaterial.get(type);
		if (g == null || !g.enabled) return;
		if (ignorePlaced && placed.remove(key(b))) return;
		if (p.hasPermission(dev.atlasac.AtlasAC.PERM_BYPASS)) return;

		ArrayDeque<Long> times = mined.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
				.computeIfAbsent(g.id, k -> new ArrayDeque<>());
		times.addLast(now);
		trim(times, now, g.windowMinutes);

		if (times.size() >= g.maxBlocks) {
			Map<String, Long> last = lastAlert.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
			Long l = last.get(g.id);
			if (l != null && now - l < cooldownMs) return;
			last.put(g.id, now);
			alert(p, g, times.size(), b.getLocation());
		}
	}

	/** Zu welchem Gestein gehoert ein Erz? Tiefenschiefer-Erze -> Tiefenschiefer, Nether-Erze -> Netherrack. */
	private static String baseFor(OreGroup g) {
		String key = g.materials.iterator().next().getKey().getKey();
		if (key.contains("deepslate")) return "deepslate";
		if (key.contains("nether") || key.contains("ancient_debris") || key.contains("gilded") || key.contains("quartz")) return "netherrack";
		return "stone";
	}

	private int baseCount(UUID player, String base, int minutes) {
		Map<String, ArrayDeque<Long>> m = baseMined.get(player);
		ArrayDeque<Long> q = m == null ? null : m.get(base);
		if (q == null) return 0;
		trim(q, System.currentTimeMillis(), minutes);
		return q.size();
	}

	private void alert(Player p, OreGroup g, int count, Location loc) {
		String base = baseFor(g);
		int stoneCount = baseCount(p.getUniqueId(), "stone", g.windowMinutes);
		int deepCount = baseCount(p.getUniqueId(), "deepslate", g.windowMinutes);
		int netherCount = baseCount(p.getUniqueId(), "netherrack", g.windowMinutes);
		int relevant = switch (base) {
			case "deepslate" -> deepCount;
			case "netherrack" -> netherCount;
			default -> stoneCount;
		};
		String ratio = relevant == 0 ? "∞" : Text.fmt1(count * 100.0 / relevant);
		String msg = Text.apply(plugin.getConfig().getString("xray.format", "%prefix%XRay? %player% %count%x %group%"), Map.of(
				"%player%", p.getName(),
				"%count%", String.valueOf(count),
				"%group%", Text.escape(g.display),
				"%window%", String.valueOf(g.windowMinutes),
				"%stone%", String.valueOf(stoneCount),
				"%deepslate%", String.valueOf(deepCount),
				"%netherrack%", String.valueOf(netherCount),
				"%base%", BASE_NAMES.get(base),
				"%ratio%", ratio,
				"%location%", loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
		plugin.alerts().send(msg);
	}

	private static void trim(ArrayDeque<Long> times, long now, int minutes) {
		long limit = minutes * 60_000L;
		while (!times.isEmpty() && now - times.peekFirst() > limit) times.pollFirst();
	}

	/** Statistik fuer /atlas xray stats. */
	public Map<String, Integer> stats(UUID player) {
		Map<String, Integer> out = new LinkedHashMap<>();
		long now = System.currentTimeMillis();
		Map<String, ArrayDeque<Long>> m = mined.getOrDefault(player, Map.of());
		for (OreGroup g : groups.values()) {
			ArrayDeque<Long> t = m.get(g.id);
			if (t != null) trim(t, now, g.windowMinutes);
			out.put(g.id, t == null ? 0 : t.size());
		}
		for (String base : BASES) out.put("__" + base, baseCount(player, base, 60));
		return out;
	}

	public void reset(UUID player) {
		mined.remove(player);
		baseMined.remove(player);
		lastAlert.remove(player);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		// Daten bleiben fuer Rejoin-Missbrauch erhalten, nur der Cooldown wird geleert
		lastAlert.remove(event.getPlayer().getUniqueId());
	}
}
