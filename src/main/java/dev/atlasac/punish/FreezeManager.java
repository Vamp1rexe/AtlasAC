package dev.atlasac.punish;

import dev.atlasac.AtlasAC;
import dev.atlasac.util.Durations;
import dev.atlasac.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** /atlas freeze – Spieler komplett festsetzen (fuer Screenshares / Kontrollen). */
public final class FreezeManager implements Listener {
	private static final Set<String> BLOCKED_TELEPORTS = Set.of("ENDER_PEARL", "CONSUMABLE_EFFECT", "CHORUS_FRUIT",
			"END_PORTAL", "NETHER_PORTAL", "END_GATEWAY");

	private final AtlasAC plugin;
	private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
	/** Ob der Spieler vorher fliegen durfte – wird beim Auftauen wiederhergestellt. */
	private final Map<UUID, Boolean> hadFlight = new ConcurrentHashMap<>();
	private final Map<UUID, String> frozenBy = new ConcurrentHashMap<>();

	public FreezeManager(AtlasAC plugin) {
		this.plugin = plugin;
		Bukkit.getScheduler().runTaskTimer(plugin, this::remind, 40L, 100L);
	}

	public boolean isFrozen(UUID uuid) {
		return frozen.contains(uuid);
	}

	public void freeze(Player p, String staff) {
		if (!frozen.add(p.getUniqueId())) return;
		frozenBy.put(p.getUniqueId(), staff);
		hadFlight.put(p.getUniqueId(), p.getAllowFlight());
		p.setAllowFlight(true);
		p.setFallDistance(0);
		showTitle(p);
		p.sendMessage(Text.mm(plugin.getConfig().getString("freeze.message", "%prefix%<red>Du wurdest eingefroren.")
				.replace("%prefix%", plugin.alerts().prefix())));
		plugin.alerts().send("%prefix%<aqua>" + p.getName() + " <gray>wurde von <white>" + Text.escape(staff) + " <gray>eingefroren.");
	}

	public void unfreeze(Player p, String staff) {
		if (!frozen.remove(p.getUniqueId())) return;
		frozenBy.remove(p.getUniqueId());
		Boolean flight = hadFlight.remove(p.getUniqueId());
		if (flight != null && !flight) {
			p.setFlying(false);
			p.setAllowFlight(false);
		}
		p.clearTitle();
		p.sendMessage(Text.mm(plugin.alerts().prefix() + "<green>Du wurdest aufgetaut."));
		plugin.alerts().send("%prefix%<aqua>" + p.getName() + " <gray>wurde von <white>" + Text.escape(staff) + " <gray>aufgetaut.");
	}

	private void showTitle(Player p) {
		p.showTitle(Title.title(
				Text.mm(plugin.getConfig().getString("freeze.title", "<aqua>EINGEFROREN")),
				Text.mm(plugin.getConfig().getString("freeze.subtitle", "")),
				Title.Times.times(Duration.ZERO, Duration.ofSeconds(6), Duration.ofMillis(500))));
	}

	private void remind() {
		for (UUID id : frozen) {
			Player p = Bukkit.getPlayer(id);
			if (p != null) showTitle(p);
		}
	}

	private boolean check(Player p, Cancellable event) {
		if (!frozen.contains(p.getUniqueId())) return false;
		event.setCancelled(true);
		return true;
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onMove(PlayerMoveEvent e) {
		if (!frozen.contains(e.getPlayer().getUniqueId())) return;
		Location from = e.getFrom(), to = e.getTo();
		if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
			Location back = from.clone();
			back.setYaw(to.getYaw());
			back.setPitch(to.getPitch());
			e.setTo(back);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent e) {
		if (BLOCKED_TELEPORTS.contains(e.getCause().name())) check(e.getPlayer(), e);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInteract(PlayerInteractEvent e) {
		check(e.getPlayer(), e);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInteractEntity(PlayerInteractEntityEvent e) {
		check(e.getPlayer(), e);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent e) {
		check(e.getPlayer(), e);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent e) {
		check(e.getPlayer(), e);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent e) {
		check(e.getPlayer(), e);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSwap(PlayerSwapHandItemsEvent e) {
		check(e.getPlayer(), e);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPickup(EntityPickupItemEvent e) {
		if (e.getEntity() instanceof Player p) check(p, e);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventory(InventoryClickEvent e) {
		if (e.getWhoClicked() instanceof Player p) check(p, e);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player p) check(p, e);
		if (e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player attacker) check(attacker, e);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onCommand(PlayerCommandPreprocessEvent e) {
		if (!frozen.contains(e.getPlayer().getUniqueId())) return;
		String label = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
		int colon = label.indexOf(':');
		if (colon >= 0) label = label.substring(colon + 1);
		if (!plugin.getConfig().getStringList("freeze.allowed-commands").contains(label)) {
			e.setCancelled(true);
			e.getPlayer().sendMessage(Text.mm(plugin.alerts().prefix() + "<red>Du bist eingefroren."));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		Player p = e.getPlayer();
		if (!frozen.remove(p.getUniqueId())) return;
		hadFlight.remove(p.getUniqueId());
		String staff = frozenBy.remove(p.getUniqueId());
		plugin.alerts().send("%prefix%<red>" + p.getName() + " <gray>hat den Server waehrend des Freezes verlassen!");
		var cfg = plugin.getConfig().getConfigurationSection("freeze.logout-ban");
		if (cfg == null || !cfg.getBoolean("enabled", true)) return;
		long duration = Durations.parse(cfg.getString("duration", "30d"));
		if (duration == 0) duration = -1;
		plugin.punishments().ban(new PunishmentManager.Target(p.getUniqueId(), p.getName(), PunishmentManager.ipOf(p)),
				cfg.getString("reason", "Verlassen waehrend Freeze"), staff != null ? staff : PunishmentManager.STAFF_ATLAS, duration);
	}
}
