package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import dev.atlasac.util.Text;

/**
 * Vertikale Bewegungs-Vorhersage (Vanilla-Physik): im freien Fall gilt
 * motionY(n+1) = (motionY(n) - 0.08) * 0.98. Abweichungen = Fly, Glide, Step, Spider ...
 */
public final class GravityCheck extends Check {
	private double threshold;

	public GravityCheck(AtlasAC plugin) {
		super(plugin, "prediction-gravity", "Prediction (Gravity)");
	}

	@Override
	protected void onReload() {
		threshold = cfg.getDouble("threshold", 0.03);
	}

	/** @return true, wenn geflaggt wurde */
	public boolean check(PlayerData data, double deltaY, double lastDeltaY) {
		if (!enabled()) return false;
		double predicted = (lastDeltaY - Physics.GRAVITY) * Physics.AIR_DRAG;
		if (Math.abs(predicted) < 0.003) predicted = 0;
		double offset = deltaY - predicted;
		if (Math.abs(offset) > threshold) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "dy=" + Text.fmt(deltaY) + " vorhergesagt=" + Text.fmt(predicted) + " offset=" + Text.fmt(offset));
				return true;
			}
		} else {
			bufferDown(data.buffers, 0.05);
		}
		return false;
	}
}
