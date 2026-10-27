package dev.atlasac.storage;

import dev.atlasac.punish.Punishment;

import java.util.List;
import java.util.UUID;

/** Speicher fuer Strafen und bekannte Spieler (Name + letzte IP). */
public interface Storage {
	record PlayerRow(UUID uuid, String name, String ip) {
	}

	record Snapshot(List<PlayerRow> players, List<Punishment> punishments) {
	}

	String name();

	void open() throws Exception;

	void close();

	Snapshot load() throws Exception;

	void savePunishment(Punishment p) throws Exception;

	void savePlayer(PlayerRow row) throws Exception;

	/** Alle Strafen fuer eine UUID oder IP (fuer den Login-Check direkt aus der Datenbank). */
	List<Punishment> loadFor(String uuid, String ip) throws Exception;

	/** true = mehrere Server koennen sich den Speicher teilen (Datenbank). */
	boolean shared();

	boolean isEmpty() throws Exception;
}
