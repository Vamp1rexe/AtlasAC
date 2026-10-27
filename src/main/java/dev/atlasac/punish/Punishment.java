package dev.atlasac.punish;

/**
 * Eine Strafe. target ist eine UUID (BAN, MUTE, KICK) oder eine IP (IPBAN).
 * expires = -1 fuer permanent, 0 fuer sofort (Kick).
 */
public record Punishment(String id, Type type, String target, String name, String reason, String staff,
						 long created, long expires, boolean active) {
	public enum Type {BAN, IPBAN, MUTE, KICK}

	public boolean permanent() {
		return expires < 0;
	}

	public boolean expired(long now) {
		return expires >= 0 && now >= expires;
	}

	public boolean inForce(long now) {
		return active && type != Type.KICK && !expired(now);
	}

	public Punishment deactivate() {
		return new Punishment(id, type, target, name, reason, staff, created, expires, false);
	}
}
