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
import java.util.Set;

/** /atlas – Hauptbefehl. */
public final class AtlasCommand implements BasicCommand {
	private final AtlasAC plugin;
	private final PunishCommands punish;

	public AtlasCommand(AtlasAC plugin) {
		this.plugin = plugin;
		this.punish = new PunishCommands(plugin);
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
			case "freezecmd" -> freezeCommands(s, args);
			case "mods" -> mods(s, args);
			case "fly" -> fly(s, args);
			case "gui" -> {
				if (!(s instanceof Player p)) {
					msg(s, "<red>Nur ingame.");
					return;
				}
				Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
				if (t != null) plugin.adminGui().openPlayer(p, t.getUniqueId());
				else plugin.adminGui().openList(p, 0);
			}
			default -> {
				String sub = args[0].toLowerCase();
				if (PunishCommands.SUBS.contains(sub)) punish.execute(s, sub, args);
				else help(s);
			}
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
				s.sendMessage(Text.mm("<dark_gray> » " + "<gray>Gestein (60 Min.): <white>" + stats.getOrDefault("__stone", 0) + " Stein <dark_gray>| <white>"
						+ stats.getOrDefault("__deepslate", 0) + " Tiefenschiefer <dark_gray>| <white>" + stats.getOrDefault("__netherrack", 0) + " Netherrack"));
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

	/** /atlas freezecmd add|remove|list [Befehl] – erlaubte Befehle waehrend Freeze. */
	private void freezeCommands(CommandSender s, String[] args) {
		if (!s.hasPermission(AtlasAC.PERM_ADMIN)) {
			msg(s, "<red>Keine Rechte.");
			return;
		}
		List<String> allowed = new ArrayList<>(plugin.getConfig().getStringList("freeze.allowed-commands"));
		String action = args.length > 1 ? args[1].toLowerCase() : "list";
		if (action.equals("list")) {
			msg(s, "<gray>Waehrend Freeze erlaubt: " + (allowed.isEmpty() ? "<red>keine"
					: "<white>/" + String.join("<gray>, <white>/", allowed)));
			return;
		}
		if (args.length < 3 || !(action.equals("add") || action.equals("remove"))) {
			msg(s, "<gray>/atlas freezecmd <white>add|remove <Befehl> <gray>oder <white>/atlas freezecmd list");
			return;
		}
		String cmd = args[2].toLowerCase().replaceFirst("^/", "");
		int colon = cmd.indexOf(':');
		if (colon >= 0) cmd = cmd.substring(colon + 1);
		if (action.equals("add")) {
			if (allowed.contains(cmd)) {
				msg(s, "<yellow>/" + cmd + " ist schon erlaubt.");
				return;
			}
			allowed.add(cmd);
			msg(s, "<green>/" + cmd + " ist waehrend Freeze jetzt erlaubt.");
		} else {
			if (!allowed.remove(cmd)) {
				msg(s, "<yellow>/" + cmd + " war nicht erlaubt.");
				return;
			}
			msg(s, "<green>/" + cmd + " ist waehrend Freeze jetzt gesperrt.");
		}
		plugin.getConfig().set("freeze.allowed-commands", allowed);
		plugin.saveConfig();
	}

	/** /atlas mods check|info <Spieler> · /atlas mods test <Name> */
	private void mods(CommandSender s, String[] args) {
		if (!s.hasPermission(AtlasAC.PERM_ADMIN)) {
			msg(s, "<red>Keine Rechte.");
			return;
		}
		String sub = args.length > 1 ? args[1].toLowerCase() : "";
		if (sub.equals("test") && args.length > 2) {
			String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
			var r = plugin.mods().match(name);
			msg(s, r == null ? "<green>\"" + Text.escape(name) + "\" trifft keine Regel."
					: "<red>\"" + Text.escape(name) + "\" -> " + Text.escape(r.display()) + " <gray>(" + r.action()
					+ (r.action().contains("ban") ? " " + dev.atlasac.util.Durations.format(r.duration()) : "") + ")");
			return;
		}
		Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
		if (t == null || !(sub.equals("check") || sub.equals("info"))) {
			msg(s, "<gray>/atlas mods <white>check <Spieler> <gray>| <white>info <Spieler> <gray>| <white>test <Name>");
			return;
		}
		if (sub.equals("info")) {
			for (String line : plugin.mods().describe(t)) s.sendMessage(Text.mm(line));
		} else {
			plugin.mods().recheck(t);
			msg(s, "<gray>Mod-Pruefung fuer <white>" + t.getName() + " <gray>gestartet.");
		}
	}

	/** /atlas fly [Spieler] – Fliegen an/aus (Survival/Adventure). */
	private void fly(CommandSender s, String[] args) {
		Player t;
		if (args.length > 1) {
			if (!s.hasPermission("atlasac.fly.others")) {
				msg(s, "<red>Keine Rechte (atlasac.fly.others)");
				return;
			}
			t = Bukkit.getPlayerExact(args[1]);
			if (t == null) {
				msg(s, "<red>Spieler nicht online.");
				return;
			}
		} else if (s instanceof Player p) {
			if (!s.hasPermission("atlasac.fly")) {
				msg(s, "<red>Keine Rechte (atlasac.fly)");
				return;
			}
			t = p;
		} else {
			msg(s, "<red>/atlas fly <Spieler>");
			return;
		}
		boolean enable = !t.getAllowFlight();
		t.setAllowFlight(enable);
		if (!enable) {
			t.setFlying(false);
			t.setFallDistance(0);
		}
		String state = enable ? "<green>aktiviert" : "<red>deaktiviert";
		if (t.equals(s)) {
			msg(s, "<gray>Fliegen " + state + "<gray>.");
		} else {
			msg(s, "<gray>Fliegen fuer <white>" + t.getName() + " " + state + "<gray>.");
			msg(t, "<gray>Fliegen wurde " + state + "<gray>.");
		}
	}

	private void help(CommandSender s) {
		msg(s, "<white>Atlas AntiCheat <gray>v" + plugin.getPluginMeta().getVersion() + " <dark_gray>| <gray>Speicher: <white>" + plugin.punishments().storageName());
		for (String line : List.of(
				"/atlas alerts <gray>– Alerts an/aus",
				"/atlas vl <Spieler> <gray>– Violations anzeigen",
				"/atlas xray <gray>– Anti-XRay GUI",
				"/atlas xray stats <Spieler> <gray>– Abbau-Statistik",
				"/atlas xray reset <Spieler> <gray>– Statistik zuruecksetzen",
				"/atlas dupe scan <gray>– Sofort-Scan nach Dupes",
				"/atlas dupe check <gray>– ID des Items in der Hand",
				"/atlas gui [Spieler] <gray>– Admin-GUI",
				"/atlas fly [Spieler] <gray>– Fliegen an/aus",
				"/staff <gray>– Staff-Modus (Vanish + Werkzeuge) · <aqua>/staff vanish",
				"/atlas freeze <Spieler> <gray>– Einfrieren / Auftauen",
				"/atlas freezecmd add|remove|list <gray>– Erlaubte Befehle im Freeze",
				"/atlas mods check|info <Spieler> <gray>· <aqua>/atlas mods test <Name>",
				"/atlas kick <Spieler> [Grund]",
				"/atlas ban <Spieler> [Dauer] [Grund]",
				"/atlas tempban <Spieler> <Dauer> [Grund]",
				"/atlas ipban <Spieler|IP> [Dauer] [Grund]",
				"/atlas tempipban <Spieler|IP> <Dauer> [Grund]",
				"/atlas unban <Spieler> <gray>· <aqua>/atlas unbanip <Spieler|IP>",
				"/atlas mute|tempmute|unmute <Spieler> [Dauer] [Grund]",
				"/atlas history <Spieler> <gray>– Strafen-Historie",
				"/atlas reload <gray>– Config neu laden")) {
			s.sendMessage(Text.mm("<dark_gray> » <aqua>" + line));
		}
	}

	@Override
	public Collection<String> suggest(CommandSourceStack source, String[] args) {
		List<String> out = new ArrayList<>();
		if (args.length <= 1) {
			out.addAll(List.of("alerts", "vl", "xray", "dupe", "reload", "freezecmd", "gui", "mods", "fly"));
			out.addAll(PunishCommands.SUBS);
		} else if (args.length == 2) {
			switch (args[0].toLowerCase()) {
				case "xray" -> out.addAll(List.of("gui", "stats", "reset"));
				case "dupe" -> out.addAll(List.of("scan", "check"));
				case "freezecmd" -> out.addAll(List.of("add", "remove", "list"));
				case "mods" -> out.addAll(List.of("check", "info", "test"));
				case "vl", "violations", "gui", "fly" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
				default -> {
					if (PunishCommands.SUBS.contains(args[0].toLowerCase())) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
				}
			}
		} else if (args.length == 3 && args[0].equalsIgnoreCase("freezecmd") && args[1].equalsIgnoreCase("remove")) {
			out.addAll(plugin.getConfig().getStringList("freeze.allowed-commands"));
		} else if (args.length == 3 && args[0].equalsIgnoreCase("mods") && !args[1].equalsIgnoreCase("test")) {
			Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
		} else if (args.length == 3 && args[0].equalsIgnoreCase("xray")) {
			Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
		} else if (args.length == 3 && Set.of("tempban", "tempipban", "tempmute", "ban", "ipban", "mute").contains(args[0].toLowerCase())) {
			out.addAll(List.of("30m", "1h", "12h", "1d", "7d", "30d", "permanent"));
		}
		String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
		out.removeIf(o -> !o.toLowerCase().startsWith(last));
		return out;
	}
}
