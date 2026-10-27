package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import dev.atlasac.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Anti-Knockback: setzt der Spieler den erhaltenen Knockback auch um? */
public final class VelocityCheck extends Check {
	private double minRatio;

	public VelocityCheck(AtlasAC plugin) {
		super(plugin, "velocity", "Velocity");
	}

	@Override
	protected void onReload() {
		minRatio = cfg.getDouble("min-ratio", 0.45);
	}

	public void onVelocity(PlayerData data, Player player, Vector velocity, int tick) {
		if (!enabled()) return;
		double h = Math.hypot(velocity.getX(), velocity.getZ());
		if (h < 0.15) return;
		int pingTicks = (int) Math.ceil(player.getPing() / 50.0);
		data.pendingVelocity = velocity.clone();
		data.velocityStart = tick + Math.max(0, pingTicks - 1);
		data.velocityDeadline = data.velocityStart + 7;
		data.velocityMaxHorizontal = 0;
		data.velocitySamples = 0;
	}

	public void onMove(PlayerData data, double dx, double dz, int tick) {
		if (data.pendingVelocity == null || tick < data.velocityStart) return;
		data.velocityMaxHorizontal = Math.max(data.velocityMaxHorizontal, Math.hypot(dx, dz));
		data.velocitySamples++;
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
		double ratio = data.velocityMaxHorizontal / expected;
		if (ratio < minRatio) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "Knockback nur " + Math.round(ratio * 100) + "% umgesetzt (erwartet " + Text.fmt(expected) + ")");
			}
		} else {
			bufferDown(data.buffers, 0.5);
		}
	}
}
