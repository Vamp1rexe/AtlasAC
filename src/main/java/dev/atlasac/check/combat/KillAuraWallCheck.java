package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/** Schlaege durch feste Bloecke hindurch. */
public final class KillAuraWallCheck extends Check {
	public KillAuraWallCheck(AtlasAC plugin) {
		super(plugin, "killaura-wall", "KillAura (Wall)");
	}

	public boolean check(PlayerData data, Player attacker, Entity target, PlayerData targetData, int tick) {
		if (!enabled()) return false;
		Location eye = attacker.getEyeLocation();
		List<BoundingBox> boxes = CombatUtil.targetBoxes(target, targetData, attacker.getPing(), tick);
		for (BoundingBox box : boxes) {
			double cx = box.getCenterX(), cz = box.getCenterZ();
			double[] ys = {box.getCenterY(), box.getMaxY() - 0.1, box.getMinY() + 0.1};
			for (double y : ys) {
				if (visible(attacker, eye, new Vector(cx, y, cz))) {
					bufferDown(data.buffers, 0.25);
					return false;
				}
			}
		}
		if (bufferUp(data.buffers, 1)) {
			flag(data, "Treffer durch Block auf " + target.getName());
		}
		return cancel();
	}

	private static boolean visible(Player attacker, Location eye, Vector point) {
		Vector dir = point.clone().subtract(eye.toVector());
		double dist = dir.length();
		if (dist < 0.05) return true;
		RayTraceResult r = attacker.getWorld().rayTraceBlocks(eye, dir.normalize(), dist, FluidCollisionMode.NEVER, true);
		return r == null || r.getHitPosition().distance(eye.toVector()) >= dist - 0.1;
	}
}
