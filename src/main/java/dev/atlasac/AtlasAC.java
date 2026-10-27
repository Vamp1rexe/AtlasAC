package dev.atlasac;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import dev.atlasac.check.CheckManager;
import dev.atlasac.command.AtlasCommand;
import dev.atlasac.core.AlertManager;
import dev.atlasac.core.AtlasListener;
import dev.atlasac.core.MovementProcessor;
import dev.atlasac.core.ViolationManager;
import dev.atlasac.data.DataManager;
import dev.atlasac.data.PlayerData;
import dev.atlasac.dupe.DupeManager;
import dev.atlasac.packet.AtlasPacketListener;
import dev.atlasac.punish.FreezeManager;
import dev.atlasac.punish.PunishmentManager;
import dev.atlasac.xray.XrayGui;
import dev.atlasac.xray.XrayManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public final class AtlasAC extends JavaPlugin {
	public static final String PERM_COMMAND = "atlasac.command";
	public static final String PERM_ADMIN = "atlasac.admin";
	public static final String PERM_BYPASS = "atlasac.bypass";

	private DataManager data;
	private CheckManager checks;
	private AlertManager alerts;
	private ViolationManager violations;
	private MovementProcessor movement;
	private XrayManager xray;
	private XrayGui xrayGui;
	private DupeManager dupe;
	private PunishmentManager punishments;
	private FreezeManager freeze;
	private PacketListenerCommon packetListener;
	private static AtlasAC instance;

	public static AtlasAC instance() {
		return instance;
	}
	private BukkitTask dupeTask;

	@Override
	public void onEnable() {
		instance = this;
		saveDefaultConfig();

		registerPermission(AlertManager.PERM_ALERTS, "Empfaengt Atlas-Alerts", PermissionDefault.OP);
		registerPermission(PERM_COMMAND, "Erlaubt /atlas", PermissionDefault.OP);
		registerPermission(PERM_ADMIN, "Erlaubt /atlas reload und Aenderungen im XRay-GUI", PermissionDefault.OP);
		registerPermission(PERM_BYPASS, "Wird von Atlas nicht geprueft", PermissionDefault.FALSE);
		for (String sub : List.of("ban", "tempban", "ipban", "tempipban", "unban", "unbanip", "kick", "mute", "tempmute", "unmute", "freeze", "history")) {
			registerPermission("atlasac." + sub, "Erlaubt /atlas " + sub, PermissionDefault.OP);
		}

		data = new DataManager();
		alerts = new AlertManager(this);
		violations = new ViolationManager(this);
		checks = new CheckManager(this);
		movement = new MovementProcessor(this);
		xray = new XrayManager(this);
		xray.reload();
		xrayGui = new XrayGui(this);
		dupe = new DupeManager(this);
		dupe.reload();
		punishments = new PunishmentManager(this);
		punishments.load();
		freeze = new FreezeManager(this);

		var pm = getServer().getPluginManager();
		pm.registerEvents(new AtlasListener(this), this);
		pm.registerEvents(xray, this);
		pm.registerEvents(xrayGui, this);
		pm.registerEvents(dupe, this);
		pm.registerEvents(punishments, this);
		pm.registerEvents(freeze, this);

		packetListener = PacketEvents.getAPI().getEventManager().registerListener(new AtlasPacketListener(this));

		// Spieler, die bei /reload schon online sind
		for (Player p : Bukkit.getOnlinePlayers()) {
			PlayerData d = data.create(p);
			d.bypass = p.hasPermission(PERM_BYPASS);
			d.exemptUntil = Bukkit.getCurrentTick() + 40;
		}

		Bukkit.getScheduler().runTaskTimer(this, () -> movement.tick(Bukkit.getCurrentTick()), 1L, 1L);
		Bukkit.getScheduler().runTaskTimer(this, () -> {
			for (PlayerData d : data.all()) d.bypass = d.player().hasPermission(PERM_BYPASS);
		}, 40L, 40L);
		long decay = Math.max(1, getConfig().getLong("violations.decay-interval-seconds", 60)) * 20L;
		Bukkit.getScheduler().runTaskTimer(this, violations::decay, decay, decay);
		scheduleDupeScan();

		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
				event.registrar().register("atlas", "Atlas AntiCheat", List.of("atlasac", "ac"), new AtlasCommand(this)));

		getLogger().info("Atlas AntiCheat aktiv – " + checks.all().size() + " Checks, Anti-XRay "
				+ (getConfig().getBoolean("xray.enabled") ? "an" : "aus") + ", Anti-Dupe "
				+ (getConfig().getBoolean("anti-dupe.enabled") ? "an" : "aus") + ".");
	}

	@Override
	public void onDisable() {
		if (packetListener != null) {
			PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
		}
		if (punishments != null) punishments.save();
	}

	private void scheduleDupeScan() {
		if (dupeTask != null) dupeTask.cancel();
		int interval = dupe.interval();
		dupeTask = Bukkit.getScheduler().runTaskTimer(this, dupe::scanAll, interval, interval);
	}

	public void reloadAll() {
		reloadConfig();
		checks.reload();
		xray.reload();
		dupe.reload();
		scheduleDupeScan();
	}

	private static void registerPermission(String name, String description, PermissionDefault def) {
		if (Bukkit.getPluginManager().getPermission(name) == null) {
			Bukkit.getPluginManager().addPermission(new Permission(name, description, def));
		}
	}

	public DataManager data() {
		return data;
	}

	public CheckManager checks() {
		return checks;
	}

	public AlertManager alerts() {
		return alerts;
	}

	public ViolationManager violations() {
		return violations;
	}

	public XrayManager xray() {
		return xray;
	}

	public XrayGui xrayGui() {
		return xrayGui;
	}

	public DupeManager dupe() {
		return dupe;
	}

	public PunishmentManager punishments() {
		return punishments;
	}

	public FreezeManager freeze() {
		return freeze;
	}
}
