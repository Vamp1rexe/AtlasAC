package dev.atlasac.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.atlasac.punish.Punishment;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** SQLite, MySQL, MariaDB oder PostgreSQL ueber HikariCP. */
public final class SqlStorage implements Storage {
	public enum Dialect {
		SQLITE("org.sqlite.JDBC"),
		MYSQL("com.mysql.cj.jdbc.Driver"),
		MARIADB("org.mariadb.jdbc.Driver"),
		POSTGRESQL("org.postgresql.Driver");

		final String driver;

		Dialect(String driver) {
			this.driver = driver;
		}
	}

	private final Dialect dialect;
	private final ConfigurationSection cfg;
	private final File dataFolder;
	private final String players;
	private final String punishments;
	private HikariDataSource ds;

	public SqlStorage(Dialect dialect, ConfigurationSection cfg, File dataFolder) {
		this.dialect = dialect;
		this.cfg = cfg;
		this.dataFolder = dataFolder;
		String prefix = cfg.getString("table-prefix", "atlas_").replaceAll("[^A-Za-z0-9_]", "");
		this.players = prefix + "players";
		this.punishments = prefix + "punishments";
	}

	@Override
	public String name() {
		return switch (dialect) {
			case SQLITE -> "SQLite (lokal, " + cfg.getString("sqlite-file", "atlas.db") + ")";
			case MYSQL -> "MySQL (" + cfg.getString("host") + ":" + cfg.getInt("port") + "/" + cfg.getString("database") + ")";
			case MARIADB -> "MariaDB (" + cfg.getString("host") + ":" + cfg.getInt("port") + "/" + cfg.getString("database") + ")";
			case POSTGRESQL -> "PostgreSQL (" + cfg.getString("host") + ":" + cfg.getInt("port") + "/" + cfg.getString("database") + ")";
		};
	}

	@Override
	public void open() throws Exception {
		Class.forName(dialect.driver);
		HikariConfig hc = new HikariConfig();
		hc.setPoolName("AtlasAC-" + dialect.name().toLowerCase(Locale.ROOT));
		hc.setDriverClassName(dialect.driver);
		String host = cfg.getString("host", "localhost");
		int port = cfg.getInt("port", dialect == Dialect.POSTGRESQL ? 5432 : 3306);
		String db = cfg.getString("database", "atlasac");
		switch (dialect) {
			case SQLITE -> {
				File f = new File(dataFolder, cfg.getString("sqlite-file", "atlas.db"));
				hc.setJdbcUrl("jdbc:sqlite:" + f.getAbsolutePath());
				hc.setMaximumPoolSize(1);
			}
			case MYSQL -> hc.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db);
			case MARIADB -> hc.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + db);
			case POSTGRESQL -> hc.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + db);
		}
		if (dialect != Dialect.SQLITE) {
			hc.setUsername(cfg.getString("username", "root"));
			hc.setPassword(cfg.getString("password", ""));
			hc.setMaximumPoolSize(Math.max(2, cfg.getInt("pool-size", 5)));
		}
		ConfigurationSection props = cfg.getConfigurationSection("properties");
		if (props != null) {
			for (String k : props.getKeys(false)) hc.addDataSourceProperty(k, String.valueOf(props.get(k)));
		}
		hc.setConnectionTimeout(10_000);
		ds = new HikariDataSource(hc);

		String text = dialect == Dialect.MYSQL || dialect == Dialect.MARIADB ? "TEXT" : "TEXT";
		try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
			st.executeUpdate("CREATE TABLE IF NOT EXISTS " + players + " ("
					+ "uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32), ip VARCHAR(64))");
			st.executeUpdate("CREATE TABLE IF NOT EXISTS " + punishments + " ("
					+ "id VARCHAR(16) NOT NULL PRIMARY KEY, type VARCHAR(8) NOT NULL, target VARCHAR(64) NOT NULL,"
					+ " name VARCHAR(32), reason " + text + ", staff VARCHAR(32), created BIGINT NOT NULL,"
					+ " expires BIGINT NOT NULL, active INT NOT NULL)");
			try {
				st.executeUpdate("CREATE INDEX " + punishments + "_target ON " + punishments + " (target)");
			} catch (Exception ignored) {
				// Index existiert bereits
			}
		}
	}

	@Override
	public void close() {
		if (ds != null) ds.close();
	}

	@Override
	public Snapshot load() throws Exception {
		List<PlayerRow> pl = new ArrayList<>();
		List<Punishment> pu = new ArrayList<>();
		try (Connection c = ds.getConnection()) {
			try (PreparedStatement ps = c.prepareStatement("SELECT uuid, name, ip FROM " + players); ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					try {
						pl.add(new PlayerRow(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3)));
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
			try (PreparedStatement ps = c.prepareStatement("SELECT id, type, target, name, reason, staff, created, expires, active FROM " + punishments);
				 ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Punishment p = read(rs);
					if (p != null) pu.add(p);
				}
			}
		}
		return new Snapshot(pl, pu);
	}

	private static Punishment read(ResultSet rs) throws Exception {
		try {
			return new Punishment(rs.getString(1), Punishment.Type.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4),
					rs.getString(5), rs.getString(6), rs.getLong(7), rs.getLong(8), rs.getInt(9) != 0);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private String upsert(String table, String key, String... columns) {
		StringBuilder cols = new StringBuilder(key), marks = new StringBuilder("?"), set = new StringBuilder();
		for (String col : columns) {
			cols.append(", ").append(col);
			marks.append(", ?");
			if (!set.isEmpty()) set.append(", ");
			set.append(switch (dialect) {
				case MYSQL, MARIADB -> col + " = VALUES(" + col + ")";
				default -> col + " = excluded." + col;
			});
		}
		String base = "INSERT INTO " + table + " (" + cols + ") VALUES (" + marks + ")";
		return switch (dialect) {
			case MYSQL, MARIADB -> base + " ON DUPLICATE KEY UPDATE " + set;
			default -> base + " ON CONFLICT (" + key + ") DO UPDATE SET " + set;
		};
	}

	@Override
	public void savePunishment(Punishment p) throws Exception {
		try (Connection c = ds.getConnection();
			 PreparedStatement ps = c.prepareStatement(upsert(punishments, "id", "type", "target", "name", "reason", "staff", "created", "expires", "active"))) {
			ps.setString(1, p.id());
			ps.setString(2, p.type().name());
			ps.setString(3, p.target());
			ps.setString(4, p.name());
			ps.setString(5, p.reason());
			ps.setString(6, p.staff());
			ps.setLong(7, p.created());
			ps.setLong(8, p.expires());
			ps.setInt(9, p.active() ? 1 : 0);
			ps.executeUpdate();
		}
	}

	@Override
	public void savePlayer(PlayerRow row) throws Exception {
		try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(upsert(players, "uuid", "name", "ip"))) {
			ps.setString(1, row.uuid().toString());
			ps.setString(2, row.name());
			ps.setString(3, row.ip());
			ps.executeUpdate();
		}
	}

	@Override
	public List<Punishment> loadFor(String uuid, String ip) throws Exception {
		List<Punishment> out = new ArrayList<>();
		try (Connection c = ds.getConnection();
			 PreparedStatement ps = c.prepareStatement("SELECT id, type, target, name, reason, staff, created, expires, active FROM "
					 + punishments + " WHERE target = ? OR target = ?")) {
			ps.setString(1, uuid);
			ps.setString(2, ip == null ? uuid : ip);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Punishment p = read(rs);
					if (p != null) out.add(p);
				}
			}
		}
		return out;
	}

	@Override
	public boolean shared() {
		return dialect != Dialect.SQLITE;
	}

	@Override
	public boolean isEmpty() throws Exception {
		try (Connection c = ds.getConnection(); Statement st = c.createStatement();
			 ResultSet rs = st.executeQuery("SELECT (SELECT COUNT(*) FROM " + punishments + ") + (SELECT COUNT(*) FROM " + players + ")")) {
			return !rs.next() || rs.getLong(1) == 0;
		}
	}
}
