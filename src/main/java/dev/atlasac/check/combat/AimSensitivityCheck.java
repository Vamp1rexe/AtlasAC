package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import dev.atlasac.util.Text;

/**
 * Vanilla-Mausbewegungen sind immer ganzzahlige Vielfache eines Sensitivity-Faktors
 * (min. ~0.0096°). Aimbots/Smoothing erzeugen beliebige Werte -> GCD bricht zusammen.
 */
public final class AimSensitivityCheck extends Check {
	private static final double MIN_VANILLA_STEP = 0.0078125;

	public AimSensitivityCheck(AtlasAC plugin) {
		super(plugin, "aim-sensitivity", "Aim (Sensitivity)");
	}

	public void check(PlayerData data, float deltaPitch, float lastDeltaPitch, float pitch, boolean inCombat) {
		if (!enabled() || !inCombat) return;
		double a = Math.abs(deltaPitch), b = Math.abs(lastDeltaPitch);
		if (a < 1.0E-4 || b < 1.0E-4 || a > 30 || b > 30 || Math.abs(pitch) > 88) return;
		double gcd = Physics.gcd(a, b);
		if (gcd < MIN_VANILLA_STEP) {
			if (bufferUp(data.buffers, 1)) flag(data, "Rotations-GCD " + Text.fmt(gcd) + " (deltaPitch " + Text.fmt(a) + ")");
		} else {
			bufferDown(data.buffers, 0.75);
		}
	}
}
