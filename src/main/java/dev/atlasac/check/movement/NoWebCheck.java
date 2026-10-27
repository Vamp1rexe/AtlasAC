package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;

/** Volles Tempo in Spinnweben (Vanilla bremst auf 25 %). */
public final class NoWebCheck extends Check {
	public NoWebCheck(AtlasAC plugin) {
		super(plugin, "noweb", "NoWeb");
	}

	public boolean check(PlayerData data, double horizontal, double speedAttribute) {
		if (!enabled()) return false;
		double max = 0.216 * (speedAttribute / 0.1) * 0.25 * 1.4 + 0.03;
		if (horizontal > max) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "speed in Spinnweben " + Text.fmt(horizontal) + " (max " + Text.fmt(max) + ")");
				return true;
			}
		} else {
			bufferDown(data.buffers, 0.1);
		}
		return false;
	}
}
