package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import dev.atlasac.util.Text;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.List;

/** Reach mit Lag-Compensation ueber die Positions-Historie des Ziels. */
public final class ReachCheck extends Check {
	private double tolerance;
	private double mobTolerance;
	private boolean ignoreSpears;

	public ReachCheck(AtlasAC plugin) {
		super(plugin, "reach", "Reach");
	}

	@Override
	protected void onReload() {
		tolerance = cfg.getDouble("tolerance", 0.1);
		mobTolerance = cfg.getDouble("mob-tolerance", 0.4);
		ignoreSpears = cfg.getBoolean("ignore-spears", true);
	}

	/** @return true, wenn der Treffer abgebrochen werden soll */
	public boolean check(PlayerData data, Player attacker, Entity target, PlayerData targetData, int tick) {
		if (!enabled()) return false;
		if (ignoreSpears && attacker.getInventory().getItemInMainHand().getType().getKey().getKey().contains("spear")) {
			return false;
		}
		double range = 3.0;
		AttributeInstance attr = attacker.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
		if (attr != null) range = attr.getValue();
		double max = range + tolerance + (targetData == null ? mobTolerance : 0);

		List<BoundingBox> boxes = CombatUtil.targetBoxes(target, targetData, attacker.getPing(), tick);
		List<double[]> eyes = CombatUtil.attackerEyes(attacker, data, tick);
		double best = Double.MAX_VALUE;
		for (double[] eye : eyes) {
			for (BoundingBox box : boxes) {
				best = Math.min(best, Physics.distance(eye[0], eye[1], eye[2], box));
			}
		}
		if (best > max) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "distance=" + Text.fmt(best) + " max=" + Text.fmt(max) + " target=" + target.getName());
			}
			return cancel();
		}
		bufferDown(data.buffers, 0.25);
		return false;
	}
}
