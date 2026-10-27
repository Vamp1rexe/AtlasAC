package dev.atlasac.gui;

import com.github.retrooper.packetevents.PacketEvents;
import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.punish.Punishment;
import dev.atlasac.punish.PunishmentManager;
import dev.atlasac.punish.PunishmentManager.Target;
import dev.atlasac.util.Durations;
import dev.atlasac.util.Text;
import dev.atlasac.xray.OreGroup;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /atlas gui – alle Spieler, Spieler-Details, Flags, Historie, Strafen ausstellen. */
public final class AdminGui {
	public static final String PERM_GUI = "atlasac.gui";
	private static final String TITLE = "<gradient:#7b5cff:#00d4ff><bold>Atlas</bold></gradient> <dark_gray>» <gray>";

	private final AtlasAC plugin;

	public AdminGui(AtlasAC plugin) {
		this.plugin = plugin;
	}

	private void msg(Player p, String text) {
		p.sendMessage(Text.mm(plugin.alerts().prefix() + text));
	}

	private static int totalVl(PlayerData d) {
		if (d == null) return 0;
		double sum = 0;
		for (double v : d.violations.values()) sum += v;
		return (int) sum;
	}

	private String brand(Player p) {
		String b = p.getClientBrandName();
		return b == null ? "unbekannt" : Text.escape(b);
	}

	private String version(Player p) {
		try {
			return PacketEvents.getAPI().getPlayerManager().getClientVersion(p).getReleaseName();
		} catch (Throwable t) {
			return "?";
		}
	}

	private ItemStack head(Player target, String name, List<String> lore) {
		ItemStack it = Menu.item(Material.PLAYER_HEAD, name, lore);
		if (it.getItemMeta() instanceof SkullMeta meta) {
			meta.setOwningPlayer(target);
			it.setItemMeta(meta);
		}
		return it;
	}

	// ───────────────────────── Spielerliste ─────────────────────────

	public void openList(Player viewer, int page) {
		if (!viewer.hasPermission(PERM_GUI)) {
			msg(viewer, "<red>Keine Rechte (" + PERM_GUI + ")");
			return;
		}
		List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
		players.sort(Comparator.comparingInt((Player p) -> -totalVl(plugin.data().get(p))).thenComparing(Player::getName));
		int perPage = 45;
		int pages = Math.max(1, (players.size() + perPage - 1) / perPage);
		int pg = Math.max(0, Math.min(page, pages - 1));
		Menu m = new Menu(6, TITLE + "Spieler <dark_gray>(" + (pg + 1) + "/" + pages + ")");
		for (int i = 0; i < perPage && pg * perPage + i < players.size(); i++) {
			Player t = players.get(pg * perPage + i);
			PlayerData d = plugin.data().get(t);
			int vl = totalVl(d);
			List<String> lore = new ArrayList<>();
			lore.add("<gray>Ping: <white>" + t.getPing() + "ms");
			lore.add("<gray>Client: <white>" + brand(t) + " <dark_gray>(" + version(t) + ")");
			lore.add("<gray>Flags: " + (vl == 0 ? "<green>0" : vl < 10 ? "<yellow>" + vl : "<red>" + vl));
			if (plugin.freeze().isFrozen(t.getUniqueId())) lore.add("<aqua>❄ Eingefroren");
			if (plugin.staffMode().inStaffMode(t)) lore.add("<light_purple>Staff-Modus");
			if (plugin.staffMode().isVanished(t)) lore.add("<dark_gray>Vanish");
			lore.add("");
			lore.add("<yellow>Klicken fuer Details");
			m.set(i, head(t, "<white><bold>" + t.getName(), lore), e -> openPlayer(viewer, t.getUniqueId()));
		}
		if (pg > 0) m.set(45, Menu.item(Material.ARROW, "<yellow>« Vorherige Seite"), e -> openList(viewer, pg - 1));
		m.set(49, Menu.item(Material.BARRIER, "<red>Schliessen"), e -> viewer.closeInventory());
		if (pg < pages - 1) m.set(53, Menu.item(Material.ARROW, "<yellow>Naechste Seite »"), e -> openList(viewer, pg + 1));
		m.set(47, Menu.item(Material.CLOCK, "<aqua>Aktualisieren"), e -> openList(viewer, pg));
		m.fill(Material.BLACK_STAINED_GLASS_PANE).open(viewer);
	}

	// ───────────────────────── Spieler-Details ─────────────────────────

	public void openPlayer(Player viewer, UUID id) {
		if (!viewer.hasPermission(PERM_GUI)) {
			msg(viewer, "<red>Keine Rechte (" + PERM_GUI + ")");
			return;
		}
		Player t = Bukkit.getPlayer(id);
		if (t == null) {
			msg(viewer, "<red>Spieler ist nicht mehr online.");
			return;
		}
		PlayerData d = plugin.data().get(t);
		Location l = t.getLocation();
		Menu m = new Menu(5, TITLE + t.getName());

		List<String> info = new ArrayList<>();
		info.add("<gray>UUID: <white>" + t.getUniqueId());
		info.add("<gray>Ping: <white>" + t.getPing() + "ms");
		info.add("<gray>Client-Brand: <white>" + brand(t));
		info.add("<gray>Version: <white>" + version(t));
		info.add("<gray>Spielmodus: <white>" + t.getGameMode().name().toLowerCase());
		info.add("<gray>Welt: <white>" + l.getWorld().getName() + " <dark_gray>" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
		info.add("<gray>Leben: <white>" + Text.fmt1(t.getHealth()) + " <gray>Hunger: <white>" + t.getFoodLevel());
		if (viewer.hasPermission("atlasac.ipban")) info.add("<gray>IP: <white>" + PunishmentManager.ipOf(t));
		info.add("<gray>Flags gesamt: <white>" + totalVl(d));
		m.set(4, head(t, "<white><bold>" + t.getName(), info));

		m.set(19, Menu.item(Material.ENDER_PEARL, "<green><bold>Teleportieren", "<gray>Zum Spieler teleportieren"), e -> {
			viewer.closeInventory();
			viewer.teleport(t.getLocation());
		});
		boolean frozen = plugin.freeze().isFrozen(t.getUniqueId());
		m.set(20, Menu.item(Material.PACKED_ICE, frozen ? "<aqua><bold>Auftauen" : "<aqua><bold>Einfrieren",
				frozen ? "<gray>Spieler ist eingefroren" : "<gray>Spieler festsetzen"), e -> {
			if (!viewer.hasPermission("atlasac.freeze")) {
				msg(viewer, "<red>Keine Rechte (atlasac.freeze)");
				return;
			}
			if (plugin.freeze().isFrozen(t.getUniqueId())) plugin.freeze().unfreeze(t, viewer.getName());
			else plugin.freeze().freeze(t, viewer.getName());
			openPlayer(viewer, id);
		});
		m.set(21, Menu.item(Material.CHEST, "<gold><bold>Inventar", "<gray>Inventar ansehen"),
				e -> plugin.staffMode().openInventory(viewer, t));
		m.set(22, Menu.item(Material.ENDER_CHEST, "<light_purple><bold>Enderkiste", "<gray>Enderkiste ansehen"),
				e -> plugin.staffMode().openEnderChest(viewer, t));
		m.set(23, Menu.item(Material.PAPER, "<yellow><bold>Flags", "<gray>Anticheat-Violations", "<gray>und XRay-Statistik"),
				e -> openFlags(viewer, id));
		m.set(24, Menu.item(Material.BOOK, "<white><bold>Historie", "<gray>Alle Strafen ansehen"), e -> openHistory(viewer, id));
		m.set(25, Menu.item(Material.IRON_AXE, "<red><bold>Bestrafen", "<gray>Kick, Mute, Ban, IP-Ban"), e -> openPunish(viewer, id));

		m.set(40, Menu.item(Material.ARROW, "<yellow>« Zurueck"), e -> openList(viewer, 0));
		m.fill(Material.BLACK_STAINED_GLASS_PANE).open(viewer);
	}

	// ───────────────────────── Flags ─────────────────────────

	public void openFlags(Player viewer, UUID id) {
		Player t = Bukkit.getPlayer(id);
		if (t == null) {
			msg(viewer, "<red>Spieler ist nicht mehr online.");
			return;
		}
		PlayerData d = plugin.data().get(t);
		Menu m = new Menu(6, TITLE + "Flags von " + t.getName());
		int slot = 0;
		for (Check c : plugin.checks().all()) {
			double vl = d == null ? 0 : d.violations.getOrDefault(c.id(), 0.0);
			if (slot >= 36) break;
			Material icon = vl <= 0 ? Material.LIME_STAINED_GLASS_PANE : vl < c.maxVl() / 2 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
			m.set(slot++, Menu.item(icon, "<white>" + c.display(),
					"<gray>VL: <white>" + (int) vl + "<dark_gray>/" + (int) c.maxVl(),
					"<gray>Strafe: " + (c.punish() ? "<red>Kick" : "<yellow>nur Alert"),
					c.enabled() ? "<green>aktiv" : "<dark_gray>deaktiviert"));
		}
		double dupe = d == null ? 0 : d.violations.getOrDefault("dupe", 0.0);
		m.set(slot, Menu.item(dupe > 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE, "<white>Dupe", "<gray>VL: <white>" + (int) dupe));

		Map<String, Integer> stats = plugin.xray().stats(id);
		List<String> xray = new ArrayList<>();
		for (OreGroup g : plugin.xray().groups().values()) {
			int c = stats.getOrDefault(g.id, 0);
			String color = c >= g.maxBlocks ? "<red>" : c >= g.maxBlocks / 2 ? "<yellow>" : "<green>";
			xray.add("<gray>" + Text.escape(g.display) + ": " + color + c + "<dark_gray>/" + g.maxBlocks + " (" + g.windowMinutes + " Min.)");
		}
		xray.add("<gray>Stein (60 Min.): <white>" + stats.getOrDefault("__stone", 0));
		m.set(40, Menu.item(Material.DIAMOND_ORE, "<aqua><bold>XRay-Statistik", xray));
		m.set(49, Menu.item(Material.ARROW, "<yellow>« Zurueck"), e -> openPlayer(viewer, id));
		m.fill(Material.BLACK_STAINED_GLASS_PANE).open(viewer);
	}

	// ───────────────────────── Historie ─────────────────────────

	public void openHistory(Player viewer, UUID id) {
		if (!viewer.hasPermission("atlasac.history")) {
			msg(viewer, "<red>Keine Rechte (atlasac.history)");
			return;
		}
		Player online = Bukkit.getPlayer(id);
		Target t = online != null ? new Target(id, online.getName(), PunishmentManager.ipOf(online))
				: plugin.punishments().resolve(id.toString());
		if (t == null) return;
		List<Punishment> list = plugin.punishments().history(t);
		long now = System.currentTimeMillis();
		Menu m = new Menu(6, TITLE + "Historie von " + t.name());
		int slot = 0;
		for (Punishment p : list) {
			if (slot >= 45) break;
			boolean inForce = p.inForce(now);
			Material icon = switch (p.type()) {
				case BAN -> inForce ? Material.RED_CONCRETE : Material.GRAY_CONCRETE;
				case IPBAN -> inForce ? Material.CRIMSON_HYPHAE : Material.GRAY_CONCRETE;
				case MUTE -> inForce ? Material.ORANGE_CONCRETE : Material.GRAY_CONCRETE;
				case KICK -> Material.LEATHER_BOOTS;
			};
			List<String> lore = new ArrayList<>();
			lore.add("<gray>Grund: <white>" + Text.escape(p.reason()));
			lore.add("<gray>Von: <white>" + Text.escape(p.staff()));
			lore.add("<gray>Datum: <white>" + Durations.date(p.created()));
			if (p.type() != Punishment.Type.KICK) {
				lore.add("<gray>Dauer: <white>" + (p.permanent() ? "Permanent" : Durations.format(p.expires() - p.created())));
				lore.add("<gray>Status: " + (inForce ? "<red>aktiv" : p.active() ? "<dark_gray>abgelaufen" : "<dark_gray>aufgehoben"));
				if (inForce) {
					lore.add("");
					lore.add("<yellow>Shift + Rechtsklick <gray>zum Aufheben");
				}
			}
			m.set(slot++, Menu.item(icon, "<white>#" + p.id() + " <aqua>" + p.type().name(), lore), e -> {
				if (!inForce || e.getClick() != ClickType.SHIFT_RIGHT) return;
				String perm = switch (p.type()) {
					case BAN -> "atlasac.unban";
					case IPBAN -> "atlasac.unbanip";
					default -> "atlasac.unmute";
				};
				if (!viewer.hasPermission(perm)) {
					msg(viewer, "<red>Keine Rechte (" + perm + ")");
					return;
				}
				switch (p.type()) {
					case BAN -> plugin.punishments().unban(id);
					case IPBAN -> plugin.punishments().unbanIp(p.target());
					default -> plugin.punishments().unmute(id);
				}
				plugin.alerts().send("%prefix%<white>#" + p.id() + " <gray>(" + p.type().name() + " von " + t.name() + ") wurde von <white>"
						+ viewer.getName() + " <gray>aufgehoben.");
				openHistory(viewer, id);
			});
		}
		if (list.isEmpty()) m.set(22, Menu.item(Material.LIME_DYE, "<green>Keine Strafen", "<gray>Weisse Weste."));
		m.set(49, Menu.item(Material.ARROW, "<yellow>« Zurueck"), e -> openPlayer(viewer, id));
		m.fill(Material.BLACK_STAINED_GLASS_PANE).open(viewer);
	}

	// ───────────────────────── Bestrafen ─────────────────────────

	public void openPunish(Player viewer, UUID id) {
		Player t = Bukkit.getPlayer(id);
		if (t == null) {
			msg(viewer, "<red>Spieler ist nicht mehr online.");
			return;
		}
		Menu m = new Menu(6, TITLE + t.getName() + " bestrafen");
		List<Map<?, ?>> presets = plugin.getConfig().getMapList("staff-gui.presets");
		int slot = 10;
		for (Map<?, ?> preset : presets) {
			if (slot % 9 == 8) slot += 2;
			if (slot >= 44) break;
			String name = String.valueOf(preset.getOrDefault("name", "Strafe"));
			String type = String.valueOf(preset.getOrDefault("type", "ban")).toLowerCase();
			String duration = String.valueOf(preset.getOrDefault("duration", "permanent"));
			String reason = String.valueOf(preset.getOrDefault("reason", "Regelverstoss"));
			Material icon = Material.matchMaterial(String.valueOf(preset.getOrDefault("icon", "PAPER")));
			if (icon == null || !icon.isItem()) icon = Material.PAPER;
			long ms = type.equals("kick") ? 0 : Durations.parse(duration);
			if (!type.equals("kick") && ms == 0) {
				plugin.getLogger().warning("staff-gui.presets: ungueltige Dauer '" + duration + "' bei " + name);
				continue;
			}
			List<String> lore = new ArrayList<>();
			lore.add("<gray>Art: <white>" + type);
			if (!type.equals("kick")) lore.add("<gray>Dauer: <white>" + Durations.format(ms));
			lore.add("<gray>Grund: <white>" + Text.escape(reason));
			lore.add("");
			lore.add("<yellow>Klicken zum Ausfuehren");
			final long duration0 = ms;
			m.set(slot++, Menu.item(icon, "<red><bold>" + Text.escape(name), lore), e -> apply(viewer, t, type, duration0, reason));
		}
		m.set(49, Menu.item(Material.ARROW, "<yellow>« Zurueck"), e -> openPlayer(viewer, id));
		m.fill(Material.BLACK_STAINED_GLASS_PANE).open(viewer);
	}

	private void apply(Player viewer, Player t, String type, long duration, String reason) {
		String perm = switch (type) {
			case "kick" -> "atlasac.kick";
			case "mute" -> duration < 0 ? "atlasac.mute" : "atlasac.tempmute";
			case "ipban" -> duration < 0 ? "atlasac.ipban" : "atlasac.tempipban";
			default -> duration < 0 ? "atlasac.ban" : "atlasac.tempban";
		};
		if (!viewer.hasPermission(perm)) {
			msg(viewer, "<red>Keine Rechte (" + perm + ")");
			return;
		}
		if (!t.isOnline()) {
			msg(viewer, "<red>Spieler ist nicht mehr online.");
			return;
		}
		if (t.hasPermission(AtlasAC.PERM_BYPASS) && !viewer.hasPermission(AtlasAC.PERM_ADMIN)) {
			msg(viewer, "<red>Dieser Spieler ist geschuetzt.");
			return;
		}
		viewer.closeInventory();
		PunishmentManager pm = plugin.punishments();
		Target target = new Target(t.getUniqueId(), t.getName(), PunishmentManager.ipOf(t));
		Punishment p = switch (type) {
			case "kick" -> pm.kick(t, reason, viewer.getName(), true);
			case "mute" -> pm.mute(target, reason, viewer.getName(), duration);
			case "ipban" -> {
				Punishment ip = pm.ipBan(target, reason, viewer.getName(), duration);
				pm.ban(target, reason, viewer.getName(), duration, false);
				yield ip;
			}
			default -> pm.ban(target, reason, viewer.getName(), duration);
		};
		msg(viewer, "<green>Erledigt <gray>(Fall <white>#" + p.id() + "<gray>)");
	}
}
