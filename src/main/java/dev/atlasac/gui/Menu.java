package dev.atlasac.gui;

import dev.atlasac.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Einfaches Klick-Menue: jedes Feld hat optional eine Aktion. */
public final class Menu implements InventoryHolder {
	private final Inventory inventory;
	private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

	public Menu(int rows, String title) {
		this.inventory = Bukkit.createInventory(this, rows * 9, Text.mm(title));
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	public Menu set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
		inventory.setItem(slot, item);
		if (action != null) actions.put(slot, action);
		else actions.remove(slot);
		return this;
	}

	public Menu set(int slot, ItemStack item) {
		return set(slot, item, null);
	}

	public Menu fill(Material pane) {
		ItemStack filler = item(pane, " ");
		for (int i = 0; i < inventory.getSize(); i++) {
			if (inventory.getItem(i) == null) inventory.setItem(i, filler);
		}
		return this;
	}

	public void open(Player player) {
		player.openInventory(inventory);
	}

	void click(InventoryClickEvent event) {
		Consumer<InventoryClickEvent> a = actions.get(event.getRawSlot());
		if (a != null) a.accept(event);
	}

	public static ItemStack item(Material type, String name, String... lore) {
		List<String> l = new ArrayList<>(List.of(lore));
		return item(type, name, l);
	}

	public static ItemStack item(Material type, String name, List<String> lore) {
		ItemStack it = new ItemStack(type);
		ItemMeta meta = it.getItemMeta();
		meta.displayName(Text.item(name));
		List<Component> comps = new ArrayList<>();
		for (String s : lore) comps.add(Text.item(s));
		meta.lore(comps);
		meta.addItemFlags(ItemFlag.values());
		it.setItemMeta(meta);
		return it;
	}
}
