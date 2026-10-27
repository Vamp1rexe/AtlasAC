package dev.atlasac.xray;

import dev.atlasac.AtlasAC;
import dev.atlasac.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** /atlas xray – Grenzwerte pro Erz-Gruppe ingame einstellen. */
public final class XrayGui implements Listener {
	private final AtlasAC plugin;

	public XrayGui(AtlasAC plugin) {
		this.plugin = plugin;
	}

	private static final class Holder implements InventoryHolder {
		private Inventory inventory;
		private final List<String> slots = new ArrayList<>();

		@Override
		public Inventory getInventory() {
			return inventory;
		}
	}

	public void open(Player player) {
		Holder holder = new Holder();
		int count = plugin.xray().groups().size();
		int size = Math.min(54, Math.max(27, ((count + 8) / 9 + 2) * 9));
		Inventory inv = Bukkit.createInventory(holder, size, Text.mm("<gradient:#7b5cff:#00d4ff><bold>Atlas</bold></gradient> <dark_gray>» <gray>Anti-XRay"));
		holder.inventory = inv;
		render(holder);
		player.openInventory(inv);
	}

	private void render(Holder holder) {
		Inventory inv = holder.inventory;
		inv.clear();
		holder.slots.clear();
		ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, Text.item(" "), List.of());
		for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

		int slot = 10;
		for (OreGroup g : plugin.xray().groups().values()) {
			if (slot % 9 == 8) slot += 2;
			if (slot >= inv.getSize() - 9) break;
			List<Component> lore = new ArrayList<>();
			lore.add(Text.item(g.enabled ? "<green>● Aktiv" : "<red>● Deaktiviert"));
			lore.add(Text.item(""));
			lore.add(Text.item("<gray>Alert ab: <white>" + g.maxBlocks + " Bloecken"));
			lore.add(Text.item("<gray>Zeitfenster: <white>" + g.windowMinutes + " Minuten"));
			lore.add(Text.item(""));
			lore.add(Text.item("<yellow>Linksklick <gray>» Grenzwert +1"));
			lore.add(Text.item("<yellow>Rechtsklick <gray>» Grenzwert -1"));
			lore.add(Text.item("<yellow>Shift + Links <gray>» Zeitfenster +1 Min."));
			lore.add(Text.item("<yellow>Shift + Rechts <gray>» Zeitfenster -1 Min."));
			lore.add(Text.item("<yellow>Q (Droppen) <gray>» An/Aus"));
			ItemStack it = item(g.enabled ? g.icon : Material.GRAY_DYE, Text.item("<aqua><bold>" + Text.escape(g.display)), lore);
			inv.setItem(slot, it);
			while (holder.slots.size() <= slot) holder.slots.add(null);
			holder.slots.set(slot, g.id);
			slot++;
		}
		inv.setItem(inv.getSize() - 5, item(Material.BARRIER, Text.item("<red>Schliessen"), List.of()));
	}

	private static ItemStack item(Material type, Component name, List<Component> lore) {
		ItemStack it = new ItemStack(type);
		ItemMeta meta = it.getItemMeta();
		meta.displayName(name);
		meta.lore(lore);
		it.setItemMeta(meta);
		return it;
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (!(event.getInventory().getHolder(false) instanceof Holder holder)) return;
		event.setCancelled(true);
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (event.getClickedInventory() != holder.inventory) return;
		int slot = event.getRawSlot();
		if (slot == holder.inventory.getSize() - 5) {
			player.closeInventory();
			return;
		}
		if (!player.hasPermission(AtlasAC.PERM_ADMIN)) return;
		String id = slot < holder.slots.size() ? holder.slots.get(slot) : null;
		if (id == null) return;
		OreGroup g = plugin.xray().groups().get(id);
		if (g == null) return;

		ClickType click = event.getClick();
		switch (click) {
			case LEFT -> g.maxBlocks = Math.min(999, g.maxBlocks + 1);
			case RIGHT -> g.maxBlocks = Math.max(1, g.maxBlocks - 1);
			case SHIFT_LEFT -> g.windowMinutes = Math.min(1440, g.windowMinutes + 1);
			case SHIFT_RIGHT -> g.windowMinutes = Math.max(1, g.windowMinutes - 1);
			case DROP, CONTROL_DROP -> g.enabled = !g.enabled;
			default -> {
				return;
			}
		}
		plugin.xray().save(g);
		render(holder);
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event) {
		if (event.getInventory().getHolder(false) instanceof Holder) event.setCancelled(true);
	}
}
