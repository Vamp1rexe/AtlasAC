package dev.atlasac.check.combat;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.Check;
import dev.atlasac.data.PlayerData;
import dev.atlasac.util.Physics;
import dev.atlasac.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Block-Platzierung: Airplace/Fluessigkeit als Stuetze, Reichweite und ob der Blick den
 * angeklickten Block ueberhaupt trifft (Scaffold, Silent-Rotations, "Grim Place").
 */
public final class PlaceCheck extends Check {
	public PlaceCheck(AtlasAC plugin) {
		super(plugin, "place", "Place (Scaffold)");
	}

	/** @return true = Platzierung abbrechen */
	public boolean check(PlayerData data, Player p, Block against, Block placed) {
		if (!enabled()) return false;
		Material placedType = placed.getType();
		String key = placedType.getKey().getKey();
		if (against.getType().isAir()) {
			flag(data, "Platziert an Luft (Airplace)");
			return cancel();
		}
		if (against.isLiquid() && !key.equals("lily_pad") && !key.equals("frogspawn")) {
			flag(data, "Platziert an Fluessigkeit");
			return cancel();
		}

		Location eyeLoc = p.getEyeLocation();
		Vector eye = eyeLoc.toVector();
		BoundingBox cube = new BoundingBox(against.getX(), against.getY(), against.getZ(),
				against.getX() + 1, against.getY() + 1, against.getZ() + 1);
		double range = 4.5;
		AttributeInstance attr = p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
		if (attr != null) range = attr.getValue();
		double dist = Physics.distance(eye.getX(), eye.getY(), eye.getZ(), cube);
		if (dist > range + 0.3) {
			if (bufferUp(data.buffers, 1)) flag(data, "Reichweite " + Text.fmt(dist) + " (max " + Text.fmt(range) + ")");
			return cancel();
		}

		List<Vector> dirs = new ArrayList<>();
		dirs.add(Physics.direction(eyeLoc.getYaw(), eyeLoc.getPitch()));
		if (data.hasLastRotation) {
			dirs.add(Physics.direction(data.lastYaw, data.lastPitch));
			dirs.add(Physics.direction(data.lastYaw - data.lastDeltaYaw, data.lastPitch - data.lastDeltaPitch));
		}
		BoundingBox target = cube.clone().expand(0.1);
		for (Vector dir : dirs) {
			if (target.rayTrace(eye, dir, range + 1.0) != null) {
				bufferDown(data.buffers, 0.25);
				return false;
			}
		}
		if (bufferUp(data.buffers, 1)) flag(data, "Blick trifft den angeklickten Block nicht");
		return cancel();
	}
}
