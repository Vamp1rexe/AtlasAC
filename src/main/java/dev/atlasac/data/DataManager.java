package dev.atlasac.data;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DataManager {
	private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

	public PlayerData create(Player player) {
		PlayerData d = new PlayerData(player);
		data.put(player.getUniqueId(), d);
		return d;
	}

	public PlayerData get(UUID uuid) {
		return uuid == null ? null : data.get(uuid);
	}

	public PlayerData get(Player player) {
		return data.get(player.getUniqueId());
	}

	public void remove(UUID uuid) {
		data.remove(uuid);
	}

	public Collection<PlayerData> all() {
		return data.values();
	}
}
