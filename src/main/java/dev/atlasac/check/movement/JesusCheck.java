package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;

/** Auf Wasser/Lava laufen ohne Kollision (Jesus/Water-Walk, auch per gespooftem onGround). */
public final class JesusCheck extends Check {
	public JesusCheck(AtlasAC plugin) {
		super(plugin, "jesus", "Jesus");
	}

	public boolean check(PlayerData data, boolean overLiquidWithoutCollision, double deltaY, boolean clientGround) {
		if (!enabled()) return false;
		if (overLiquidWithoutCollision && (Math.abs(deltaY) < 0.03 || clientGround)) {
			data.jesusTicks++;
			if (data.jesusTicks > 6) {
				data.jesusTicks = 0;
				flag(data, "laeuft auf Fluessigkeit" + (clientGround ? " (onGround gespooft)" : ""));
				return true;
			}
		} else {
			data.jesusTicks = Math.max(0, data.jesusTicks - 1);
		}
		return false;
	}
}
