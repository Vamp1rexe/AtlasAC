package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;

/** Zu gleichmaessige Klickabstaende – typisch fuer Autoclicker (Netty-Thread). */
public final class AutoClickerConsistencyCheck extends Check {
	private static final int SAMPLES = 20;
	private double minDeviation;
	private int minCps;

	public AutoClickerConsistencyCheck(AtlasAC plugin) {
		super(plugin, "autoclicker-consistency", "AutoClicker (Consistency)");
	}

	@Override
	protected void onReload() {
		minDeviation = cfg.getDouble("min-deviation-ms", 9.0);
		minCps = cfg.getInt("min-cps", 9);
	}

	public void onClick(PlayerData data, long nanos) {
		if (data.lastClickNanos != 0) {
			double interval = (nanos - data.lastClickNanos) / 1_000_000.0;
			if (interval < 400) {
				data.clickIntervals.addLast(interval);
			} else {
				data.clickIntervals.clear();
			}
		}
		data.lastClickNanos = nanos;
		if (!enabled() || data.clickIntervals.size() < SAMPLES) return;

		double sum = 0;
		for (double v : data.clickIntervals) sum += v;
		double mean = sum / data.clickIntervals.size();
		double var = 0;
		for (double v : data.clickIntervals) var += (v - mean) * (v - mean);
		double std = Math.sqrt(var / data.clickIntervals.size());
		for (int i = 0; i < SAMPLES / 2; i++) data.clickIntervals.pollFirst();

		double cps = 1000.0 / mean;
		if (cps < minCps) return;
		if (std < minDeviation) {
			if (bufferUp(data.nettyBuffers, 1)) {
				flag(data, "Abweichung " + Text.fmt1(std) + "ms bei " + Text.fmt1(cps) + " CPS");
			}
		} else {
			bufferDown(data.nettyBuffers, 0.5);
		}
	}
}
