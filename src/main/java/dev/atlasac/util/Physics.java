package dev.atlasac.util;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Kollisions- und Physik-Hilfen. Nur im Haupt-Thread benutzen. */
public final class Physics {
	public static final double GRAVITY = 0.08;
	public static final double AIR_DRAG = 0.98;
	public static final double AIR_FRICTION = 0.91;
	public static final double SPRINT_AIR_ACCEL = 0.026;
	public static final double JUMP_VELOCITY = 0.42;

	private Physics() {
	}

	/** Spieler-Hitbox an einer (Packet-)Position. */
	public static BoundingBox boxAt(Player player, double x, double y, double z) {
		BoundingBox current = player.getBoundingBox();
		double halfW = current.getWidthX() / 2.0;
		double h = current.getHeight();
		return new BoundingBox(x - halfW, y, z - halfW, x + halfW, y + h, z + halfW);
	}

	/** Steht die Hitbox auf etwas Kollidierbarem (Bloecke inkl. Teilbloecke, Boote, Shulker)? */
	public static boolean onGround(World world, BoundingBox box) {
		BoundingBox below = new BoundingBox(box.getMinX() + 1.0E-4, box.getMinY() - 0.03, box.getMinZ() + 1.0E-4,
				box.getMaxX() - 1.0E-4, box.getMinY() + 1.0E-4, box.getMaxZ() - 1.0E-4);
		return world.hasCollisionsIn(below);
	}

	/** Etwas direkt ueber dem Kopf (Head-Hitter)? */
	public static boolean ceiling(World world, BoundingBox box) {
		BoundingBox above = new BoundingBox(box.getMinX() + 1.0E-4, box.getMaxY() - 1.0E-4, box.getMinZ() + 1.0E-4,
				box.getMaxX() - 1.0E-4, box.getMaxY() + 0.35, box.getMaxZ() - 1.0E-4);
		return world.hasCollisionsIn(above);
	}

	/**
	 * "Komplexes" Terrain in der Naehe: Fluessigkeiten, Teilbloecke, Leitern, Spinnweben,
	 * Schleim/Honig, Puderschnee, Gerueste … – dort weicht die Vanilla-Physik von der
	 * einfachen Vorhersage ab, also wird nicht geprueft.
	 */
	public static boolean complexNearby(World world, BoundingBox box) {
		int minX = floor(box.getMinX() - 0.35), maxX = floor(box.getMaxX() + 0.35);
		int minY = floor(box.getMinY() - 0.65), maxY = floor(box.getMaxY() + 0.35);
		int minZ = floor(box.getMinZ() - 0.35), maxZ = floor(box.getMaxZ() + 0.35);
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					Block b = world.getBlockAt(x, y, z);
					Material m = b.getType();
					if (m.isAir()) continue;
					if (!m.isOccluding() || b.isLiquid()) return true;
				}
			}
		}
		return false;
	}

	/** Entitaeten, die schieben oder Kollision liefern koennen. */
	public static boolean entitiesNearby(Player player, BoundingBox box, double range) {
		for (Entity e : player.getWorld().getNearbyEntities(box.clone().expand(range))) {
			if (e.equals(player)) continue;
			if (e instanceof LivingEntity || e instanceof org.bukkit.entity.Vehicle) return true;
		}
		return false;
	}

	/** Kollidiert die Box mit Block-Kollisionsformen (ohne Entitaeten)? */
	public static boolean blockCollides(World world, BoundingBox box) {
		int minX = floor(box.getMinX()), maxX = floor(box.getMaxX());
		int minY = floor(box.getMinY()), maxY = floor(box.getMaxY());
		int minZ = floor(box.getMinZ()), maxZ = floor(box.getMaxZ());
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					Block b = world.getBlockAt(x, y, z);
					if (b.getType().isAir() || b.isPassable()) continue;
					BoundingBox local = box.clone().shift(-x, -y, -z);
					if (b.getCollisionShape().overlaps(local)) return true;
				}
			}
		}
		return false;
	}

	/** Fluessigkeit direkt unter den Fuessen, aber nichts, worauf man stehen koennte. */
	public static boolean overLiquidWithoutCollision(World world, BoundingBox box) {
		BoundingBox below = new BoundingBox(box.getMinX(), box.getMinY() - 0.1, box.getMinZ(), box.getMaxX(), box.getMinY(), box.getMaxZ());
		if (world.hasCollisionsIn(below)) return false;
		int y = floor(box.getMinY() - 0.1);
		for (int x = floor(box.getMinX()); x <= floor(box.getMaxX()); x++) {
			for (int z = floor(box.getMinZ()); z <= floor(box.getMaxZ()); z++) {
				if (world.getBlockAt(x, y, z).isLiquid()) return true;
			}
		}
		return false;
	}

	/** Anzahl schiebender Entitaeten direkt an der Hitbox (Vanilla-Push max. ~0.05 pro Entitaet und Tick). */
	public static int pushingEntities(Player player, BoundingBox box) {
		int n = 0;
		for (Entity e : player.getWorld().getNearbyEntities(box.clone().expand(0.3))) {
			if (e.equals(player)) continue;
			if (e instanceof LivingEntity || e instanceof org.bukkit.entity.Vehicle) n++;
		}
		return n;
	}

	/** Boote, Shulker, Minecarts – darauf kann man stehen, also echte Kollision. */
	public static boolean standableEntitiesNearby(Player player, BoundingBox box) {
		for (Entity e : player.getWorld().getNearbyEntities(box.clone().expand(1.0))) {
			if (e instanceof org.bukkit.entity.Vehicle || e instanceof org.bukkit.entity.Shulker) return true;
		}
		return false;
	}

	/** Abstand eines Punktes zu einer Box (0, wenn innen). */
	public static double distance(double px, double py, double pz, BoundingBox b) {
		double dx = Math.max(Math.max(b.getMinX() - px, 0), px - b.getMaxX());
		double dy = Math.max(Math.max(b.getMinY() - py, 0), py - b.getMaxY());
		double dz = Math.max(Math.max(b.getMinZ() - pz, 0), pz - b.getMaxZ());
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public static Vector direction(float yaw, float pitch) {
		double rotX = Math.toRadians(yaw);
		double rotY = Math.toRadians(pitch);
		double xz = Math.cos(rotY);
		return new Vector(-xz * Math.sin(rotX), -Math.sin(rotY), xz * Math.cos(rotX));
	}

	public static double wrapDegrees(double deg) {
		deg %= 360.0;
		if (deg >= 180.0) deg -= 360.0;
		if (deg < -180.0) deg += 360.0;
		return deg;
	}

	/** GCD mit Toleranz fuer Float-Ungenauigkeit (fuer Maus-Sensitivity-Analyse). */
	public static double gcd(double a, double b) {
		a = Math.abs(a);
		b = Math.abs(b);
		if (a < b) {
			double t = a;
			a = b;
			b = t;
		}
		int guard = 0;
		while (b > 0.001 && guard++ < 64) {
			double t = a - Math.floor(a / b) * b;
			a = b;
			b = t;
		}
		return a;
	}

	public static int floor(double v) {
		return (int) Math.floor(v);
	}
}
