package dev.atlasac.data;

/**
 * Eine vom Client empfangene Bewegungs-/Rotationsnachricht (Flying-Packet).
 * Wird auf dem Netty-Thread erzeugt und im Haupt-Thread ausgewertet.
 *
 * @param clientTick Anzahl der bis dahin empfangenen Client-Tick-End-Packets
 */
public record MoveSample(boolean hasPosition, double x, double y, double z,
						 boolean hasRotation, float yaw, float pitch,
						 boolean onGround, long clientTick, long nanos) {
}
