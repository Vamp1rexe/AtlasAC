package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Trifft der Blickstrahl des Angreifers die (lag-kompensierte) Hitbox ueberhaupt? */
public final class HitboxCheck extends Check {
	private double expand;

	public HitboxCheck(AtlasAC plugin) {
		super(plugin, "hitbox", "Hitbox");
	}

	@Override
	protected void onReload() {
		expand = cfg.getDouble("expand", 0.12);
	}

	public boolean check(PlayerData data, Player attacker, Entity target, PlayerData targetData, int tick) {
		if (!enabled()) return false;
		Location loc = attacker.getLocation();
		List<Vector> dirs = new ArrayList<>();
		dirs.add(Physics.direction(loc.getYaw(), loc.getPitch()));
		if (data.hasLastRotation) {
			dirs.add(Physics.direction(data.lastYaw, data.lastPitch));
			dirs.add(Physics.direction(data.lastYaw - data.lastDeltaYaw, data.lastPitch - data.lastDeltaPitch));
		}
		List<BoundingBox> boxes = CombatUtil.targetBoxes(target, targetData, attacker.getPing(), tick);
		double reach = 8.0;
		for (double[] e : CombatUtil.attackerEyes(attacker, data, tick)) {
			Vector eye = new Vector(e[0], e[1], e[2]);
			for (BoundingBox raw : boxes) {
				BoundingBox box = raw.clone().expand(expand);
				if (box.contains(eye)) {
					bufferDown(data.buffers, 0.5);
					return false;
				}
				for (Vector dir : dirs) {
					if (box.rayTrace(eye, dir, reach) != null) {
						bufferDown(data.buffers, 0.5);
						return false;
					}
				}
			}
		}
		if (bufferUp(data.buffers, 1)) {
			flag(data, "Blick verfehlt Hitbox von " + target.getName());
		}
		return cancel();
	}
}
