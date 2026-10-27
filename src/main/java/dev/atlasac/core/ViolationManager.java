package dev.atlasac.core;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** Zaehlt Violations, schickt Alerts und fuehrt Strafen aus. */
public final class ViolationManager {
	private final AtlasAC plugin;

	public ViolationManager(AtlasAC plugin) {
		this.plugin = plugin;
	}

	public void flag(Check check, PlayerData data, String info) {
		if (!Bukkit.isPrimaryThread()) {
			Bukkit.getScheduler().runTask(plugin, () -> flag(check, data, info));
			return;
		}
		Player player = data.player();
		if (!player.isOnline() || data.bypass || !check.enabled()) return;

		double vl = data.violations.merge(check.id(), 1.0, Double::sum);

		long now = System.currentTimeMillis();
		long cooldown = plugin.getConfig().getLong("alerts.cooldown-ms", 300);
		Long last = data.lastAlert.get(check.id());
		if (last == null || now - last >= cooldown) {
			data.lastAlert.put(check.id(), now);
			String msg = Text.apply(plugin.getConfig().getString("alerts.format", "%prefix%%player% flagged %check% (x%vl%)"), Map.of(
					"%player%", player.getName(),
					"%check%", check.display(),
					"%vl%", String.valueOf((int) vl),
					"%max%", String.valueOf((int) check.maxVl()),
					"%info%", Text.escape(info),
					"%ping%", String.valueOf(player.getPing())));
			plugin.alerts().send(msg);
		}

		if (check.punish() && vl >= check.maxVl()) {
			data.violations.put(check.id(), 0.0);
			punish(player, check.display());
		}
	}

	public void punish(Player player, String reason) {
		List<String> commands = plugin.getConfig().getStringList("punishment.commands");
		for (String cmd : commands) {
			String c = cmd.replace("%player%", player.getName()).replace("%check%", reason);
			if (c.startsWith("/")) c = c.substring(1);
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
		}
		plugin.alerts().broadcast(plugin.getConfig().getString("punishment.broadcast", "")
				.replace("%player%", player.getName()).replace("%check%", reason));
	}

	/** Regelmaessiger VL-Abbau. */
	public void decay() {
		for (PlayerData d : plugin.data().all()) {
			for (Check c : plugin.checks().all()) {
				d.violations.computeIfPresent(c.id(), (k, v) -> v - c.decay() <= 0 ? null : v - c.decay());
			}
		}
	}
}
