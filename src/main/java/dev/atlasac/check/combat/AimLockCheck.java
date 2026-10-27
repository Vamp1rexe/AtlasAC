package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import dev.atlasac.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/** Aimbot-Lock: der Ziel-Offset zur Hitbox-Mitte ist unnatuerlich konstant. */
public final class AimLockCheck extends Check {
	private int samples;
	private double maxDeviation;

	public AimLockCheck(AtlasAC plugin) {
		super(plugin, "aim-lock", "Aim (Lock)");
	}

	@Override
	protected void onReload() {
		samples = Math.max(8, cfg.getInt("samples", 20));
		maxDeviation = cfg.getDouble("max-deviation", 0.35);
	}

	public void check(PlayerData data, Player attacker, Entity target) {
		if (!enabled()) return;
		Location eye = attacker.getEyeLocation();
		BoundingBox box = target.getBoundingBox();
		double dx = box.getCenterX() - eye.getX();
		double dy = box.getCenterY() - eye.getY();
		double dz = box.getCenterZ() - eye.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal < 1.5) return;
		double yawTo = Math.toDegrees(Math.atan2(-dx, dz));
		double pitchTo = -Math.toDegrees(Math.atan2(dy, horizontal));
		double offYaw = Physics.wrapDegrees(yawTo - eye.getYaw());
		double offPitch = pitchTo - eye.getPitch();
		data.aimOffsets.addLast(new double[]{offYaw, offPitch});
		if (data.aimOffsets.size() < samples) return;

		double[] stdYaw = stats(data, 0), stdPitch = stats(data, 1);
		for (int i = 0; i < samples / 2; i++) data.aimOffsets.pollFirst();
		if (stdYaw[1] < maxDeviation && stdPitch[1] < maxDeviation) {
			flag(data, "Offset-Abweichung yaw=" + Text.fmt(stdYaw[1]) + "° pitch=" + Text.fmt(stdPitch[1]) + "° ueber " + samples + " Treffer");
		}
	}

	private static double[] stats(PlayerData data, int idx) {
		double sum = 0;
		for (double[] v : data.aimOffsets) sum += v[idx];
		double mean = sum / data.aimOffsets.size();
		double var = 0;
		for (double[] v : data.aimOffsets) var += (v[idx] - mean) * (v[idx] - mean);
		return new double[]{mean, Math.sqrt(var / data.aimOffsets.size())};
	}
}
