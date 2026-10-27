package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import org.bukkit.entity.Player;

/** Kritische Treffer, obwohl der Spieler laut Server praktisch auf dem Boden steht (Packet-Crits). */
public final class CriticalsCheck extends Check {
	public CriticalsCheck(AtlasAC plugin) {
		super(plugin, "criticals", "Criticals");
	}

	public boolean check(PlayerData data, Player attacker, boolean critical) {
		if (!enabled() || !critical) return false;
		if (attacker.isInsideVehicle() || attacker.isFlying()) return false;
		boolean ground = Physics.onGround(attacker.getWorld(), attacker.getBoundingBox());
		if (ground && data.serverAirTicks < 2) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "Crit am Boden (airTicks=" + data.serverAirTicks + ")");
			}
			return cancel();
		}
		bufferDown(data.buffers, 0.25);
		return false;
	}
}
