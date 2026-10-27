package dev.atlasac.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import dev.atlasac.AtlasAC;
import dev.atlasac.check.CheckManager;
import dev.atlasac.data.MoveSample;
import dev.atlasac.data.PlayerData;

/**
 * Liest die Client-Packets direkt aus dem Netzwerk (wie Grim).
 * Laeuft auf dem Netty-Thread – hier werden nur Daten gesammelt und netzwerknahe Checks gerechnet,
 * alles mit Weltzugriff passiert im Haupt-Thread (MovementProcessor).
 */
public final class AtlasPacketListener extends PacketListenerAbstract {
	private static final long DIG_GRACE = 300_000_000L;
	private static final long PLACE_GRACE = 60_000_000L;

	private final AtlasAC plugin;

	public AtlasPacketListener(AtlasAC plugin) {
		super(PacketListenerPriority.MONITOR);
		this.plugin = plugin;
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		User user = event.getUser();
		if (user == null) return;
		PlayerData d = plugin.data().get(user.getUUID());
		if (d == null) return;
		CheckManager checks = plugin.checks();
		PacketTypeCommon type = event.getPacketType();
		long now = System.nanoTime();

		if (WrapperPlayClientPlayerFlying.isFlying(type)) {
			WrapperPlayClientPlayerFlying w = new WrapperPlayClientPlayerFlying(event);
			Location loc = w.getLocation();
			d.moves.add(new MoveSample(w.hasPositionChanged(), loc.getX(), loc.getY(), loc.getZ(),
					w.hasRotationChanged(), loc.getYaw(), loc.getPitch(), w.isOnGround(), d.clientTick, now));
			return;
		}
		if (type == PacketType.Play.Client.CLIENT_TICK_END) {
			d.tickEndSupported = true;
			if (!d.bypass) {
				checks.timer.onTickEnd(d, now);
				checks.killAuraNoSwing.onTickEnd(d);
			}
			d.clientTick++;
			return;
		}
		if (type == PacketType.Play.Client.INTERACT_ENTITY) {
			WrapperPlayClientInteractEntity w = new WrapperPlayClientInteractEntity(event);
			if (w.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
				d.attackedThisTick = true;
				if (!d.bypass) checks.killAuraMulti.onAttack(d, w.getEntityId());
			} else {
				d.lastPlaceNanos = now;
			}
			return;
		}
		if (type == PacketType.Play.Client.ANIMATION) {
			d.swungThisTick = true;
			boolean ignore = d.digging || now - d.lastDigActionNanos < DIG_GRACE || now - d.lastPlaceNanos < PLACE_GRACE;
			if (!ignore && !d.bypass) {
				checks.autoClickerCps.onClick(d, now);
				checks.autoClickerConsistency.onClick(d, now);
			}
			return;
		}
		if (type == PacketType.Play.Client.PLAYER_DIGGING) {
			WrapperPlayClientPlayerDigging w = new WrapperPlayClientPlayerDigging(event);
			DiggingAction action = w.getAction();
			if (action == DiggingAction.START_DIGGING) {
				d.digging = true;
				d.lastDigActionNanos = now;
			} else if (action == DiggingAction.CANCELLED_DIGGING || action == DiggingAction.FINISHED_DIGGING) {
				d.digging = false;
				d.lastDigActionNanos = now;
			}
			return;
		}
		if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT || type == PacketType.Play.Client.USE_ITEM) {
			d.lastPlaceNanos = now;
		}
	}
}
