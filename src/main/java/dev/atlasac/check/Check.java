package dev.atlasac.check;

import dev.atlasac.AtlasAC;
import dev.atlasac.data.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.Map;

/** Basis fuer alle Checks. Einstellungen liegen unter checks.&lt;id&gt; in der config.yml. */
public abstract class Check {
	protected final AtlasAC plugin;
	private final String id;
	private final String display;

	protected ConfigurationSection cfg = new MemoryConfiguration();
	private boolean enabled;
	private boolean punish;
	private boolean cancel;
	private double maxVl;
	private double bufferLimit;
	private double decay;

	protected Check(AtlasAC plugin, String id, String display) {
		this.plugin = plugin;
		this.id = id;
		this.display = display;
	}

	public final void reload() {
		ConfigurationSection s = plugin.getConfig().getConfigurationSection("checks." + id);
		cfg = s != null ? s : new MemoryConfiguration();
		enabled = cfg.getBoolean("enabled", true);
		punish = cfg.getBoolean("punish", true);
		cancel = cfg.getBoolean("cancel", false);
		maxVl = cfg.getDouble("max-vl", 10);
		bufferLimit = cfg.getDouble("buffer", 0);
		decay = cfg.getDouble("decay", 1);
		onReload();
	}

	protected void onReload() {
	}

	public String id() {
		return id;
	}

	public String display() {
		return display;
	}

	public boolean enabled() {
		return enabled;
	}

	public boolean punish() {
		return punish;
	}

	public boolean cancel() {
		return cancel;
	}

	public double maxVl() {
		return maxVl;
	}

	public double decay() {
		return decay;
	}

	/** Erhoeht den Buffer; true, wenn der Grenzwert ueberschritten ist (= flaggen). */
	protected boolean bufferUp(Map<String, Double> buffers, double amount) {
		double b = buffers.merge(id, amount, Double::sum);
		if (b > bufferLimit) {
			buffers.put(id, Math.min(b, bufferLimit + 2));
			return true;
		}
		return false;
	}

	protected void bufferDown(Map<String, Double> buffers, double amount) {
		buffers.computeIfPresent(id, (k, v) -> Math.max(0, v - amount));
	}

	/** Thread-sicher: wird bei Bedarf auf den Haupt-Thread verschoben. */
	protected void flag(PlayerData data, String info) {
		plugin.violations().flag(this, data, info);
	}
}
