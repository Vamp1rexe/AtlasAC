package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import org.bukkit.Bukkit;

/**
 * Mehr als ein Bewegungs-Packet pro Client-Tick. Vanilla sendet genau eins (ausser bei Teleports).
 * Schliesst Packet-Criticals, extra Rotations-Packets (Silent-Rotations), "VanillaSpeed"-Splitting
 * und Packet-Bursts. (Netty-Thread)
 */
public final class BadPacketsCheck extends Check {
	public BadPacketsCheck(AtlasAC plugin) {
		super(plugin, "badpackets-move", "BadPackets (Multi-Move)");
	}

	public void onTickEnd(PlayerData data) {
		int moves = data.movesThisTick;
		data.movesThisTick = 0;
		if (!enabled() || moves <= 1 || data.inVehicle || Bukkit.getCurrentTick() < data.exemptUntil) return;
		if (bufferUp(data.nettyBuffers, 1)) flag(data, moves + " Bewegungs-Packets in einem Tick");
	}
}
