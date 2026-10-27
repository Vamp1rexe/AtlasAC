package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import dev.atlasac.util.Text;

/**
 * Horizontale Vorhersage in der Luft: neue Geschwindigkeit <= alte * 0.91 + Sprint-Luftbeschleunigung.
 * Effekte wie Speed wirken in der Luft nicht – Speed-/Strafe-/Boost-Hacks fallen sofort auf.
 */
public final class AirCheck extends Check {
	private double threshold;

	public AirCheck(AtlasAC plugin) {
		super(plugin, "prediction-air", "Prediction (Air)");
	}

	@Override
	protected void onReload() {
		threshold = cfg.getDouble("threshold", 0.006);
	}

	public boolean check(PlayerData data, double horizontal, double lastHorizontal) {
		if (!enabled()) return false;
		double max = lastHorizontal * Physics.AIR_FRICTION + Physics.SPRINT_AIR_ACCEL + threshold;
		if (horizontal > max && horizontal > 0.1) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "speed=" + Text.fmt(horizontal) + " max=" + Text.fmt(max));
				return true;
			}
		} else {
			bufferDown(data.buffers, 0.05);
		}
		return false;
	}
}
