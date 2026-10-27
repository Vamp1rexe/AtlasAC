package dev.atlasmodcheck;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Atlas ModCheck – nur Client- und Mod-Erkennung (ohne Anticheat). */
public final class AtlasModCheck extends JavaPlugin {
	public static final String PERM_ADMIN = "atlasmodcheck.admin";
	public static final String PERM_BYPASS = "atlasmodcheck.bypass";

	private Alerts alerts;
	private Punisher punisher;
	private ModDetector mods;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		getConfig().options().copyDefaults(true);
		saveConfig();

		perm(PERM_ADMIN, "Erlaubt /modcheck", PermissionDefault.OP);
		perm(Alerts.PERM_ALERTS, "Empfaengt ModCheck-Meldungen", PermissionDefault.OP);
		perm(PERM_BYPASS, "Wird nicht geprueft", PermissionDefault.FALSE);
		perm(ModDetector.PERM_BYPASS_MODS, "Darf mit verbotenen Mods joinen (wird trotzdem gemeldet)", PermissionDefault.FALSE);

		alerts = new Alerts(this);
		punisher = new Punisher(this);
		punisher.load();
		mods = new ModDetector(this);
		mods.reload();
		getServer().getPluginManager().registerEvents(punisher, this);
		getServer().getPluginManager().registerEvents(mods, this);

		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
				event.registrar().register("modcheck", "Atlas ModCheck", List.of("amc"), new ModCheckCommand(this)));
		getLogger().info("Atlas ModCheck aktiv – " + mods.rules().size() + " Mod-Regeln.");
	}

	public void reloadAll() {
		reloadConfig();
		getConfig().options().copyDefaults(true);
		saveConfig();
		mods.reload();
		punisher.load();
	}

	private static void perm(String name, String desc, PermissionDefault def) {
		if (Bukkit.getPluginManager().getPermission(name) == null) {
			Bukkit.getPluginManager().addPermission(new Permission(name, desc, def));
		}
	}

	public Alerts alerts() {
		return alerts;
	}

	public Punisher punisher() {
		return punisher;
	}

	public ModDetector mods() {
		return mods;
	}
}
