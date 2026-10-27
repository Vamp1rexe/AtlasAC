package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;

/**
 * Horizontale Vorhersage am Boden (Vanilla-Formel):
 * neu <= alt * (Reibung * 0.91) + Speed-Attribut * (0.216 / Reibung^3) (+0.2 beim Sprint-Sprung).
 * Erkennt Speed, Strafe, Bhop-Boosts und Ground-Speed ohne Springen.
 */
public final class GroundSpeedCheck extends Check {
	private double threshold;

	public GroundSpeedCheck(AtlasAC plugin) {
		super(plugin, "prediction-ground", "Prediction (Speed)");
	}

	@Override
	protected void onReload() {
		threshold = cfg.getDouble("threshold", 0.008);
	}

	public boolean check(PlayerData data, double horizontal, double lastHorizontal, double slipperiness,
						 double speedAttribute, boolean jumped, double extra) {
		if (!enabled()) return false;
		double accel = speedAttribute * (0.21600002 / (slipperiness * slipperiness * slipperiness));
		double max = lastHorizontal * slipperiness * 0.91 + accel + (jumped ? 0.2 : 0) + threshold + extra;
		if (horizontal > max && horizontal > 0.15) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "speed=" + Text.fmt(horizontal) + " max=" + Text.fmt(max) + (jumped ? " (Sprung)" : ""));
				return true;
			}
		} else {
			bufferDown(data.buffers, 0.05);
		}
		return false;
	}
}
