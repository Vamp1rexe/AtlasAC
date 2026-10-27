package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import dev.atlasac.util.Text;

/** Nach einem Server-Teleport muss der Client die vom Server gesetzte Rotation uebernehmen (NoRotate). */
public final class NoRotateCheck extends Check {
	public NoRotateCheck(AtlasAC plugin) {
		super(plugin, "badpackets-rotate", "BadPackets (NoRotate)");
	}

	public void check(PlayerData data, double x, double y, double z, float yaw, float pitch) {
		if (!data.teleportRotationCheck) return;
		if (Math.abs(x - data.teleportX) > 1.0E-3 || Math.abs(y - data.teleportY) > 1.0E-3 || Math.abs(z - data.teleportZ) > 1.0E-3) return;
		data.teleportRotationCheck = false;
		if (!enabled()) return;
		double dYaw = Math.abs(Physics.wrapDegrees(yaw - data.teleportYaw));
		double dPitch = Math.abs(pitch - data.teleportPitch);
		if (dYaw > 0.5 || dPitch > 0.5) {
			if (bufferUp(data.buffers, 1)) flag(data, "Rotation nach Teleport ignoriert (yaw " + Text.fmt1(dYaw) + "°, pitch " + Text.fmt1(dPitch) + "°)");
		} else {
			bufferDown(data.buffers, 0.5);
		}
	}
}
