package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;

/** Klicks pro Sekunde ueber dem Limit (Netty-Thread). */
public final class AutoClickerCpsCheck extends Check {
	private int maxCps;

	public AutoClickerCpsCheck(AtlasAC plugin) {
		super(plugin, "autoclicker-cps", "AutoClicker (CPS)");
	}

	@Override
	protected void onReload() {
		maxCps = cfg.getInt("max-cps", 20);
	}

	public void onClick(PlayerData data, long nanos) {
		data.clickTimes.addLast(nanos);
		while (!data.clickTimes.isEmpty() && nanos - data.clickTimes.peekFirst() > 1_000_000_000L) {
			data.clickTimes.pollFirst();
		}
		if (!enabled()) return;
		int cps = data.clickTimes.size();
		if (cps > maxCps) {
			if (bufferUp(data.nettyBuffers, 1)) flag(data, cps + " CPS (max " + maxCps + ")");
			data.clickTimes.clear();
		} else {
			bufferDown(data.nettyBuffers, 0.02);
		}
	}
}
