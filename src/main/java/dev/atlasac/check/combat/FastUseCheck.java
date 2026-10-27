package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;

/** Essen/Traenke schneller benutzt als Vanilla (schnellstes Essen: getrockneter Seetang, 16 Ticks). */
public final class FastUseCheck extends Check {
	public FastUseCheck(AtlasAC plugin) {
		super(plugin, "fastuse", "FastUse");
	}

	/** @return true = Verbrauch abbrechen */
	public boolean check(PlayerData data, int usedTicks) {
		if (!enabled() || usedTicks < 0) return false;
		if (usedTicks < 12) {
			if (bufferUp(data.buffers, 1)) flag(data, "Item nach " + usedTicks + " Ticks verbraucht");
			return cancel();
		}
		return false;
	}
}
