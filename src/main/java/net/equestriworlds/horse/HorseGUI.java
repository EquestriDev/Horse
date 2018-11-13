package net.equestriworlds.horse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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
        if (this.inventory == null) throw new NullPointerException("inventory cannot be null");
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

    // --- Prepare specific

    HorseGUI horseList(Player player) {
        if (this.inventory != null) throw new IllegalStateException("inventory already initialized!");
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
            meta.setDisplayName(data.getName());
            meta.setLore(this.plugin.getHorseCommand().describeHorse(data).stream().map(bc -> bc.toPlainText()).collect(Collectors.toList()));
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
        return this;
    }

    HorseGUI brandList(int page) {
        ArrayList<HorseBrand> brands = new ArrayList<>(this.plugin.getHorseBrands().values());
        this.inventory = Bukkit.getServer().createInventory(this, 4 * 9, "" + ChatColor.DARK_RED + ChatColor.BOLD + "Brand List" + ChatColor.DARK_GRAY + "(" + brands.size() + ")");
        int pageSize = 3 * 9;
        int startIndex = pageSize * page;
        for (int i = 0; i < pageSize; i += 1) {
            int index = startIndex + i;
            if (i >= brands.size()) break;
            HorseBrand horseBrand = brands.get(index);
            long brandedHorses = this.plugin.getHorses().stream().filter(h -> horseBrand.equals(h.getBrand())).count();
            ItemStack item = new ItemStack(Material.SIGN);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(horseBrand.getFormat());
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Owner " + ChatColor.WHITE + this.plugin.cachedPlayerName(horseBrand.getOwner()),
                                       ChatColor.GRAY + "Horses " + ChatColor.WHITE + brandedHorses));
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }
        return this;
    }
}
