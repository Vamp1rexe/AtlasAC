package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import org.bukkit.Bukkit;

/**
 * Balance-Timer wie bei Grim: jeder Client-Tick (Tick-End-Packet) "kostet" 50ms.
 * Schickt der Client mehr Ticks als Echtzeit vergeht, steigt die Balance.
 */
public final class TimerCheck extends Check {
	private long maxBalance;
	private long minBalance;

	public TimerCheck(AtlasAC plugin) {
		super(plugin, "timer", "Timer");
	}

	@Override
	protected void onReload() {
		maxBalance = cfg.getLong("max-balance-ms", 150) * 1_000_000L;
		minBalance = cfg.getLong("min-balance-ms", -500) * 1_000_000L;
	}

	public void onTickEnd(PlayerData data, long nanos) {
		if (data.lastTickEndNanos == 0 || Bukkit.getCurrentTick() < data.exemptUntil) {
			data.lastTickEndNanos = nanos;
			data.timerBalanceNanos = 0;
			return;
		}
		long elapsed = nanos - data.lastTickEndNanos;
		data.lastTickEndNanos = nanos;
		data.timerBalanceNanos += 50_000_000L - elapsed;
		if (data.timerBalanceNanos < minBalance) data.timerBalanceNanos = minBalance;
		if (data.timerBalanceNanos > maxBalance) {
			if (enabled() && bufferUp(data.nettyBuffers, 1)) {
				flag(data, "Client ist " + (data.timerBalanceNanos / 1_000_000L) + "ms voraus");
			}
			data.timerBalanceNanos = 0;
		}
	}
}
