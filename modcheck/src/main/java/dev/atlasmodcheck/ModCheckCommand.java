package dev.atlasmodcheck;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** /modcheck info|check <Spieler> · test <Name> · unban <Spieler> · reload */
public final class ModCheckCommand implements BasicCommand {
	private final AtlasModCheck plugin;

	public ModCheckCommand(AtlasModCheck plugin) {
		this.plugin = plugin;
	}

	@Override
	public String permission() {
		return AtlasModCheck.PERM_ADMIN;
	}

	private void msg(CommandSender s, String t) {
		s.sendMessage(Text.mm(plugin.alerts().prefix() + t));
	}

	@Override
	public void execute(CommandSourceStack source, String[] args) {
		CommandSender s = source.getSender();
		String sub = args.length > 0 ? args[0].toLowerCase() : "";
		switch (sub) {
			case "info", "check" -> {
				Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
				if (t == null) {
					msg(s, "<red>/modcheck " + sub + " <Spieler>");
					return;
				}
				if (sub.equals("info")) {
					for (String line : plugin.mods().describe(t)) s.sendMessage(Text.mm(line));
				} else {
					plugin.mods().recheck(t);
					msg(s, "<gray>Pruefung fuer <white>" + t.getName() + " <gray>gestartet.");
				}
			}
			case "test" -> {
				if (args.length < 2) {
					msg(s, "<red>/modcheck test <Name>");
					return;
				}
				String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
				var r = plugin.mods().match(name);
				msg(s, r == null ? "<green>\"" + Text.escape(name) + "\" trifft keine Regel."
						: "<red>\"" + Text.escape(name) + "\" -> " + Text.escape(r.display()) + " <gray>(" + r.action()
						+ (r.action().contains("ban") ? " " + Durations.format(r.duration()) : "") + ")");
			}
			case "unban" -> {
				if (args.length < 2) {
					msg(s, "<red>/modcheck unban <Spieler>");
					return;
				}
				int n = plugin.punisher().unban(args[1]);
				msg(s, n > 0 ? "<green>" + args[1] + " entbannt." : "<yellow>Kein ModCheck-Ban fuer " + args[1] + ".");
			}
			case "reload" -> {
				plugin.reloadAll();
				msg(s, "<green>Neu geladen.");
			}
			default -> {
				msg(s, "<white>Atlas ModCheck <gray>v" + plugin.getPluginMeta().getVersion());
				for (String l : List.of("/modcheck info <Spieler> <gray>– Client, Loader, Mods",
						"/modcheck check <Spieler> <gray>– erneut pruefen",
						"/modcheck test <Name> <gray>– Namensregel testen",
						"/modcheck unban <Spieler>",
						"/modcheck reload")) {
					s.sendMessage(Text.mm("<dark_gray> » <aqua>" + l));
				}
			}
		}
	}

	@Override
	public Collection<String> suggest(CommandSourceStack source, String[] args) {
		List<String> out = new ArrayList<>();
		if (args.length <= 1) out.addAll(List.of("info", "check", "test", "unban", "reload"));
		else if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("check"))) {
			Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
		}
		String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
		out.removeIf(o -> !o.toLowerCase().startsWith(last));
		return out;
	}
}
