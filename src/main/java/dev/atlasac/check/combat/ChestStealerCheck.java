package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;

import java.util.ArrayDeque;

/** Container in Rekordzeit leergeraeumt: 6 Klicks in unter 200 ms (Menschen schaffen ~10-12/s). */
public final class ChestStealerCheck extends Check {
	public ChestStealerCheck(AtlasAC plugin) {
		super(plugin, "cheststealer", "ChestStealer");
	}

	/** @return true = Klick abbrechen */
	public boolean onClick(PlayerData data, long nowMs) {
		if (!enabled()) return false;
		ArrayDeque<Long> clicks = data.containerClicks;
		clicks.addLast(nowMs);
		while (clicks.size() > 6) clicks.pollFirst();
		if (clicks.size() == 6 && nowMs - clicks.peekFirst() < 200) {
			clicks.clear();
			if (bufferUp(data.buffers, 1)) {
				flag(data, "6 Container-Klicks in unter 200 ms");
			}
			return cancel();
		}
		return false;
	}
}
