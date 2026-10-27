package dev.atlasac.command;

import dev.atlasac.AtlasAC;
import dev.atlasac.staff.StaffModeManager;
import dev.atlasac.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** /staff – Staff-Modus an/aus, /staff vanish, /staff gui. */
public final class StaffCommand implements BasicCommand {
	private final AtlasAC plugin;

	public StaffCommand(AtlasAC plugin) {
		this.plugin = plugin;
	}

	@Override
	public String permission() {
		return StaffModeManager.PERM_STAFF;
	}

	@Override
	public void execute(CommandSourceStack source, String[] args) {
		if (!(source.getSender() instanceof Player p)) {
			source.getSender().sendMessage(Text.mm("<red>Nur ingame."));
			return;
		}
		String sub = args.length > 0 ? args[0].toLowerCase() : "";
		switch (sub) {
			case "vanish", "v" -> plugin.staffMode().toggleVanish(p);
			case "gui" -> plugin.adminGui().openList(p, 0);
			default -> plugin.staffMode().toggle(p);
		}
	}

	@Override
	public Collection<String> suggest(CommandSourceStack source, String[] args) {
		String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
		return args.length <= 1 ? List.of("vanish", "gui").stream().filter(s -> s.startsWith(last)).toList() : List.of();
	}
}
