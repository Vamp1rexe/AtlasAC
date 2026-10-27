package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import dev.atlasac.util.Text;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/** Bewegung in feste Bloecke hinein (Phase/VClip) oder unmoeglich weite Spruenge (ClickTP). */
public final class PhaseCheck extends Check {
	private static final double MAX_DISTANCE = 10.0;

	public PhaseCheck(AtlasAC plugin) {
		super(plugin, "phase", "Phase");
	}

	public boolean check(PlayerData data, World world, BoundingBox from, BoundingBox to, double distance) {
		if (!enabled()) return false;
		if (distance > MAX_DISTANCE) {
			flag(data, "Sprung um " + Text.fmt1(distance) + " Bloecke");
			return true;
		}
		BoundingBox shrunkTo = to.clone().expand(-0.05);
		if (Physics.blockCollides(world, shrunkTo) && !Physics.blockCollides(world, from.clone().expand(-0.05))) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "Bewegung in einen Block");
			}
			return true;
		}
		bufferDown(data.buffers, 0.05);
		return false;
	}
}
