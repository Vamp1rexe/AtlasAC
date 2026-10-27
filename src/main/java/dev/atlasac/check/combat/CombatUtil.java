package dev.atlasac.check.combat;

import dev.atlasac.data.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/** Lag-Compensation: alle Hitboxen, die der Angreifer beim Schlag gesehen haben kann. */
public final class CombatUtil {
	private CombatUtil() {
	}

	public static List<BoundingBox> targetBoxes(Entity target, PlayerData targetData, int attackerPing, int tick) {
		List<BoundingBox> list = new ArrayList<>();
		list.add(target.getBoundingBox());
		if (targetData != null) {
			int pingTicks = (int) Math.ceil(Math.max(0, attackerPing) / 50.0);
			// 3 Ticks Client-Interpolation + Jitter
			for (int t = tick - pingTicks - 4; t <= tick - pingTicks + 2; t++) {
				BoundingBox b = targetData.history.box(t);
				if (b != null) list.add(b);
			}
		}
		return list;
	}

	public static List<double[]> attackerEyes(Player attacker, PlayerData data, int tick) {
		List<double[]> eyes = new ArrayList<>();
		var eye = attacker.getEyeLocation();
		eyes.add(new double[]{eye.getX(), eye.getY(), eye.getZ()});
		for (int t = tick - 2; t <= tick; t++) {
			double[] e = data.history.eye(t);
			if (e != null) eyes.add(e);
		}
		return eyes;
	}
}
