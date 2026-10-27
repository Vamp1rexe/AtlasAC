package dev.atlasmodcheck;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Team-Meldungen an alle mit atlasmodcheck.alerts. */
public final class Alerts {
	public static final String PERM_ALERTS = "atlasmodcheck.alerts";
	private final AtlasModCheck plugin;

	public Alerts(AtlasModCheck plugin) {
		this.plugin = plugin;
	}

	public String prefix() {
		return plugin.getConfig().getString("prefix", "<dark_gray>[<aqua>Atlas<dark_gray>] ");
	}

	public void send(String miniMessage) {
		Component c = Text.mm(miniMessage.replace("%prefix%", prefix()));
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.hasPermission(PERM_ALERTS)) p.sendMessage(c);
		}
		plugin.getLogger().info(Text.plain(c));
	}

	public void broadcast(String miniMessage) {
		if (miniMessage == null || miniMessage.isBlank()) return;
		Bukkit.broadcast(Text.mm(miniMessage.replace("%prefix%", prefix())));
	}
}
