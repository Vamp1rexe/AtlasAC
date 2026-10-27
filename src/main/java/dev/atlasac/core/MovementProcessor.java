package dev.atlasac.core;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.CheckManager;
import dev.atlasac.data.MoveSample;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;

/** Wertet jeden Server-Tick die gesammelten Flying-Packets aller Spieler aus. */
public final class MovementProcessor {
	private final AtlasAC plugin;

	public MovementProcessor(AtlasAC plugin) {
		this.plugin = plugin;
	}

	public void tick(int tick) {
		CheckManager checks = plugin.checks();
		for (PlayerData d : plugin.data().all()) {
			Player p = d.player();
			if (!p.isOnline()) continue;
			d.inVehicle = p.isInsideVehicle();
			d.useTicks = p.hasActiveItem() ? d.useTicks + 1 : 0;
			d.sneakTicks = p.isSneaking() ? d.sneakTicks + 1 : 0;
			var topType = p.getOpenInventory().getTopInventory().getType();
			boolean container = topType != org.bukkit.event.inventory.InventoryType.CRAFTING
					&& topType != org.bukkit.event.inventory.InventoryType.CREATIVE
					&& topType != org.bukkit.event.inventory.InventoryType.PLAYER;
			d.containerOpenTicks = container ? d.containerOpenTicks + 1 : 0;
			MoveSample s;
			while ((s = d.moves.poll()) != null) {
				if (process(d, p, s, tick, checks)) {
					d.moves.clear();
					break;
				}
			}
			checks.velocity.tick(d, p, tick);
			Location eye = p.getEyeLocation();
			d.history.record(tick, p.getBoundingBox(), eye.getX(), eye.getY(), eye.getZ());
		}
	}

	/** @return true, wenn ein Setback ausgefuehrt wurde */
	private boolean process(PlayerData d, Player p, MoveSample s, int tick, CheckManager checks) {
		boolean exempt = movementExempt(p, d, tick);

		if (s.hasRotation()) {
			if (d.hasLastRotation && !d.bypass && tick >= d.exemptUntil) {
				float dYaw = s.yaw() - d.lastYaw;
				float dPitch = s.pitch() - d.lastPitch;
				boolean combat = tick - d.lastAttackServerTick < 40;
				checks.aimSnap.check(d, dYaw, d.lastDeltaYaw);
				checks.aimSensitivity.check(d, dPitch, d.lastDeltaPitch, s.pitch(), combat);
				d.lastDeltaYaw = dYaw;
				d.lastDeltaPitch = dPitch;
			} else {
				d.lastDeltaYaw = 0;
				d.lastDeltaPitch = 0;
			}
			d.lastYaw = s.yaw();
			d.lastPitch = s.pitch();
			d.hasLastRotation = true;
		}

		if (s.hasPosition() && s.hasRotation()) {
			checks.noRotate.check(d, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
		}
		if (d.teleportRotationCheck && tick > d.teleportCheckUntil) d.teleportRotationCheck = false;

		if (!s.hasPosition()) {
			d.hasLastDelta = false;
			d.lastSampleTick = Long.MIN_VALUE;
			return false;
		}

		World world = p.getWorld();
		if (!d.hasLast) {
			d.lastX = s.x();
			d.lastY = s.y();
			d.lastZ = s.z();
			d.hasLast = true;
			d.hasLastDelta = false;
			d.lastSampleTick = s.clientTick();
			return false;
		}

		double dx = s.x() - d.lastX, dy = s.y() - d.lastY, dz = s.z() - d.lastZ;
		boolean consecutive = d.tickEndSupported && s.clientTick() == d.lastSampleTick + 1;
		BoundingBox box = Physics.boxAt(p, s.x(), s.y(), s.z());
		boolean serverGround = Physics.onGround(world, box);
		boolean jumped = d.lastServerGround && !serverGround && dy > 0.3;
		checks.velocity.onMove(d, dx, dz, jumped, tick);

		boolean setback = false;
		if (!exempt) {
			BoundingBox fromBox = Physics.boxAt(p, d.lastX, d.lastY, d.lastZ);
			if (checks.phase.check(d, world, fromBox, box, Math.sqrt(dx * dx + dy * dy + dz * dz)) && checks.phase.cancel()) setback = true;
			// Fluessigkeiten zaehlen sonst als "komplex" und waeren eine Luecke (Jesus / onGround ueber Wasser)
			if (checks.jesus.check(d, Physics.overLiquidWithoutCollision(world, box), dy, s.onGround()) && checks.jesus.cancel()) setback = true;

			if (consecutive) {
				double h = Math.hypot(dx, dz), lastH = Math.hypot(d.lastDeltaX, d.lastDeltaZ);
				double speedAttr = attr(p, org.bukkit.attribute.Attribute.MOVEMENT_SPEED, 0.1);
				// Boden-Speed (auch ohne Springen) – Vanilla-Reibung + Beschleunigung
				if (d.lastServerGround && !Physics.fluidNearby(world, box) && !Physics.standableEntitiesNearby(p, box)) {
					double slip = Physics.slipperiness(world, d.lastX, d.lastY, d.lastZ);
					double push = Math.min(0.3, 0.05 * Physics.pushingEntities(p, box));
					if (checks.groundSpeed.check(d, h, lastH, slip, speedAttr, jumped, push) && checks.groundSpeed.cancel()) setback = true;
				}
				// Step: mehr als Step-Hoehe hoch, ohne den Boden zu verlassen
				if (d.lastServerGround && serverGround && dy > 0) {
					if (checks.step.check(d, dy, attr(p, org.bukkit.attribute.Attribute.STEP_HEIGHT, 0.6)) && checks.step.cancel()) setback = true;
				}
				// Laufen mit offener Kiste/Ofen
				int pingTicks = (int) Math.ceil(Math.min(p.getPing(), 1000) / 50.0);
				if (d.containerOpenTicks > pingTicks + 6 && d.lastServerGround && serverGround) {
					if (checks.invMove.check(d, h) && checks.invMove.cancel()) setback = true;
				}
				// Spinnweben
				if (Physics.inCobweb(world, box)) {
					if (checks.noWeb.check(d, h, speedAttr) && checks.noWeb.cancel()) setback = true;
				}
			}
		} else if (consecutive && p.isClimbing() && climbCheckAllowed(p, d, tick) && !d.lastServerGround) {
			if (checks.fastLadder.check(d, dy) && checks.fastLadder.cancel()) setback = true;
		}
		if (!exempt && !Physics.complexNearby(world, box)) {
			if (checks.groundSpoof.check(d, s.onGround(), serverGround)) d.spoofedFall = true;

			if (consecutive) {
				boolean ceiling = Physics.ceiling(world, box);
				double h = Math.hypot(dx, dz), lastH = Math.hypot(d.lastDeltaX, d.lastDeltaZ);
				if (d.lastServerGround && serverGround) {
					if (checks.noSlow.check(d, p, h) && checks.noSlow.cancel()) setback = true;
				}
				if (d.lastServerGround && !serverGround && dy > 0 && !ceiling) {
					if (checks.jump.check(d, p, dy) && checks.jump.cancel()) setback = true;
				}
				if (!d.lastServerGround && !serverGround && d.hasLastDelta && !ceiling) {
					// Nur Boote/Shulker/Minecarts geben echte Kollision – andere Entitaeten schieben nur leicht.
					// (Frueher: komplette Ausnahme bei Entitaeten = bekannter Grim-Speed-Bypass "Collide")
					if (!Physics.standableEntitiesNearby(p, box)) {
						if (checks.gravity.check(d, dy, d.lastDeltaY) && checks.gravity.cancel()) setback = true;
						double push = Math.min(0.3, 0.05 * Physics.pushingEntities(p, box));
						if (checks.air.check(d, h, lastH, push) && checks.air.cancel()) setback = true;
					}
				}
			}
		}

		// Eigene Fallhoehe – gleicht NoFall (gespoofter Boden) aus
		if (!serverGround) {
			if (dy < 0) d.fallDistance -= dy;
		} else {
			if (d.spoofedFall && d.fallDistance > 3.5 && checks.groundSpoof.cancel() && !exempt) {
				p.damage(d.fallDistance - 3.0);
			}
			d.fallDistance = 0;
			d.spoofedFall = false;
		}

		d.lastDeltaX = dx;
		d.lastDeltaY = dy;
		d.lastDeltaZ = dz;
		d.hasLastDelta = consecutive;
		d.lastServerGround = serverGround;
		d.lastClientGround = s.onGround();
		d.serverAirTicks = serverGround ? 0 : d.serverAirTicks + 1;
		d.lastX = s.x();
		d.lastY = s.y();
		d.lastZ = s.z();
		d.lastSampleTick = s.clientTick();

		if (setback && d.lastLegit != null && d.lastLegit.getWorld() == world) {
			Location to = d.lastLegit.clone();
			to.setYaw(d.lastYaw);
			to.setPitch(d.lastPitch);
			p.teleport(to);
			return true;
		}
		if (serverGround && !setback) {
			d.lastLegit = new Location(world, s.x(), s.y(), s.z());
		}
		return false;
	}

	private static double attr(Player p, org.bukkit.attribute.Attribute a, double def) {
		var inst = p.getAttribute(a);
		return inst == null ? def : inst.getValue();
	}

	/** Wie movementExempt, nur dass Klettern hier gerade geprueft wird. */
	private static boolean climbCheckAllowed(Player p, PlayerData d, int tick) {
		GameMode gm = p.getGameMode();
		if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || d.bypass) return false;
		if (tick < d.exemptUntil || tick < d.movementExemptUntil || d.pendingVelocity != null) return false;
		if (p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isRiptiding() || p.isInWater()) return false;
		return !p.hasPotionEffect(PotionEffectType.LEVITATION) && !p.hasPotionEffect(PotionEffectType.JUMP_BOOST);
	}

	private static boolean movementExempt(Player p, PlayerData d, int tick) {
		GameMode gm = p.getGameMode();
		if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
		if (AtlasAC.instance().freeze().isFrozen(p.getUniqueId())) return true;
		if (d.bypass || tick < d.exemptUntil || tick < d.movementExemptUntil || d.pendingVelocity != null) return true;
		if (p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isRiptiding()
				|| p.isDead() || p.isClimbing() || p.isInWater() || p.isSwimming() || p.isSleeping()) return true;
		return p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING);
	}
}
