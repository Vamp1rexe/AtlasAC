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
		// Ein echter Crit braucht einen Fall: nach einem Sprung fruehestens ~6 Ticks in der Luft.
		// Packet-Crits / Mini-Hops bleiben dicht ueber dem Boden.
		var box = attacker.getBoundingBox();
		boolean nearGround = attacker.getWorld().hasCollisionsIn(new org.bukkit.util.BoundingBox(box.getMinX(), box.getMinY() - 0.25,
				box.getMinZ(), box.getMaxX(), box.getMinY() + 1.0E-4, box.getMaxZ()));
		if (nearGround && data.serverAirTicks < 5) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "Crit am Boden (airTicks=" + data.serverAirTicks + ")");
			}
			return cancel();
		}
		bufferDown(data.buffers, 0.25);
		return false;
	}
}
