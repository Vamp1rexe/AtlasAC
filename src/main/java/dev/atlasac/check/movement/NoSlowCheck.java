package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Item benutzen (Essen, Bogen, Schild ...) bremst auf 20 %, Schleichen auf SNEAKING_SPEED (Standard 30 %).
 * Vanilla-Endgeschwindigkeit am Boden: 0.216 * (Speed-Attribut / 0.1) * Faktor.
 */
public final class NoSlowCheck extends Check {
	private static final int SETTLE_TICKS = 6;

	public NoSlowCheck(AtlasAC plugin) {
		super(plugin, "noslow", "NoSlow");
	}

	public boolean check(PlayerData data, Player p, double horizontal) {
		if (!enabled()) return false;
		boolean using = data.useTicks >= SETTLE_TICKS;
		boolean sneaking = data.sneakTicks >= SETTLE_TICKS;
		if (!using && !sneaking) return false;

		Block below = p.getLocation().clone().subtract(0, 0.5, 0).getBlock();
		String key = below.getType().getKey().getKey();
		if (key.contains("ice") || below.getType() == Material.SLIME_BLOCK || key.contains("soul")) return false;

		double speed = 0.1;
		AttributeInstance ms = p.getAttribute(Attribute.MOVEMENT_SPEED);
		if (ms != null) speed = ms.getValue();
		double factor = 1.0;
		if (using) factor = Math.min(factor, 0.2);
		if (sneaking) {
			AttributeInstance sn = p.getAttribute(Attribute.SNEAKING_SPEED);
			factor = Math.min(factor, sn != null ? sn.getValue() : 0.3);
		}
		double max = 0.216 * (speed / 0.1) * factor * 1.25 + 0.03;

		if (horizontal > max) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, (using ? "Item benutzen" : "Schleichen") + " speed=" + Text.fmt(horizontal) + " max=" + Text.fmt(max)
						+ (using && p.isSprinting() ? " (sprintet)" : ""));
				return true;
			}
		} else {
			bufferDown(data.buffers, 0.1);
		}
		return false;
	}
}
