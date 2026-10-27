package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;

/** Leitern/Ranken schneller hochklettern als Vanilla (0.2 Bloecke pro Tick). */
public final class FastLadderCheck extends Check {
	public FastLadderCheck(AtlasAC plugin) {
		super(plugin, "fastladder", "FastLadder");
	}

	public boolean check(PlayerData data, double deltaY) {
		if (!enabled()) return false;
		if (deltaY > 0.2 + 0.01) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "klettert " + Text.fmt(deltaY) + " pro Tick (max 0.2)");
				return true;
			}
		} else if (deltaY > 0) {
			bufferDown(data.buffers, 0.1);
		}
		return false;
	}
}
