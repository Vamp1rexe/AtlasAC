package dev.atlasac.core;

import dev.atlasac.AtlasAC;
import dev.atlasac.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Verteilt Alerts an Teammitglieder mit atlasac.alerts. */
public final class AlertManager {
	public static final String PERM_ALERTS = "atlasac.alerts";

	private final AtlasAC plugin;
	private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();

	public AlertManager(AtlasAC plugin) {
		this.plugin = plugin;
	}

	/** @return true, wenn Alerts jetzt aktiv sind */
	public boolean toggle(Player player) {
		if (!disabled.remove(player.getUniqueId())) {
			disabled.add(player.getUniqueId());
			return false;
		}
		return true;
	}

	public String prefix() {
		return plugin.getConfig().getString("prefix", "<dark_gray>[<aqua>Atlas<dark_gray>] ");
	}

	/** Muss im Haupt-Thread aufgerufen werden. Der Text darf %prefix% enthalten. */
	public void send(String miniMessage) {
		Component c = Text.mm(miniMessage.replace("%prefix%", prefix()));
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.hasPermission(PERM_ALERTS) && !disabled.contains(p.getUniqueId())) {
				p.sendMessage(c);
			}
		}
		if (plugin.getConfig().getBoolean("alerts.console", true)) {
			plugin.getLogger().info(Text.plain(c));
		}
	}

	public void broadcast(String miniMessage) {
		if (miniMessage == null || miniMessage.isBlank()) return;
		Bukkit.broadcast(Text.mm(miniMessage.replace("%prefix%", prefix())));
	}
}
