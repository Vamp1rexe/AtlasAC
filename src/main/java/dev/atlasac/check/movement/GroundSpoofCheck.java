package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;

/** Client meldet "auf dem Boden", obwohl unter ihm nichts ist (NoFall, Packet-Crits, Spider). */
public final class GroundSpoofCheck extends Check {
	public GroundSpoofCheck(AtlasAC plugin) {
		super(plugin, "groundspoof", "GroundSpoof");
	}

	public boolean check(PlayerData data, boolean clientGround, boolean serverGround) {
		if (!enabled() || !clientGround) return false;
		if (!serverGround) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "onGround=true ohne Boden");
				return true;
			}
		} else {
			bufferDown(data.buffers, 0.25);
		}
		return false;
	}
}
