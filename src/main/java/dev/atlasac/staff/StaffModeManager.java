package dev.atlasac.staff;

import dev.atlasac.AtlasAC;
import dev.atlasac.gui.Menu;
import dev.atlasac.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /staff – Staff-Modus wie bei NookureStaff: Inventar, Position und Spielmodus werden gesichert
 * (auch auf der Festplatte, falls der Server abstuerzt), man geht in Vanish und bekommt Werkzeuge.
 * Erneutes /staff stellt alles wieder her und teleportiert zurueck zur Startposition.
 */
public final class StaffModeManager implements Listener {
	public static final String PERM_STAFF = "atlasac.staff";
	public static final String PERM_SEE_VANISH = "atlasac.vanish.see";
	public static final String PERM_INV_EDIT = "atlasac.staff.invedit";

	private enum Tool {FREEZE, INVENTORY, ENDERCHEST, INFO, RANDOM_TP, VANISH, GUI}

	private final AtlasAC plugin;
	private final NamespacedKey toolKey;
	private final File folder;
	private final Set<UUID> staff = ConcurrentHashMap.newKeySet();
	private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
	/** Wer gerade ein fremdes Inventar nur ansieht (ohne Bearbeitungsrecht). */
	private final Set<UUID> readOnlyViewers = ConcurrentHashMap.newKeySet();

	public StaffModeManager(AtlasAC plugin) {
		this.plugin = plugin;
		this.toolKey = new NamespacedKey(plugin, "staff_tool");
		this.folder = new File(plugin.getDataFolder(), "staffmode");
	}

	public boolean inStaffMode(Player p) {
		return staff.contains(p.getUniqueId());
	}

	public boolean isVanished(Player p) {
		return vanished.contains(p.getUniqueId());
	}

	private void msg(Player p, String text) {
		p.sendMessage(Text.mm(plugin.alerts().prefix() + text));
	}

	// ───────────────────────── Staff-Modus ─────────────────────────

	public void toggle(Player p) {
		if (inStaffMode(p)) disable(p, true);
		else enable(p);
	}

	public void enable(Player p) {
		if (!staff.add(p.getUniqueId())) return;
		try {
			saveState(p);
		} catch (Exception ex) {
			staff.remove(p.getUniqueId());
			msg(p, "<red>Inventar konnte nicht gesichert werden – Staff-Modus abgebrochen.");
			plugin.getLogger().severe("Staff-Modus " + p.getName() + ": " + ex.getMessage());
			return;
		}
		p.getInventory().clear();
		p.getInventory().setArmorContents(new ItemStack[4]);
		p.getInventory().setItemInOffHand(null);
		p.closeInventory();
		p.setGameMode(GameMode.SURVIVAL);
		p.setAllowFlight(true);
		p.setFlying(true);
		p.setInvulnerable(true);
		p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
				? p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() : 20.0);
		p.setFoodLevel(20);
		p.setFireTicks(0);
		giveTools(p);
		var data = plugin.data().get(p);
		if (data != null) data.bypass = true;
		if (plugin.getConfig().getBoolean("staff-mode.vanish-on-enter", true)) setVanished(p, true);
		else updateGlow(p);
		msg(p, "<green>Staff-Modus aktiviert. <gray>Nochmal <white>/staff</white> zum Verlassen.");
		plugin.alerts().send("%prefix%<aqua>" + p.getName() + " <gray>ist jetzt im Staff-Modus.");
	}

	public void disable(Player p, boolean teleportBack) {
		if (!staff.remove(p.getUniqueId())) return;
		setVanished(p, false);
		p.closeInventory();
		restoreState(p, teleportBack);
		var data = plugin.data().get(p);
		if (data != null) data.bypass = p.hasPermission(AtlasAC.PERM_BYPASS);
		msg(p, "<red>Staff-Modus deaktiviert.");
		plugin.alerts().send("%prefix%<aqua>" + p.getName() + " <gray>hat den Staff-Modus verlassen.");
	}

	public void disableAll() {
		for (UUID id : List.copyOf(staff)) {
			Player p = Bukkit.getPlayer(id);
			if (p != null) disable(p, true);
		}
	}

	private File file(Player p) {
		return new File(folder, p.getUniqueId() + ".yml");
	}

	private void saveState(Player p) throws Exception {
		if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Ordner staffmode fehlt");
		YamlConfiguration y = new YamlConfiguration();
		y.set("location", p.getLocation());
		y.set("gamemode", p.getGameMode().name());
		y.set("allow-flight", p.getAllowFlight());
		y.set("flying", p.isFlying());
		y.set("invulnerable", p.isInvulnerable());
		y.set("health", p.getHealth());
		y.set("food", p.getFoodLevel());
		y.set("saturation", (double) p.getSaturation());
		y.set("level", p.getLevel());
		y.set("exp", (double) p.getExp());
		ItemStack[] contents = p.getInventory().getContents();
		for (int i = 0; i < contents.length; i++) {
			ItemStack it = contents[i];
			if (it != null && !it.getType().isAir()) {
				y.set("inventory." + i, Base64.getEncoder().encodeToString(it.serializeAsBytes()));
			}
		}
		y.save(file(p));
	}

	private void restoreState(Player p, boolean teleportBack) {
		File f = file(p);
		if (!f.exists()) return;
		YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
		updateGlow(p);
		p.getInventory().clear();
		ItemStack[] contents = new ItemStack[p.getInventory().getSize()];
		ConfigurationSection inv = y.getConfigurationSection("inventory");
		if (inv != null) {
			for (String key : inv.getKeys(false)) {
				try {
					int slot = Integer.parseInt(key);
					if (slot >= 0 && slot < contents.length) {
						contents[slot] = ItemStack.deserializeBytes(Base64.getDecoder().decode(inv.getString(key, "")));
					}
				} catch (Exception ex) {
					plugin.getLogger().warning("Staff-Inventar " + p.getName() + " Slot " + key + ": " + ex.getMessage());
				}
			}
		}
		p.getInventory().setContents(contents);
		try {
			p.setGameMode(GameMode.valueOf(y.getString("gamemode", "SURVIVAL")));
		} catch (IllegalArgumentException ignored) {
			p.setGameMode(GameMode.SURVIVAL);
		}
		applyFlight(p, y.getBoolean("allow-flight", false), y.getBoolean("flying", false));
		p.setInvulnerable(y.getBoolean("invulnerable", false));
		p.setFoodLevel(y.getInt("food", 20));
		p.setSaturation((float) y.getDouble("saturation", 5));
		p.setLevel(y.getInt("level", 0));
		p.setExp((float) y.getDouble("exp", 0));
		double health = y.getDouble("health", 20);
		var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
		p.setHealth(Math.max(0.5, Math.min(health, max == null ? 20 : max.getValue())));
		Location loc = y.getLocation("location");
		if (teleportBack && loc != null && loc.getWorld() != null) p.teleport(loc);
		if (!f.delete()) plugin.getLogger().warning("staffmode/" + f.getName() + " konnte nicht geloescht werden.");
		// Nach dem Teleport (Weltwechsel, andere Plugins) Flugrechte nochmal hart setzen
		boolean allow = y.getBoolean("allow-flight", false), flying = y.getBoolean("flying", false);
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (p.isOnline() && !inStaffMode(p)) applyFlight(p, allow, flying);
		}, 2L);
	}

	/**
	 * Flug nach dem Staff-Modus: in Survival/Adventure standardmaessig immer aus
	 * (staff-mode.keep-flight-after-exit: true behaelt eine vorher erlaubte Flugfaehigkeit).
	 */
	private void applyFlight(Player p, boolean savedAllow, boolean savedFlying) {
		GameMode gm = p.getGameMode();
		if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
		boolean keep = plugin.getConfig().getBoolean("staff-mode.keep-flight-after-exit", false) && savedAllow;
		if (!keep) {
			p.setFlying(false);
			p.setAllowFlight(false);
			p.setFallDistance(0);
		} else {
			p.setAllowFlight(true);
			p.setFlying(savedFlying);
		}
	}

	// ───────────────────────── Vanish ─────────────────────────

	public void setVanished(Player p, boolean vanish) {
		if (vanish) {
			vanished.add(p.getUniqueId());
			for (Player other : Bukkit.getOnlinePlayers()) {
				if (!other.equals(p) && !other.hasPermission(PERM_SEE_VANISH)) other.hidePlayer(plugin, p);
			}
		} else {
			vanished.remove(p.getUniqueId());
			for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(plugin, p);
		}
		if (inStaffMode(p)) giveTools(p);
		updateGlow(p);
	}

	// ───────────────────────── Rotes Leuchten (sichtbar im Staff-Modus) ─────────────────────────

	private static final String GLOW_TEAM = "atlas_staff";
	private final Map<UUID, String> previousTeam = new ConcurrentHashMap<>();

	private org.bukkit.scoreboard.Team glowTeam() {
		org.bukkit.scoreboard.Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
		org.bukkit.scoreboard.Team team = board.getTeam(GLOW_TEAM);
		if (team == null) team = board.registerNewTeam(GLOW_TEAM);
		team.color(net.kyori.adventure.text.format.NamedTextColor.RED);
		return team;
	}

	/** Im Staff-Modus und sichtbar -> rot leuchten, sonst nicht. */
	public void updateGlow(Player p) {
		boolean glow = inStaffMode(p) && !isVanished(p) && plugin.getConfig().getBoolean("staff-mode.glow-when-visible", true);
		org.bukkit.scoreboard.Team team = glowTeam();
		String entry = p.getName();
		if (glow) {
			org.bukkit.scoreboard.Team current = team.getScoreboard().getEntryTeam(entry);
			if (current != null && !current.getName().equals(GLOW_TEAM)) previousTeam.put(p.getUniqueId(), current.getName());
			team.addEntry(entry);
			p.setGlowing(true);
		} else {
			p.setGlowing(false);
			if (team.hasEntry(entry)) {
				team.removeEntry(entry);
				String prev = previousTeam.remove(p.getUniqueId());
				if (prev != null) {
					org.bukkit.scoreboard.Team old = team.getScoreboard().getTeam(prev);
					if (old != null) old.addEntry(entry);
				}
			}
		}
	}

	public void toggleVanish(Player p) {
		setVanished(p, !isVanished(p));
		msg(p, isVanished(p) ? "<green>Du bist jetzt unsichtbar." : "<yellow>Du bist jetzt sichtbar <gray>(weiterhin im Staff-Modus).");
	}

	// ───────────────────────── Werkzeuge ─────────────────────────

	private ItemStack tool(Tool tool, Material type, String name, String... lore) {
		ItemStack it = Menu.item(type, name, lore);
		ItemMeta meta = it.getItemMeta();
		meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.name());
		it.setItemMeta(meta);
		return it;
	}

	private void giveTools(Player p) {
		var inv = p.getInventory();
		inv.setItem(0, tool(Tool.FREEZE, Material.PACKED_ICE, "<aqua><bold>Einfrieren", "<gray>Rechtsklick auf Spieler"));
		inv.setItem(1, tool(Tool.INVENTORY, Material.CHEST, "<gold><bold>Inventar ansehen", "<gray>Rechtsklick auf Spieler"));
		inv.setItem(2, tool(Tool.ENDERCHEST, Material.ENDER_CHEST, "<light_purple><bold>Enderkiste ansehen", "<gray>Rechtsklick auf Spieler"));
		inv.setItem(3, tool(Tool.INFO, Material.BOOK, "<yellow><bold>Spieler-Info", "<gray>Rechtsklick auf Spieler", "<gray>Flags, Historie, Strafen"));
		inv.setItem(4, tool(Tool.RANDOM_TP, Material.COMPASS, "<green><bold>Zufaelliger Teleport", "<gray>Rechtsklick"));
		inv.setItem(7, isVanished(p)
				? tool(Tool.VANISH, Material.LIME_DYE, "<green><bold>Vanish: AN", "<gray>Rechtsklick zum Ausschalten")
				: tool(Tool.VANISH, Material.GRAY_DYE, "<gray><bold>Vanish: AUS", "<gray>Rechtsklick zum Einschalten"));
		inv.setItem(8, tool(Tool.GUI, Material.NETHER_STAR, "<gradient:#7b5cff:#00d4ff><bold>Admin-GUI", "<gray>Alle Spieler"));
	}

	private Tool toolOf(ItemStack it) {
		if (it == null || !it.hasItemMeta()) return null;
		String v = it.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
		if (v == null) return null;
		try {
			return Tool.valueOf(v);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ───────────────────────── Inspektion ─────────────────────────

	public void openInventory(Player viewer, Player target) {
		if (!viewer.hasPermission(PERM_INV_EDIT)) readOnlyViewers.add(viewer.getUniqueId());
		viewer.openInventory(target.getInventory());
	}

	public void openEnderChest(Player viewer, Player target) {
		if (!viewer.hasPermission(PERM_INV_EDIT)) readOnlyViewers.add(viewer.getUniqueId());
		viewer.openInventory(target.getEnderChest());
	}

	private void randomTeleport(Player p) {
		List<Player> targets = new ArrayList<>();
		for (Player o : Bukkit.getOnlinePlayers()) {
			if (!o.equals(p) && !inStaffMode(o)) targets.add(o);
		}
		if (targets.isEmpty()) {
			msg(p, "<red>Keine Spieler zum Teleportieren.");
			return;
		}
		Player t = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
		p.teleport(t.getLocation());
		msg(p, "<gray>Teleportiert zu <white>" + t.getName());
	}

	// ───────────────────────── Events ─────────────────────────

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteractEntity(PlayerInteractEntityEvent e) {
		Player p = e.getPlayer();
		if (!inStaffMode(p)) return;
		e.setCancelled(true);
		if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player target)) return;
		Tool tool = toolOf(p.getInventory().getItemInMainHand());
		if (tool == null) return;
		switch (tool) {
			case FREEZE -> {
				if (!p.hasPermission("atlasac.freeze")) {
					msg(p, "<red>Keine Rechte (atlasac.freeze)");
					return;
				}
				if (plugin.freeze().isFrozen(target.getUniqueId())) plugin.freeze().unfreeze(target, p.getName());
				else plugin.freeze().freeze(target, p.getName());
			}
			case INVENTORY -> openInventory(p, target);
			case ENDERCHEST -> openEnderChest(p, target);
			case INFO -> plugin.adminGui().openPlayer(p, target.getUniqueId());
			default -> {
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(PlayerInteractEvent e) {
		Player p = e.getPlayer();
		if (!inStaffMode(p)) return;
		Tool tool = toolOf(e.getItem());
		// Staff darf Tueren/Kisten nutzen, aber keine Werkzeuge platzieren
		if (tool != null) e.setCancelled(true);
		if (tool == null || e.getHand() != EquipmentSlot.HAND) return;
		if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		switch (tool) {
			case RANDOM_TP -> randomTeleport(p);
			case VANISH -> toggleVanish(p);
			case GUI -> plugin.adminGui().openList(p, 0);
			default -> {
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onClick(InventoryClickEvent e) {
		if (!(e.getWhoClicked() instanceof Player p)) return;
		if (readOnlyViewers.contains(p.getUniqueId())) {
			e.setCancelled(true);
			return;
		}
		if (inStaffMode(p) && e.getClickedInventory() == p.getInventory() && toolOf(e.getCurrentItem()) != null) {
			e.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDrag(InventoryDragEvent e) {
		if (readOnlyViewers.contains(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
	}

	@EventHandler
	public void onClose(InventoryCloseEvent e) {
		readOnlyViewers.remove(e.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDrop(PlayerDropItemEvent e) {
		if (inStaffMode(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onSwap(PlayerSwapHandItemsEvent e) {
		if (inStaffMode(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPickup(EntityPickupItemEvent e) {
		if (e.getEntity() instanceof Player p && (inStaffMode(p) || isVanished(p))) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onBreak(BlockBreakEvent e) {
		if (inStaffMode(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlace(BlockPlaceEvent e) {
		if (inStaffMode(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player p && inStaffMode(p)) e.setCancelled(true);
		if (e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player d && inStaffMode(d)) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onFood(FoodLevelChangeEvent e) {
		if (e.getEntity() instanceof Player p && inStaffMode(p)) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onTarget(EntityTargetEvent e) {
		if (e.getTarget() instanceof Player p && (inStaffMode(p) || isVanished(p))) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		// Vanish fuer Neuankoemmlinge anwenden
		if (!p.hasPermission(PERM_SEE_VANISH)) {
			for (UUID id : vanished) {
				Player v = Bukkit.getPlayer(id);
				if (v != null) p.hidePlayer(plugin, v);
			}
		}
		// Nach Absturz: gesichertes Inventar zurueckgeben
		if (file(p).exists()) {
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				if (p.isOnline() && !inStaffMode(p)) {
					restoreState(p, true);
					msg(p, "<yellow>Dein Inventar aus dem Staff-Modus wurde wiederhergestellt.");
				}
			}, 5L);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		Player p = e.getPlayer();
		readOnlyViewers.remove(p.getUniqueId());
		if (inStaffMode(p)) disable(p, true);
		vanished.remove(p.getUniqueId());
	}

	public Map<String, Integer> counts() {
		return Map.of("staff", staff.size(), "vanished", vanished.size());
	}
}
