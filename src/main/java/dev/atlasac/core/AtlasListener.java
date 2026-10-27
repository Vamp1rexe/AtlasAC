package dev.atlasac.core;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.CheckManager;
import dev.atlasac.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/** Combat-Checks (Bukkit-Seite) und alle Ausnahmen (Teleport, Knockback, Kolben, Wind Charges ...). */
public final class AtlasListener implements Listener {
	private final AtlasAC plugin;

	public AtlasListener(AtlasAC plugin) {
		this.plugin = plugin;
	}

	private static int tick() {
		return Bukkit.getCurrentTick();
	}

	private static int pingTicks(Player p) {
		return (int) Math.ceil(Math.max(0, p.getPing()) / 50.0);
	}

	// ───────────────────────── Combat ─────────────────────────

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onAttack(EntityDamageByEntityEvent event) {
		if (!(event.getDamager() instanceof Player attacker)) return;
		if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
		PlayerData d = plugin.data().get(attacker);
		if (d == null) return;
		int tick = tick();
		d.lastAttackServerTick = tick;
		if (d.bypass || attacker.getGameMode() == GameMode.CREATIVE || attacker.getGameMode() == GameMode.SPECTATOR) return;
		if (tick < d.exemptUntil || plugin.freeze().isFrozen(attacker.getUniqueId())) return;

		Entity target = event.getEntity();
		PlayerData td = target instanceof Player tp ? plugin.data().get(tp) : null;
		CheckManager checks = plugin.checks();

		boolean cancel = checks.reach.check(d, attacker, target, td, tick);
		cancel |= checks.hitbox.check(d, attacker, target, td, tick);
		cancel |= checks.killAuraWall.check(d, attacker, target, td, tick);
		checks.aimLock.check(d, attacker, target);
		cancel |= checks.criticals.check(d, attacker, event.isCritical());
		if (cancel) event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		Player p = event.getPlayer();
		PlayerData d = plugin.data().get(p);
		if (d == null || d.bypass || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
		if (tick() < d.exemptUntil) return;
		if (plugin.checks().place.check(d, p, event.getBlockAgainst(), event.getBlockPlaced())) event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onContainerClick(org.bukkit.event.inventory.InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player p)) return;
		var top = event.getView().getTopInventory().getType();
		if (top == org.bukkit.event.inventory.InventoryType.CRAFTING || top == org.bukkit.event.inventory.InventoryType.CREATIVE
				|| top == org.bukkit.event.inventory.InventoryType.PLAYER) return;
		if (event.getInventory().getHolder(false) instanceof dev.atlasac.gui.Menu) return;
		PlayerData d = plugin.data().get(p);
		if (d == null || d.bypass || p.getGameMode() == GameMode.CREATIVE) return;
		if (plugin.checks().chestStealer.onClick(d, System.currentTimeMillis())) event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onUseStart(org.bukkit.event.player.PlayerInteractEvent event) {
		var a = event.getAction();
		if (a != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && a != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
		var item = event.getItem();
		if (item == null) return;
		String k = item.getType().getKey().getKey();
		if (item.getType().isEdible() || k.contains("potion") || k.equals("milk_bucket") || k.equals("honey_bottle")) {
			PlayerData d = plugin.data().get(event.getPlayer());
			if (d != null) d.useStartTick = tick();
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
		Player p = event.getPlayer();
		PlayerData d = plugin.data().get(p);
		if (d == null || d.bypass || p.getGameMode() == GameMode.CREATIVE || d.useStartTick < 0) return;
		int used = tick() - d.useStartTick;
		d.useStartTick = -1;
		if (plugin.checks().fastUse.check(d, used)) event.setCancelled(true);
	}

	// ───────────────────────── Ausnahmen ─────────────────────────

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player p)) return;
		PlayerData d = plugin.data().get(p);
		if (d == null) return;
		d.lastDamageTick = tick();
		d.movementExemptUntil = Math.max(d.movementExemptUntil, tick() + pingTicks(p) + 10);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onVelocity(PlayerVelocityEvent event) {
		Player p = event.getPlayer();
		PlayerData d = plugin.data().get(p);
		if (d == null) return;
		int tick = tick();
		// Nur das Anwendungsfenster des Knockbacks aussetzen – lange Hurt-Time-Ausnahmen sind ein klassischer Bypass
		d.movementExemptUntil = Math.max(d.movementExemptUntil, tick + Math.min(pingTicks(p), 20) + 8);
		if (!d.bypass && tick - d.lastDamageTick <= 1) {
			plugin.checks().velocity.onVelocity(d, p, event.getVelocity(), tick);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event) {
		exempt(event.getPlayer(), 10, event.getTo());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRespawn(PlayerRespawnEvent event) {
		exempt(event.getPlayer(), 20, event.getRespawnLocation());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldChange(PlayerChangedWorldEvent event) {
		exempt(event.getPlayer(), 20, event.getPlayer().getLocation());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onFlight(PlayerToggleFlightEvent event) {
		movementExempt(event.getPlayer(), 20);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onGlide(EntityToggleGlideEvent event) {
		if (event.getEntity() instanceof Player p) movementExempt(p, 20);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent event) {
		pistonExempt(event.getBlock());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent event) {
		pistonExempt(event.getBlock());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onProjectileHit(ProjectileHitEvent event) {
		// Wind Charges stossen Spieler ohne Schaden weg
		if (!event.getEntity().getType().getKey().getKey().contains("wind_charge")) return;
		for (Entity e : event.getEntity().getNearbyEntities(6, 6, 6)) {
			if (e instanceof Player p) movementExempt(p, 40);
		}
	}

	private void pistonExempt(Block piston) {
		for (Entity e : piston.getWorld().getNearbyEntities(piston.getLocation().add(0.5, 0.5, 0.5), 4, 4, 4)) {
			if (e instanceof Player p) movementExempt(p, 20);
		}
	}

	private void movementExempt(Player p, int ticks) {
		PlayerData d = plugin.data().get(p);
		if (d != null) d.movementExemptUntil = Math.max(d.movementExemptUntil, tick() + pingTicks(p) + ticks);
	}

	private void exempt(Player p, int ticks, Location to) {
		PlayerData d = plugin.data().get(p);
		if (d == null) return;
		if (to != null) {
			d.teleportRotationCheck = true;
			d.teleportX = to.getX();
			d.teleportY = to.getY();
			d.teleportZ = to.getZ();
			d.teleportYaw = to.getYaw();
			d.teleportPitch = to.getPitch();
			d.teleportCheckUntil = tick() + pingTicks(p) + 40;
		}
		d.exemptUntil = Math.max(d.exemptUntil, tick() + pingTicks(p) + ticks);
		d.resetMovement();
		d.pendingVelocity = null;
		d.fallDistance = 0;
		d.spoofedFall = false;
		if (to != null) d.lastLegit = to.clone();
	}

	// ───────────────────────── Join / Quit ─────────────────────────

	@EventHandler(priority = EventPriority.LOWEST)
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		PlayerData d = plugin.data().create(p);
		d.bypass = p.hasPermission(AtlasAC.PERM_BYPASS);
		d.exemptUntil = tick() + 60;
		d.lastLegit = p.getLocation();
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		plugin.data().remove(event.getPlayer().getUniqueId());
	}
}
