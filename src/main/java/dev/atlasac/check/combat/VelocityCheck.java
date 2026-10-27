package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import dev.atlasac.util.Text;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Anti-Knockback: setzt der Spieler den erhaltenen Knockback auch um? */
public final class VelocityCheck extends Check {
	private double tolerance;

	public VelocityCheck(AtlasAC plugin) {
		super(plugin, "velocity", "Velocity");
	}

	@Override
	protected void onReload() {
		tolerance = cfg.getDouble("tolerance", 0.02);
	}

	public void onVelocity(PlayerData data, Player player, Vector velocity, int tick) {
		if (!enabled()) return;
		double h = Math.hypot(velocity.getX(), velocity.getZ());
		if (h < 0.2) return;
		int pingTicks = (int) Math.ceil(Math.min(player.getPing(), 1000) / 50.0);
		data.pendingVelocity = velocity.clone();
		data.velocityStart = tick + Math.max(0, pingTicks - 1);
		data.velocityDeadline = data.velocityStart + 7;
		data.velocityMaxHorizontal = -10;
		data.velocitySamples = 0;
		data.velocityJumped = false;
	}

	/** Anteil der Bewegung in Knockback-Richtung – erkennt auch Reversal/Strafe und Teil-Multiplikatoren. */
	public void onMove(PlayerData data, double dx, double dz, boolean jumped, int tick) {
		Vector v = data.pendingVelocity;
		if (v == null || tick < data.velocityStart) return;
		double h = Math.hypot(v.getX(), v.getZ());
		double projection = (dx * v.getX() + dz * v.getZ()) / h;
		data.velocityMaxHorizontal = Math.max(data.velocityMaxHorizontal, projection);
		data.velocitySamples++;
		if (jumped) data.velocityJumped = true;
	}

	public void tick(PlayerData data, Player player, int tick) {
		Vector v = data.pendingVelocity;
		if (v == null || tick <= data.velocityDeadline) return;
		data.pendingVelocity = null;
		if (!enabled() || data.velocitySamples < 2) return;
		if (player.isInsideVehicle() || player.isFlying() || player.isGliding() || player.isClimbing() || player.isInWater()) return;

		BoundingBox box = player.getBoundingBox();
		var world = player.getWorld();
		if (Physics.complexNearby(world, box)) return;
		Vector dir = new Vector(v.getX(), 0, v.getZ()).normalize().multiply(0.6);
		if (world.hasCollisionsIn(box.clone().shift(dir))) return;

		double expected = Math.hypot(v.getX(), v.getZ());
		// Eigene Eingabe kann den ersten Tick hoechstens um die Bodenbeschleunigung bremsen,
		// ein Sprint-Sprung (Jump-Reset, legit) zusaetzlich um 0.2
		double accel = 0.13;
		AttributeInstance ms = player.getAttribute(Attribute.MOVEMENT_SPEED);
		if (ms != null) accel = ms.getValue() + 0.02;
		double min = expected - accel - (data.velocityJumped ? 0.2 : 0) - tolerance;
		double taken = data.velocityMaxHorizontal;
		if (taken < min) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "Knockback " + Math.round(Math.max(0, taken) / expected * 100) + "% umgesetzt (min " + Math.round(min / expected * 100) + "%)");
			}
		} else {
			bufferDown(data.buffers, 0.5);
		}
	}
}
