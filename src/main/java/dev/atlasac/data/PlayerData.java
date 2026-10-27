package dev.atlasac.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Zustand pro Spieler.
 * Felder unter "Netty" werden nur auf dem Netty-Thread des Spielers benutzt,
 * Felder unter "Main" nur im Server-Haupt-Thread. Die Queue verbindet beide.
 */
public final class PlayerData {
	private final UUID uuid;
	private final Player player;

	/** Bypass-Recht (atlasac.bypass), wird regelmaessig im Haupt-Thread aktualisiert. */
	public volatile boolean bypass;
	/** Bis zu diesem Server-Tick sind Movement-Checks ausgesetzt (Teleport, Join, Respawn ...). */
	public volatile int exemptUntil;
	/** Wahr, sobald der Client Tick-End-Packets schickt (1.21.2+ Clients ohne ViaVersion-Downgrade). */
	public volatile boolean tickEndSupported;

	public final ConcurrentLinkedQueue<MoveSample> moves = new ConcurrentLinkedQueue<>();
	public final Map<String, Double> violations = new ConcurrentHashMap<>();
	public final Map<String, Long> lastAlert = new ConcurrentHashMap<>();

	// ───── Netty ─────
	public long clientTick;
	public long lastTickEndNanos;
	public long timerBalanceNanos;
	public final ArrayDeque<Long> clickTimes = new ArrayDeque<>();
	public final ArrayDeque<Double> clickIntervals = new ArrayDeque<>();
	public long lastClickNanos;
	public boolean digging;
	public long lastDigActionNanos;
	public long lastPlaceNanos;
	public long lastAttackClientTick = -1;
	public int lastAttackEntityId = -1;
	public boolean attackedThisTick;
	public boolean swungThisTick;
	public final Map<String, Double> nettyBuffers = new HashMap<>();

	// ───── Main ─────
	public final Map<String, Double> buffers = new HashMap<>();
	public final PositionHistory history = new PositionHistory();
	public boolean hasLast;
	public double lastX, lastY, lastZ;
	public float lastYaw, lastPitch;
	public boolean hasLastRotation;
	public float lastDeltaYaw, lastDeltaPitch;
	public boolean hasLastDelta;
	public double lastDeltaX, lastDeltaY, lastDeltaZ;
	public long lastSampleTick = Long.MIN_VALUE;
	public boolean lastServerGround = true;
	public boolean lastClientGround = true;
	public int serverAirTicks;
	public Location lastLegit;
	public int lastAttackServerTick = -10_000;
	public int lastDamageTick = -10_000;
	public int movementExemptUntil;
	public double fallDistance;
	public boolean spoofedFall;

	public Vector pendingVelocity;
	public int velocityStart;
	public int velocityDeadline;
	public double velocityMaxHorizontal;
	public int velocitySamples;

	public final ArrayDeque<double[]> aimOffsets = new ArrayDeque<>();

	public PlayerData(Player player) {
		this.uuid = player.getUniqueId();
		this.player = player;
	}

	public UUID uuid() {
		return uuid;
	}

	public Player player() {
		return player;
	}

	public void resetMovement() {
		hasLast = false;
		hasLastDelta = false;
		moves.clear();
	}
}
