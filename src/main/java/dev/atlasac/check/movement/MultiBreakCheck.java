package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;

/** Mehrere Abbau-Starts in einem Client-Tick – Nuker / PacketMine. (Netty-Thread) */
public final class MultiBreakCheck extends Check {
	public MultiBreakCheck(AtlasAC plugin) {
		super(plugin, "badpackets-break", "BadPackets (Multi-Break)");
	}

	public void onTickEnd(PlayerData data) {
		int starts = data.digStartsThisTick;
		data.digStartsThisTick = 0;
		if (!enabled() || starts <= 1) return;
		if (bufferUp(data.nettyBuffers, 1)) flag(data, starts + " Bloecke in einem Tick angefangen");
	}
}
