package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;

/** Laufen mit offenem Container (Kiste, Ofen ...) – Vanilla blockiert Bewegung in GUIs. */
public final class InvMoveCheck extends Check {
	public InvMoveCheck(AtlasAC plugin) {
		super(plugin, "invmove", "InventoryMove");
	}

	public boolean check(PlayerData data, double horizontal) {
		if (!enabled()) return false;
		if (horizontal > 0.1) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "laeuft mit offenem Inventar (speed=" + Text.fmt(horizontal) + ")");
				return true;
			}
		} else {
			bufferDown(data.buffers, 0.25);
		}
		return false;
	}
}
