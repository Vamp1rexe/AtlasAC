package dev.atlasac.xray;

import org.bukkit.Material;

import java.util.Set;

/** Eine ueberwachte Erz-Gruppe (z.B. Diamant = Diamanterz + Tiefenschiefer-Diamanterz). */
public final class OreGroup {
	public final String id;
	public String display;
	public Material icon;
	public Set<Material> materials;
	public boolean enabled;
	public int maxBlocks;
	public int windowMinutes;

	public OreGroup(String id, String display, Material icon, Set<Material> materials, boolean enabled, int maxBlocks, int windowMinutes) {
		this.id = id;
		this.display = display;
		this.icon = icon;
		this.materials = materials;
		this.enabled = enabled;
		this.maxBlocks = maxBlocks;
		this.windowMinutes = windowMinutes;
	}
}
