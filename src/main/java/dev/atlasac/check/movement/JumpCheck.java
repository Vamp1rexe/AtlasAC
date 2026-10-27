package dev.atlasac.check.movement;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import dev.atlasac.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Absprung hoeher als Vanilla (0.42 + Jump-Boost). */
public final class JumpCheck extends Check {
	public JumpCheck(AtlasAC plugin) {
		super(plugin, "prediction-jump", "Prediction (Jump)");
	}

	public boolean check(PlayerData data, Player player, double deltaY) {
		if (!enabled()) return false;
		double max = Physics.JUMP_VELOCITY;
		PotionEffect jb = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
		if (jb != null) max += 0.1 * (jb.getAmplifier() + 1);
		if (deltaY > max + 0.002) {
			if (bufferUp(data.buffers, 1)) {
				flag(data, "Sprung dy=" + Text.fmt(deltaY) + " max=" + Text.fmt(max));
				return true;
			}
		} else {
			bufferDown(data.buffers, 0.1);
		}
		return false;
	}
}
