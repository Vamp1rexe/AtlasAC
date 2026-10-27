package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;

/** Yaw springt >= 320° (Client setzt Rotation modulo 360 – typisch fuer Aura/Rotations-Module). */
public final class AimSnapCheck extends Check {
	public AimSnapCheck(AtlasAC plugin) {
		super(plugin, "aim-snap", "Aim (Modulo360)");
	}

	public void check(PlayerData data, float deltaYaw, float lastDeltaYaw) {
		if (!enabled()) return;
		if (Math.abs(deltaYaw) >= 320 && Math.abs(lastDeltaYaw) < 30) {
			if (bufferUp(data.buffers, 1)) flag(data, "deltaYaw=" + Text.fmt1(deltaYaw));
		}
	}
}
