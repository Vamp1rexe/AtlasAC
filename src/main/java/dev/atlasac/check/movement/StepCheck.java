package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;

/** Hoeher als die Step-Hoehe (Standard 0.6) hochgestiegen, ohne zu springen. */
public final class StepCheck extends Check {
	public StepCheck(AtlasAC plugin) {
		super(plugin, "step", "Step");
	}

	public boolean check(PlayerData data, double deltaY, double stepHeight) {
		if (!enabled() || deltaY <= stepHeight + 0.001) return false;
		if (bufferUp(data.buffers, 1)) {
			flag(data, "Step " + Text.fmt(deltaY) + " (max " + Text.fmt(stepHeight) + ")");
			return true;
		}
		return false;
	}
}
