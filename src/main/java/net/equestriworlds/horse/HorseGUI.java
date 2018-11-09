package net.equestriworlds.horse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@Getter @RequiredArgsConstructor
final class HorseGUI implements InventoryHolder {
    private final HorsePlugin plugin;
    private Inventory inventory;
    private HashMap<Integer, Consumer<InventoryClickEvent>> clicks = new HashMap<>();

    InventoryView open(Player player) {
        List<HorseData> horses = this.plugin.findHorses(player);
        this.inventory = Bukkit.getServer().createInventory(this, 3 * 9, "" + ChatColor.DARK_BLUE + ChatColor.BOLD + "Horse List" + ChatColor.DARK_GRAY + "(" + horses.size() + ")");
        int currentIndex = 0;
        for (HorseData data: horses) {
            ItemStack icon;
            switch (data.getGender()) {
            case STALLION: icon = new ItemStack(Material.INK_SACK, 1, (short)12); break;
            case MARE: icon = new ItemStack(Material.INK_SACK, 1, (short)9); break;
            case GELDING: icon = new ItemStack(Material.INK_SACK, 1, (short)10); break;
            default: icon = new ItemStack(Material.INK_SACK);
            }
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + data.getName());
            String[] lore = {
                ChatColor.GOLD + "Age " + ChatColor.YELLOW + data.getAge().humanName,
                ChatColor.GOLD + "Gender " + ChatColor.YELLOW + data.getGender().humanName,
                ChatColor.GOLD + "Breed " + ChatColor.YELLOW + data.getBreed().humanName,
                "" + ChatColor.LIGHT_PURPLE + ChatColor.ITALIC + "CLICK " + ChatColor.WHITE + "More info",
                "" + ChatColor.LIGHT_PURPLE + ChatColor.ITALIC + "SHIFT-CLICK " + ChatColor.WHITE + "Bring horse"
            };
            meta.setLore(Arrays.asList(lore));
            icon.setItemMeta(meta);
            this.inventory.setItem(currentIndex, icon);
            clicks.put(currentIndex, (event) -> {
                    HumanEntity human = event.getWhoClicked();
                    if (event.isLeftClick() && !event.isShiftClick()) {
                        this.plugin.getHorseCommand().sendHorseInfo((Player)human, data);
                    } else if (event.isLeftClick() && event.isShiftClick()) {
                        this.plugin.teleportHorse(data, human.getLocation());
                        human.closeInventory();
                    }
                });
            currentIndex += 1;
        }
        return player.openInventory(this.inventory);
    }

    void onOpen(InventoryOpenEvent event) {
    }

    void onClose(InventoryCloseEvent event) {
    }

    void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(this.inventory)) return;
        int slot = event.getSlot();
        Consumer<InventoryClickEvent> consumer = this.clicks.get(slot);
        if (consumer == null) return;
        Bukkit.getScheduler().runTask(this.plugin, () -> consumer.accept(event));
    }

    void onDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }
}
