package dev.atlasac.command;

import dev.atlasac.AtlasAC;
import dev.atlasac.punish.Punishment;
import dev.atlasac.punish.PunishmentManager;
import dev.atlasac.punish.PunishmentManager.Target;
import dev.atlasac.util.Durations;
import dev.atlasac.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** /atlas ban | tempban | ipban | tempipban | unban | unbanip | kick | mute | tempmute | unmute | freeze | unfreeze | history */
final class PunishCommands {
	static final Set<String> SUBS = Set.of("ban", "tempban", "ipban", "tempipban", "unban", "unbanip", "kick",
			"mute", "tempmute", "unmute", "freeze", "unfreeze", "history", "lookup");

	private final AtlasAC plugin;

	PunishCommands(AtlasAC plugin) {
		this.plugin = plugin;
	}

	private void msg(CommandSender s, String text) {
		s.sendMessage(Text.mm(plugin.alerts().prefix() + text));
	}

	private static String staff(CommandSender s) {
		return s instanceof Player p ? p.getName() : "Konsole";
	}

	private static String reason(String[] args, int from, String fallback) {
		if (args.length <= from) return fallback;
		return String.join(" ", Arrays.copyOfRange(args, from, args.length));
	}

	private boolean perm(CommandSender s, String sub) {
		String node = "atlasac." + (sub.equals("unfreeze") ? "freeze" : sub.equals("lookup") ? "history" : sub);
		if (s.hasPermission(node)) return true;
		msg(s, "<red>Keine Rechte <dark_gray>(" + node + ")");
		return false;
	}

	/** Schutz: Teammitglieder mit atlasac.bypass koennen nur von der Konsole bestraft werden. */
	private boolean protectedTarget(CommandSender s, Target t) {
		if (!(s instanceof Player) || t.uuid() == null) return false;
		Player online = Bukkit.getPlayer(t.uuid());
		if (online != null && online.hasPermission(AtlasAC.PERM_BYPASS) && !s.hasPermission(AtlasAC.PERM_ADMIN)) {
			msg(s, "<red>Dieser Spieler ist geschuetzt.");
			return true;
		}
		return false;
	}

	void execute(CommandSender s, String sub, String[] args) {
		if (!perm(s, sub)) return;
		if (args.length < 2) {
			msg(s, "<red>" + usage(sub));
			return;
		}
		PunishmentManager pm = plugin.punishments();
		Target t = pm.resolve(args[1]);
		if (t == null) {
			msg(s, "<red>Spieler <white>" + Text.escape(args[1]) + "<red> ist unbekannt.");
			return;
		}

		switch (sub) {
			case "ban", "tempban", "mute", "tempmute" -> {
				if (t.uuid() == null) {
					msg(s, "<red>Dafuer brauchst du einen Spielernamen, keine IP.");
					return;
				}
				if (protectedTarget(s, t)) return;
				long duration = -1;
				int reasonFrom = 2;
				boolean temp = sub.startsWith("temp");
				if (args.length > 2 && Durations.isDuration(args[2])) {
					duration = Durations.parse(args[2]);
					reasonFrom = 3;
				} else if (temp) {
					msg(s, "<red>" + usage(sub));
					return;
				}
				boolean mute = sub.endsWith("mute");
				String reason = reason(args, reasonFrom, mute ? "Chat-Regelverstoss" : "Regelverstoss");
				Punishment p = mute ? pm.mute(t, reason, staff(s), duration) : pm.ban(t, reason, staff(s), duration);
				msg(s, "<green>" + t.name() + (mute ? " gemutet" : " gebannt") + " <gray>(" + Durations.format(duration) + ", Fall <white>#" + p.id() + "<gray>)");
			}
			case "ipban", "tempipban" -> {
				if (t.ip() == null) {
					msg(s, "<red>Von <white>" + t.name() + "<red> ist keine IP bekannt (war noch nie online).");
					return;
				}
				if (protectedTarget(s, t)) return;
				long duration = -1;
				int reasonFrom = 2;
				if (args.length > 2 && Durations.isDuration(args[2])) {
					duration = Durations.parse(args[2]);
					reasonFrom = 3;
				} else if (sub.equals("tempipban")) {
					msg(s, "<red>" + usage(sub));
					return;
				}
				// Account zusaetzlich bannen, falls ein Spieler angegeben wurde
				String reason = reason(args, reasonFrom, "Regelverstoss");
				Punishment p = pm.ipBan(t, reason, staff(s), duration);
				if (t.uuid() != null) pm.ban(t, reason, staff(s), duration, false);
				msg(s, "<green>" + t.name() + " IP-gebannt <gray>(" + Durations.format(duration) + ", Fall <white>#" + p.id() + "<gray>)");
			}
			case "unban" -> {
				int n = t.uuid() == null ? 0 : pm.unban(t.uuid());
				if (t.ip() != null && args.length > 2 && args[2].equalsIgnoreCase("-ip")) n += pm.unbanIp(t.ip());
				msg(s, n > 0 ? "<green>" + t.name() + " entbannt." : "<yellow>" + t.name() + " war nicht gebannt.");
				if (n > 0) plugin.alerts().send("%prefix%<white>" + t.name() + " <gray>wurde von <white>" + staff(s) + " <gray>entbannt.");
			}
			case "unbanip" -> {
				int n = t.ip() == null ? 0 : pm.unbanIp(t.ip());
				if (t.uuid() != null) n += pm.unban(t.uuid());
				msg(s, n > 0 ? "<green>IP von " + t.name() + " entbannt." : "<yellow>Kein aktiver IP-Ban gefunden.");
			}
			case "unmute" -> {
				int n = t.uuid() == null ? 0 : pm.unmute(t.uuid());
				msg(s, n > 0 ? "<green>" + t.name() + " entmutet." : "<yellow>" + t.name() + " war nicht gemutet.");
				Player online = t.uuid() == null ? null : Bukkit.getPlayer(t.uuid());
				if (n > 0 && online != null) msg(online, "<green>Du bist nicht mehr gemutet.");
			}
			case "kick" -> {
				Player online = t.uuid() == null ? null : Bukkit.getPlayer(t.uuid());
				if (online == null) {
					msg(s, "<red>" + t.name() + " ist nicht online.");
					return;
				}
				if (protectedTarget(s, t)) return;
				Punishment p = pm.kick(online, reason(args, 2, "Vom Team gekickt"), staff(s), true);
				msg(s, "<green>" + t.name() + " gekickt <gray>(Fall <white>#" + p.id() + "<gray>)");
			}
			case "freeze", "unfreeze" -> {
				Player online = t.uuid() == null ? null : Bukkit.getPlayer(t.uuid());
				if (online == null) {
					msg(s, "<red>" + t.name() + " ist nicht online.");
					return;
				}
				boolean frozen = plugin.freeze().isFrozen(online.getUniqueId());
				if (sub.equals("unfreeze") || frozen) {
					plugin.freeze().unfreeze(online, staff(s));
					msg(s, "<green>" + online.getName() + " aufgetaut.");
				} else {
					if (protectedTarget(s, t)) return;
					plugin.freeze().freeze(online, staff(s));
					msg(s, "<aqua>" + online.getName() + " eingefroren. <gray>Nochmal /atlas freeze " + online.getName() + " zum Auftauen.");
				}
			}
			case "history", "lookup" -> history(s, t);
			default -> msg(s, "<red>Unbekannt.");
		}
	}

	private void history(CommandSender s, Target t) {
		List<Punishment> list = plugin.punishments().history(t);
		long now = System.currentTimeMillis();
		msg(s, "<gray>Historie von <white>" + t.name() + (t.ip() != null && s.hasPermission("atlasac.ipban") ? " <dark_gray>(" + t.ip() + ")" : "")
				+ "<gray>: <white>" + list.size() + " Eintraege");
		Player online = t.uuid() == null ? null : Bukkit.getPlayer(t.uuid());
		if (online != null && plugin.freeze().isFrozen(online.getUniqueId())) s.sendMessage(Text.mm("<dark_gray> » <aqua>Aktuell eingefroren"));
		int shown = 0;
		for (Punishment p : list) {
			if (shown++ >= 10) break;
			String state = p.type() == Punishment.Type.KICK ? "<gray>" : p.inForce(now) ? "<red>aktiv " : "<dark_gray>abgelaufen ";
			String length = p.type() == Punishment.Type.KICK ? "" : " <gray>" + (p.permanent() ? "permanent" : Durations.format(p.expires() - p.created()));
			s.sendMessage(Text.mm("<dark_gray> » <white>#" + p.id() + " " + state + "<aqua>" + p.type().name() + length
					+ " <gray>von <white>" + Text.escape(p.staff()) + " <dark_gray>(" + Durations.date(p.created()) + ")"
					+ "<newline><dark_gray>     <gray>" + Text.escape(p.reason())));
		}
	}

	static String usage(String sub) {
		return switch (sub) {
			case "ban" -> "/atlas ban <Spieler> [Dauer] [Grund]";
			case "tempban" -> "/atlas tempban <Spieler> <Dauer> [Grund]  (z.B. 30m, 12h, 7d, 1mo)";
			case "ipban" -> "/atlas ipban <Spieler|IP> [Dauer] [Grund]";
			case "tempipban" -> "/atlas tempipban <Spieler|IP> <Dauer> [Grund]";
			case "unban" -> "/atlas unban <Spieler> [-ip]";
			case "unbanip" -> "/atlas unbanip <Spieler|IP>";
			case "kick" -> "/atlas kick <Spieler> [Grund]";
			case "mute" -> "/atlas mute <Spieler> [Dauer] [Grund]";
			case "tempmute" -> "/atlas tempmute <Spieler> <Dauer> [Grund]";
			case "unmute" -> "/atlas unmute <Spieler>";
			case "freeze", "unfreeze" -> "/atlas freeze <Spieler>";
			default -> "/atlas history <Spieler>";
		};
	}
}
