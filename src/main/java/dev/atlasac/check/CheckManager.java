package dev.atlasac.check;

import dev.atlasac.AtlasAC;
import dev.atlasac.check.combat.*;
import dev.atlasac.check.movement.*;

import java.util.List;

public final class CheckManager {
	public final ReachCheck reach;
	public final HitboxCheck hitbox;
	public final KillAuraMultiCheck killAuraMulti;
	public final KillAuraWallCheck killAuraWall;
	public final KillAuraNoSwingCheck killAuraNoSwing;
	public final AimSensitivityCheck aimSensitivity;
	public final AimSnapCheck aimSnap;
	public final AimLockCheck aimLock;
	public final AutoClickerCpsCheck autoClickerCps;
	public final AutoClickerConsistencyCheck autoClickerConsistency;
	public final VelocityCheck velocity;
	public final CriticalsCheck criticals;
	public final GravityCheck gravity;
	public final AirCheck air;
	public final JumpCheck jump;
	public final GroundSpoofCheck groundSpoof;
	public final TimerCheck timer;
	public final BadPacketsCheck badPackets;
	public final MultiBreakCheck multiBreak;
	public final NoRotateCheck noRotate;
	public final NoSlowCheck noSlow;
	public final PhaseCheck phase;
	public final JesusCheck jesus;
	public final PlaceCheck place;
	public final KillAuraSnapCheck killAuraSnap;
	public final GroundSpeedCheck groundSpeed;
	public final StepCheck step;
	public final FastLadderCheck fastLadder;
	public final InvMoveCheck invMove;
	public final NoWebCheck noWeb;
	public final ChestStealerCheck chestStealer;
	public final FastUseCheck fastUse;

	private final List<Check> all;

	public CheckManager(AtlasAC plugin) {
		all = List.of(
				reach = new ReachCheck(plugin),
				hitbox = new HitboxCheck(plugin),
				killAuraMulti = new KillAuraMultiCheck(plugin),
				killAuraWall = new KillAuraWallCheck(plugin),
				killAuraNoSwing = new KillAuraNoSwingCheck(plugin),
				aimSensitivity = new AimSensitivityCheck(plugin),
				aimSnap = new AimSnapCheck(plugin),
				aimLock = new AimLockCheck(plugin),
				autoClickerCps = new AutoClickerCpsCheck(plugin),
				autoClickerConsistency = new AutoClickerConsistencyCheck(plugin),
				velocity = new VelocityCheck(plugin),
				criticals = new CriticalsCheck(plugin),
				gravity = new GravityCheck(plugin),
				air = new AirCheck(plugin),
				jump = new JumpCheck(plugin),
				groundSpoof = new GroundSpoofCheck(plugin),
				timer = new TimerCheck(plugin),
				badPackets = new BadPacketsCheck(plugin),
				multiBreak = new MultiBreakCheck(plugin),
				noRotate = new NoRotateCheck(plugin),
				noSlow = new NoSlowCheck(plugin),
				phase = new PhaseCheck(plugin),
				jesus = new JesusCheck(plugin),
				place = new PlaceCheck(plugin),
				killAuraSnap = new KillAuraSnapCheck(plugin),
				groundSpeed = new GroundSpeedCheck(plugin),
				step = new StepCheck(plugin),
				fastLadder = new FastLadderCheck(plugin),
				invMove = new InvMoveCheck(plugin),
				noWeb = new NoWebCheck(plugin),
				chestStealer = new ChestStealerCheck(plugin),
				fastUse = new FastUseCheck(plugin));
		reload();
	}

	public void reload() {
		all.forEach(Check::reload);
	}

	public List<Check> all() {
		return all;
	}
}
