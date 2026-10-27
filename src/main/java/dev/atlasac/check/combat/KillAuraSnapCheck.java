package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;
import org.bukkit.Bukkit;

/**
 * Rotation springt im Tick vor dem Schlag aus dem Stillstand weit aufs Ziel
 * (Silent-Aura: "rotieren und im selben Tick angreifen"). (Netty-Thread)
 */
public final class KillAuraSnapCheck extends Check {
	private double minSnap;

	public KillAuraSnapCheck(AtlasAC plugin) {
		super(plugin, "killaura-snap", "KillAura (Snap)");
	}

	@Override
	protected void onReload() {
		minSnap = cfg.getDouble("min-snap", 45);
	}

	public void onAttack(PlayerData data) {
		if (!enabled() || !data.nettyHasRotation || Bukkit.getCurrentTick() < data.exemptUntil) return;
		if (data.nettyDeltaYaw >= minSnap && data.nettyPrevDeltaYaw < 2.5f) {
			if (bufferUp(data.nettyBuffers, 1)) {
				flag(data, "Snap " + Text.fmt1(data.nettyDeltaYaw) + "° direkt vor dem Schlag");
			}
		} else {
			bufferDown(data.nettyBuffers, 0.2);
		}
	}
}
