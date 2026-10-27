package dev.atlasac.data;

import org.bukkit.util.BoundingBox;

/**
 * Ringpuffer der letzten Server-Ticks: Hitbox und Augenposition eines Spielers.
 * Dient als Lag-Compensation fuer Reach/Hitbox (wie Grim, nur Tick- statt Transaction-basiert).
 */
public final class PositionHistory {
	public static final int SIZE = 40;

	private final int[] ticks = new int[SIZE];
	private final BoundingBox[] boxes = new BoundingBox[SIZE];
	private final double[] eyeX = new double[SIZE];
	private final double[] eyeY = new double[SIZE];
	private final double[] eyeZ = new double[SIZE];
	private int size;

	public void record(int tick, BoundingBox box, double ex, double ey, double ez) {
		int i = Math.floorMod(tick, SIZE);
		ticks[i] = tick;
		boxes[i] = box.clone();
		eyeX[i] = ex;
		eyeY[i] = ey;
		eyeZ[i] = ez;
		if (size < SIZE) size++;
	}

	/** Hitbox zum Tick oder null, falls nicht (mehr) vorhanden. */
	public BoundingBox box(int tick) {
		int i = Math.floorMod(tick, SIZE);
		return size > 0 && ticks[i] == tick && boxes[i] != null ? boxes[i] : null;
	}

	public double[] eye(int tick) {
		int i = Math.floorMod(tick, SIZE);
		if (size == 0 || ticks[i] != tick || boxes[i] == null) return null;
		return new double[]{eyeX[i], eyeY[i], eyeZ[i]};
	}
}
