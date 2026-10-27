package dev.atlasac.command;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Text;
import dev.atlasac.xray.OreGroup;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** /atlas – Hauptbefehl. */
public final class AtlasCommand implements BasicCommand {
	private final AtlasAC plugin;

	public AtlasCommand(AtlasAC plugin) {
		this.plugin = plugin;
	}

	@Override
	public String permission() {
		return AtlasAC.PERM_COMMAND;
	}

	private void msg(CommandSender s, String text) {
		s.sendMessage(Text.mm(plugin.alerts().prefix() + text));
	}

	@Override
	public void execute(CommandSourceStack source, String[] args) {
		CommandSender s = source.getSender();
		if (args.length == 0) {
			help(s);
			return;
		}
		switch (args[0].toLowerCase()) {
			case "alerts" -> {
				if (!(s instanceof Player p)) {
					msg(s, "<red>Nur ingame.");
					return;
				}
				msg(s, plugin.alerts().toggle(p) ? "<green>Alerts aktiviert." : "<red>Alerts deaktiviert.");
			}
			case "reload" -> {
				if (!s.hasPermission(AtlasAC.PERM_ADMIN)) {
					msg(s, "<red>Keine Rechte.");
					return;
				}
				plugin.reloadAll();
				msg(s, "<green>Config neu geladen.");
			}
			case "vl", "violations" -> {
				Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
				PlayerData d = t == null ? null : plugin.data().get(t);
				if (d == null) {
					msg(s, "<red>Spieler nicht online. <gray>/atlas vl <Spieler>");
					return;
				}
				msg(s, "<gray>Violations von <white>" + t.getName() + "<gray> (Ping " + t.getPing() + "ms):");
				boolean any = false;
				for (Check c : plugin.checks().all()) {
					Double v = d.violations.get(c.id());
					if (v == null || v < 1) continue;
					any = true;
					s.sendMessage(Text.mm("<dark_gray> » <aqua>" + c.display() + " <gray>x" + v.intValue() + "<dark_gray>/" + (int) c.maxVl()));
				}
				Double dupe = d.violations.get("dupe");
				if (dupe != null && dupe >= 1) {
					any = true;
					s.sendMessage(Text.mm("<dark_gray> » <aqua>Dupe <gray>x" + dupe.intValue()));
				}
				if (!any) s.sendMessage(Text.mm("<dark_gray> » <green>keine"));
			}
			case "xray" -> xray(s, args);
			case "dupe" -> dupe(s, args);
			default -> help(s);
		}
	}

	private void xray(CommandSender s, String[] args) {
		if (args.length == 1 || args[1].equalsIgnoreCase("gui")) {
			if (s instanceof Player p) plugin.xrayGui().open(p);
			else msg(s, "<red>Das GUI geht nur ingame – oder config.yml (xray.groups) bearbeiten.");
			return;
		}
		Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
		switch (args[1].toLowerCase()) {
			case "stats" -> {
				if (t == null) {
					msg(s, "<red>/atlas xray stats <Spieler>");
					return;
				}
				Map<String, Integer> stats = plugin.xray().stats(t.getUniqueId());
				msg(s, "<gray>Abbau von <white>" + t.getName() + "<gray> (jeweiliges Zeitfenster):");
				for (OreGroup g : plugin.xray().groups().values()) {
					int c = stats.getOrDefault(g.id, 0);
					String color = c >= g.maxBlocks ? "<red>" : c >= g.maxBlocks / 2 ? "<yellow>" : "<green>";
					s.sendMessage(Text.mm("<dark_gray> » <aqua>" + Text.escape(g.display) + " " + color + c + "<dark_gray>/" + g.maxBlocks
							+ " <gray>in " + g.windowMinutes + " Min."));
				}
				s.sendMessage(Text.mm("<dark_gray> » <gray>Stein (60 Min.): <white>" + stats.getOrDefault("__stone", 0)));
			}
			case "reset" -> {
				if (t == null) {
					msg(s, "<red>/atlas xray reset <Spieler>");
					return;
				}
				plugin.xray().reset(t.getUniqueId());
				msg(s, "<green>XRay-Statistik von " + t.getName() + " zurueckgesetzt.");
			}
			default -> msg(s, "<gray>/atlas xray <white>[gui|stats <Spieler>|reset <Spieler>]");
		}
	}

	private void dupe(CommandSender s, String[] args) {
		String sub = args.length > 1 ? args[1].toLowerCase() : "";
		switch (sub) {
			case "scan" -> {
				int found = plugin.dupe().scanAll();
				msg(s, found == 0 ? "<green>Scan fertig – keine Dupes gefunden." : "<red>Scan fertig – " + found + " Dupe(s) gefunden und entfernt.");
			}
			case "check" -> {
				if (!(s instanceof Player p)) {
					msg(s, "<red>Nur ingame.");
					return;
				}
				ItemStack hand = p.getInventory().getItemInMainHand();
				if (!plugin.dupe().eligible(hand)) {
					msg(s, "<gray>Dieses Item bekommt keine ID (stapelbar oder ignoriert).");
					return;
				}
				String id = plugin.dupe().readId(hand);
				msg(s, id == null ? "<yellow>Noch keine ID – wird beim naechsten Scan vergeben." : "<gray>Item-ID: <white>" + id);
			}
			default -> msg(s, "<gray>/atlas dupe <white>[scan|check]");
		}
	}

	private void help(CommandSender s) {
		msg(s, "<white>Atlas AntiCheat <gray>v" + plugin.getPluginMeta().getVersion());
		for (String line : List.of(
				"/atlas alerts <gray>– Alerts an/aus",
				"/atlas vl <Spieler> <gray>– Violations anzeigen",
				"/atlas xray <gray>– Anti-XRay GUI",
				"/atlas xray stats <Spieler> <gray>– Abbau-Statistik",
				"/atlas xray reset <Spieler> <gray>– Statistik zuruecksetzen",
				"/atlas dupe scan <gray>– Sofort-Scan nach Dupes",
				"/atlas dupe check <gray>– ID des Items in der Hand",
				"/atlas reload <gray>– Config neu laden")) {
			s.sendMessage(Text.mm("<dark_gray> » <aqua>" + line));
		}
	}

	@Override
	public Collection<String> suggest(CommandSourceStack source, String[] args) {
		List<String> out = new ArrayList<>();
		if (args.length <= 1) {
			out.addAll(List.of("alerts", "vl", "xray", "dupe", "reload"));
		} else if (args.length == 2) {
			switch (args[0].toLowerCase()) {
				case "xray" -> out.addAll(List.of("gui", "stats", "reset"));
				case "dupe" -> out.addAll(List.of("scan", "check"));
				case "vl", "violations" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
				default -> {
				}
			}
		} else if (args.length == 3 && args[0].equalsIgnoreCase("xray")) {
			Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
		}
		String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
		out.removeIf(o -> !o.toLowerCase().startsWith(last));
		return out;
	}
}
