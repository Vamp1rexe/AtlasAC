package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;

/** Mehrere verschiedene Ziele im selben Client-Tick angegriffen (Netty-Thread). */
public final class KillAuraMultiCheck extends Check {
	public KillAuraMultiCheck(AtlasAC plugin) {
		super(plugin, "killaura-multi", "KillAura (Multi)");
	}

	public void onAttack(PlayerData data, int entityId) {
		if (enabled() && data.tickEndSupported
				&& data.lastAttackClientTick == data.clientTick
				&& data.lastAttackEntityId != entityId) {
			if (bufferUp(data.nettyBuffers, 1)) {
				flag(data, "2 Ziele in einem Tick (#" + data.lastAttackEntityId + ", #" + entityId + ")");
			}
		}
		data.lastAttackClientTick = data.clientTick;
		data.lastAttackEntityId = entityId;
	}
}
