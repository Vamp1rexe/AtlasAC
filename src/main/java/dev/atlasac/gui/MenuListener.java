package dev.atlasac.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {
	@EventHandler(priority = EventPriority.LOWEST)
	public void onClick(InventoryClickEvent event) {
		if (!(event.getInventory().getHolder(false) instanceof Menu menu)) return;
		event.setCancelled(true);
		if (event.getClickedInventory() == menu.getInventory()) menu.click(event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDrag(InventoryDragEvent event) {
		if (event.getInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
	}
}
