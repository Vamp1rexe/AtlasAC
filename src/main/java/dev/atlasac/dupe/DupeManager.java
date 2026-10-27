package dev.atlasac.dupe;

import dev.atlasac.AtlasAC;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Anti-Dupe: jedes nicht stapelbare Item bekommt eine versteckte, eindeutige ID (PersistentDataContainer).
 * Taucht dieselbe ID bei einem Scan an mehreren Stellen gleichzeitig auf, wurde das Item dupliziert:
 * Kopien werden geloescht, das Team bekommt einen Alert und die beteiligten Spieler werden geflaggt.
 */
public final class DupeManager implements Listener {
	private static final int MAX_DEPTH = 3;
	private static final int MAX_HINTS = 250_000;

	private final AtlasAC plugin;
	private final NamespacedKey key;
	private final Set<UUID> pendingScans = new HashSet<>();
	/** Letzter bekannter Aufbewahrungsort je ID – bestimmt, welche Kopie das Original ist. */
	private final Map<String, String> lastHolder = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > MAX_HINTS;
		}
	};

	private boolean enabled;
	private boolean scanDropped;
	private boolean removeAll;
	private boolean flagOverstacked;
	private boolean punish;
	private int maxVl;
	private final Set<String> ignored = new HashSet<>();

	public DupeManager(AtlasAC plugin) {
		this.plugin = plugin;
		this.key = new NamespacedKey(plugin, "uid");
	}

	public void reload() {
		ConfigurationSection c = plugin.getConfig().getConfigurationSection("anti-dupe");
		enabled = c != null && c.getBoolean("enabled", true);
		if (c == null) return;
		scanDropped = c.getBoolean("scan-dropped-items", true);
		removeAll = c.getBoolean("remove-all-copies", false);
		flagOverstacked = c.getBoolean("flag-overstacked", true);
		punish = c.getBoolean("punish", true);
		maxVl = Math.max(1, c.getInt("max-vl", 1));
		ignored.clear();
		for (String s : c.getStringList("ignore-materials")) {
			Material m = Material.matchMaterial(s);
			if (m != null) ignored.add(m.getKey().toString());
		}
	}

	public int interval() {
		return Math.max(20, plugin.getConfig().getInt("anti-dupe.scan-interval-ticks", 100));
	}

	// ───────────────────────── ID-Verwaltung ─────────────────────────

	public boolean eligible(ItemStack it) {
		return it != null && !it.getType().isAir() && it.getMaxStackSize() == 1 && !ignored.contains(it.getType().getKey().toString());
	}

	public String readId(ItemStack it) {
		if (it == null || !it.hasItemMeta()) return null;
		return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
	}

	private void writeId(ItemStack it, String id) {
		ItemMeta meta = it.getItemMeta();
		if (meta == null) return;
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
		it.setItemMeta(meta);
	}

	private static String newId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	// ───────────────────────── Scannen ─────────────────────────

	private record Occurrence(String id, String holderKey, String holderName, Player owner, Material type,
							  boolean overstacked, Runnable remove, Runnable fixAmount) {
	}

	/** Voller Scan: alle Online-Spieler, ihre Enderkisten, offenen Container und (optional) Boden-Items. */
	public int scanAll() {
		if (!enabled) return 0;
		List<Occurrence> out = new ArrayList<>();
		Set<String> scanned = new HashSet<>();
		for (Player p : Bukkit.getOnlinePlayers()) collectPlayer(p, out, scanned);
		if (scanDropped) {
			for (World w : Bukkit.getWorlds()) {
				for (Item item : w.getEntitiesByClass(Item.class)) collectItemEntity(item, out);
			}
		}
		return evaluate(out);
	}

	public int scanPlayer(Player p) {
		if (!enabled || !p.isOnline()) return 0;
		List<Occurrence> out = new ArrayList<>();
		collectPlayer(p, out, new HashSet<>());
		return evaluate(out);
	}

	private void collectPlayer(Player p, List<Occurrence> out, Set<String> scanned) {
		String uuid = p.getUniqueId().toString();
		if (scanned.add("p:" + uuid)) {
			collectInventory(p.getInventory(), "p:" + uuid, p.getName(), p, out);
			ItemStack cursor = p.getItemOnCursor();
			if (!cursor.getType().isAir()) {
				collectStack(cursor, "cursor:" + uuid, p.getName(), p, out, p::getItemOnCursor,
						v -> p.setItemOnCursor(v == null ? new ItemStack(Material.AIR) : v), 0);
			}
		}
		if (scanned.add("ec:" + uuid)) {
			collectInventory(p.getEnderChest(), "ec:" + uuid, p.getName() + " (Enderkiste)", p, out);
		}
		Inventory top = p.getOpenInventory().getTopInventory();
		InventoryType type = top.getType();
		if (type == InventoryType.CRAFTING || type == InventoryType.PLAYER || type == InventoryType.CREATIVE
				|| type == InventoryType.ENDER_CHEST) return;
		List<Inventory> parts = new ArrayList<>();
		if (top instanceof DoubleChestInventory dci) {
			parts.add(dci.getLeftSide());
			parts.add(dci.getRightSide());
		} else {
			parts.add(top);
		}
		for (Inventory part : parts) {
			String k = inventoryKey(part);
			if (k != null && scanned.add(k)) collectInventory(part, k, describe(part, k), p, out);
		}
	}

	/** Schluessel fuer echte Container. Virtuelle Inventare (Plugin-GUIs, Auktionshaeuser) werden ignoriert. */
	private static String inventoryKey(Inventory inv) {
		InventoryHolder h = inv.getHolder(false);
		if (h instanceof Entity e) return "e:" + e.getUniqueId();
		Location l = inv.getLocation();
		if (l == null || l.getWorld() == null || h == null) return null;
		return "b:" + l.getWorld().getName() + ':' + l.getBlockX() + ':' + l.getBlockY() + ':' + l.getBlockZ();
	}

	private static String describe(Inventory inv, String key) {
		Location l = inv.getLocation();
		if (l == null) return key;
		return inv.getType().name().toLowerCase() + " @ " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
	}

	private void collectInventory(Inventory inv, String holderKey, String holderName, Player owner, List<Occurrence> out) {
		ItemStack[] contents = inv.getContents();
		for (int i = 0; i < contents.length; i++) {
			ItemStack it = contents[i];
			if (it == null || it.getType().isAir()) continue;
			final int slot = i;
			collectStack(it, holderKey, holderName, owner, out, () -> inv.getItem(slot), v -> inv.setItem(slot, v), 0);
		}
	}

	private void collectItemEntity(Item entity, List<Occurrence> out) {
		ItemStack it = entity.getItemStack();
		Location l = entity.getLocation();
		String name = "Boden @ " + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
		UUID thrower = entity.getThrower();
		Player owner = thrower == null ? null : Bukkit.getPlayer(thrower);
		collectStack(it, "item:" + entity.getUniqueId(), name, owner, out, entity::getItemStack, v -> {
			if (v == null) entity.remove();
			else entity.setItemStack(v);
		}, 0);
	}

	private void collectStack(ItemStack it, String holderKey, String holderName, Player owner, List<Occurrence> out,
							  Supplier<ItemStack> get, Consumer<ItemStack> set, int depth) {
		if (eligible(it)) {
			String id = readId(it);
			if (id == null) {
				id = newId();
				writeId(it, id);
				set.accept(it);
			}
			out.add(new Occurrence(id, holderKey, holderName, owner, it.getType(), it.getAmount() > 1,
					() -> set.accept(null),
					() -> {
						ItemStack cur = get.get();
						if (cur != null) {
							cur.setAmount(1);
							set.accept(cur);
						}
					}));
		}
		if (depth >= MAX_DEPTH || !it.hasItemMeta()) return;
		ItemMeta meta = it.getItemMeta();

		// Shulker-Inhalt
		if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
			ItemStack[] inner = box.getInventory().getContents();
			for (int j = 0; j < inner.length; j++) {
				ItemStack child = inner[j];
				if (child == null || child.getType().isAir()) continue;
				final int slot = j;
				Supplier<ItemStack> childGet = () -> {
					ItemStack outer = get.get();
					if (outer == null || !(outer.getItemMeta() instanceof BlockStateMeta m) || !(m.getBlockState() instanceof ShulkerBox b)) return null;
					return b.getInventory().getItem(slot);
				};
				Consumer<ItemStack> childSet = v -> {
					ItemStack outer = get.get();
					if (outer == null || !(outer.getItemMeta() instanceof BlockStateMeta m) || !(m.getBlockState() instanceof ShulkerBox b)) return;
					b.getInventory().setItem(slot, v);
					m.setBlockState(b);
					outer.setItemMeta(m);
					set.accept(outer);
				};
				collectStack(child, holderKey, holderName + " (Shulker)", owner, out, childGet, childSet, depth + 1);
			}
		}

		// Bundle-Inhalt
		if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
			List<ItemStack> inner = bundle.getItems();
			for (int j = 0; j < inner.size(); j++) {
				ItemStack child = inner.get(j);
				if (child == null || child.getType().isAir()) continue;
				final int index = j;
				Supplier<ItemStack> childGet = () -> {
					ItemStack outer = get.get();
					if (outer == null || !(outer.getItemMeta() instanceof BundleMeta m)) return null;
					List<ItemStack> items = m.getItems();
					return index < items.size() ? items.get(index) : null;
				};
				Consumer<ItemStack> childSet = v -> {
					ItemStack outer = get.get();
					if (outer == null || !(outer.getItemMeta() instanceof BundleMeta m)) return;
					List<ItemStack> items = new ArrayList<>(m.getItems());
					if (index >= items.size()) return;
					if (v == null) items.remove(index);
					else items.set(index, v);
					m.setItems(items);
					outer.setItemMeta(m);
					set.accept(outer);
				};
				collectStack(child, holderKey, holderName + " (Bundle)", owner, out, childGet, childSet, depth + 1);
			}
		}
	}

	// ───────────────────────── Auswertung ─────────────────────────

	private int evaluate(List<Occurrence> all) {
		Map<String, List<Occurrence>> byId = new LinkedHashMap<>();
		for (Occurrence o : all) byId.computeIfAbsent(o.id(), k -> new ArrayList<>()).add(o);

		int dupes = 0;
		for (Map.Entry<String, List<Occurrence>> e : byId.entrySet()) {
			List<Occurrence> list = e.getValue();
			String id = e.getKey();
			if (list.size() == 1) {
				Occurrence o = list.getFirst();
				lastHolder.put(id, o.holderKey());
				if (o.overstacked() && flagOverstacked) {
					o.fixAmount().run();
					report(id, list, List.of(o), "Stack > 1");
					dupes++;
				}
				continue;
			}
			// Mehrere Kopien gleichzeitig -> Dupe
			Occurrence keep = null;
			if (!removeAll) {
				String hint = lastHolder.get(id);
				for (Occurrence o : list) {
					if (o.holderKey().equals(hint)) {
						keep = o;
						break;
					}
				}
				if (keep == null) keep = list.getFirst();
			}
			List<Occurrence> removed = new ArrayList<>();
			// Rueckwaerts loeschen, damit Bundle-Indizes stimmen
			for (int i = list.size() - 1; i >= 0; i--) {
				Occurrence o = list.get(i);
				if (o != keep) {
					o.remove().run();
					removed.add(o);
				}
			}
			if (keep != null) {
				lastHolder.put(id, keep.holderKey());
				if (keep.overstacked()) keep.fixAmount().run();
			}
			report(id, list, removed, null);
			dupes++;
		}
		return dupes;
	}

	private void report(String id, List<Occurrence> all, List<Occurrence> removed, String reason) {
		Set<String> names = new LinkedHashSet<>();
		for (Occurrence o : all) names.add(o.owner() != null ? o.owner().getName() : o.holderName());
		Material type = all.getFirst().type();
		String msg = Text.apply(plugin.getConfig().getString("anti-dupe.format", "%prefix%DUPE %player% %amount%x %item%"), Map.of(
				"%player%", String.join(", ", names),
				"%amount%", reason != null ? "Stack" : String.valueOf(all.size()),
				"%item%", type.getKey().getKey(),
				"%id%", id.substring(0, Math.min(8, id.length()))));
		plugin.alerts().send(msg);
		StringBuilder log = new StringBuilder("Dupe ").append(id).append(" (").append(type.getKey()).append(")");
		if (reason != null) log.append(" [").append(reason).append("]");
		log.append(" Fundorte:");
		for (Occurrence o : all) log.append(" [").append(o.holderName()).append(removed.contains(o) ? " -> geloescht" : " -> behalten").append(']');
		plugin.getLogger().warning(log.toString());

		Set<Player> offenders = new LinkedHashSet<>();
		for (Occurrence o : removed) if (o.owner() != null) offenders.add(o.owner());
		for (Player p : offenders) {
			PlayerData d = plugin.data().get(p);
			if (d == null || d.bypass) continue;
			double vl = d.violations.merge("dupe", 1.0, Double::sum);
			if (punish && vl >= maxVl) {
				d.violations.put("dupe", 0.0);
				plugin.violations().punish(p, "Dupe");
			}
		}
	}

	// ───────────────────────── Events ─────────────────────────

	private void scheduleScan(HumanEntity who) {
		if (!enabled || !(who instanceof Player p)) return;
		if (!pendingScans.add(p.getUniqueId())) return;
		Bukkit.getScheduler().runTask(plugin, () -> {
			pendingScans.remove(p.getUniqueId());
			scanPlayer(p);
		});
	}

	/** Creative-Klone (Mittelklick) bekommen sofort eine neue ID, damit sie nicht als Dupe zaehlen. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCreative(InventoryCreativeEvent event) {
		ItemStack cursor = event.getCursor();
		if (eligible(cursor) && readId(cursor) != null) {
			writeId(cursor, newId());
			event.setCursor(cursor);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onClick(InventoryClickEvent event) {
		if (event.getWhoClicked().getGameMode() == GameMode.CREATIVE) return;
		scheduleScan(event.getWhoClicked());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDrag(InventoryDragEvent event) {
		scheduleScan(event.getWhoClicked());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onClose(InventoryCloseEvent event) {
		scheduleScan(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPickup(EntityPickupItemEvent event) {
		if (event.getEntity() instanceof Player p) scheduleScan(p);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		Bukkit.getScheduler().runTaskLater(plugin, () -> scanPlayer(p), 20L);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		pendingScans.remove(event.getPlayer().getUniqueId());
	}
}
