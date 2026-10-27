package dev.atlasac.storage;

import dev.atlasac.punish.Punishment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lokale Speicherung in plugins/AtlasAC/data.yml (Standard). */
public final class YamlStorage implements Storage {
	private final File file;
	private final Map<String, Punishment> punishments = new LinkedHashMap<>();
	private final Map<UUID, PlayerRow> players = new LinkedHashMap<>();

	public YamlStorage(File file) {
		this.file = file;
	}

	@Override
	public String name() {
		return "YAML (lokal, " + file.getName() + ")";
	}

	@Override
	public void open() {
	}

	@Override
	public void close() {
	}

	@Override
	public synchronized Snapshot load() {
		punishments.clear();
		players.clear();
		YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection ps = y.getConfigurationSection("players");
		if (ps != null) {
			for (String key : ps.getKeys(false)) {
				try {
					UUID uuid = UUID.fromString(key);
					players.put(uuid, new PlayerRow(uuid, ps.getString(key + ".name"), ps.getString(key + ".ip")));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		ConfigurationSection pu = y.getConfigurationSection("punishments");
		if (pu != null) {
			for (String id : pu.getKeys(false)) {
				ConfigurationSection s = pu.getConfigurationSection(id);
				if (s == null) continue;
				try {
					punishments.put(id, new Punishment(id, Punishment.Type.valueOf(s.getString("type", "BAN")), s.getString("target", ""),
							s.getString("name", "?"), s.getString("reason", ""), s.getString("staff", "?"),
							s.getLong("created"), s.getLong("expires", -1), s.getBoolean("active", true)));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		return new Snapshot(new ArrayList<>(players.values()), new ArrayList<>(punishments.values()));
	}

	@Override
	public synchronized void savePunishment(Punishment p) throws Exception {
		punishments.put(p.id(), p);
		write();
	}

	@Override
	public synchronized void savePlayer(PlayerRow row) throws Exception {
		players.put(row.uuid(), row);
		write();
	}

	private void write() throws Exception {
		YamlConfiguration y = new YamlConfiguration();
		for (PlayerRow r : players.values()) {
			if (r.name() != null) y.set("players." + r.uuid() + ".name", r.name());
			if (r.ip() != null) y.set("players." + r.uuid() + ".ip", r.ip());
		}
		for (Punishment p : punishments.values()) {
			String b = "punishments." + p.id() + ".";
			y.set(b + "type", p.type().name());
			y.set(b + "target", p.target());
			y.set(b + "name", p.name());
			y.set(b + "reason", p.reason());
			y.set(b + "staff", p.staff());
			y.set(b + "created", p.created());
			y.set(b + "expires", p.expires());
			y.set(b + "active", p.active());
		}
		File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
		y.save(tmp);
		java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	@Override
	public synchronized List<Punishment> loadFor(String uuid, String ip) {
		List<Punishment> out = new ArrayList<>();
		for (Punishment p : punishments.values()) {
			if (p.target().equals(uuid) || (ip != null && p.target().equals(ip))) out.add(p);
		}
		return out;
	}

	@Override
	public boolean shared() {
		return false;
	}

	@Override
	public synchronized boolean isEmpty() {
		return punishments.isEmpty() && players.isEmpty();
	}

	public boolean exists() {
		return file.exists();
	}
}
