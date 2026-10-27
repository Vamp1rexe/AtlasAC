package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;

/** Angriff ohne Arm-Schwung im selben Client-Tick (Netty-Thread). */
public final class KillAuraNoSwingCheck extends Check {
	public KillAuraNoSwingCheck(AtlasAC plugin) {
		super(plugin, "killaura-noswing", "KillAura (NoSwing)");
	}

	public void onTickEnd(PlayerData data) {
		if (enabled() && data.attackedThisTick) {
			if (!data.swungThisTick) {
				if (bufferUp(data.nettyBuffers, 1)) flag(data, "Angriff ohne Swing");
			} else {
				bufferDown(data.nettyBuffers, 0.1);
			}
		}
		data.attackedThisTick = false;
		data.swungThisTick = false;
	}
}
