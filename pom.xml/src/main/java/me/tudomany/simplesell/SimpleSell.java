package me.tudomany.simplesell;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public class SimpleSell extends JavaPlugin implements CommandExecutor, Listener {

    private static Economy econ = null;
    private final String guiTitle = "§6§lEladó Felület";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupEconomy();
        getCommand("sell").setExecutor(this);
        getCommand("simpleselladminadd").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (command.getName().equalsIgnoreCase("simpleselladminadd")) {
            if (!player.hasPermission("simplesell.admin")) {
                player.sendMessage("§cEhhez nincs jogosultságod!");
                return true;
            }
            if (args.length != 1) {
                player.sendMessage("§cHasználat: /simpleselladminadd <ár>");
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                player.sendMessage("§cFogj egy tárgyat a kezedbe!");
                return true;
            }
            try {
                double price = Double.parseDouble(args[0]);
                String id = UUID.randomUUID().toString().substring(0, 8);
                getConfig().set("items." + id + ".item", hand);
                getConfig().set("items." + id + ".price", price);
                saveConfig();
                player.sendMessage("§8[§6Sell§8] §aTárgy mentve! Ár: §e" + price + "$");
            } catch (NumberFormatException e) {
                player.sendMessage("§cÉrvénytelen ár!");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("sell")) {
            Inventory gui = Bukkit.createInventory(null, 36, guiTitle);
            gui.setItem(31, createGuiItem(Material.GREEN_STAINED_GLASS_PANE, "§a§lELADÁS", "§7Kattints az eladáshoz!"));
            gui.setItem(35, createGuiItem(Material.RED_STAINED_GLASS_PANE, "§c§lMÉGSE", "§7Minden tárgyat visszakapsz."));
            player.openInventory(gui);
            return true;
        }
        return true;
    }

    private void processSell(Player player, Inventory inv) {
        double totalMoney = 0;
        int count = 0;
        ConfigurationSection itemsSection = getConfig().getConfigurationSection("items");

        for (int i = 0; i < 27; i++) {
            ItemStack invItem = inv.getItem(i);
            if (invItem == null || invItem.getType() == Material.AIR) continue;

            if (itemsSection != null) {
                for (String key : itemsSection.getKeys(false)) {
                    ItemStack configItem = itemsSection.getItemStack(key + ".item");
                    if (configItem != null && configItem.isSimilar(invItem)) {
                        double price = itemsSection.getDouble(key + ".price");
                        totalMoney += price * invItem.getAmount();
                        count += invItem.getAmount();
                        inv.setItem(i, null);
                        break;
                    }
                }
            }
        }

        if (totalMoney > 0) {
            econ.depositPlayer(player, totalMoney);
            player.sendMessage("§8[§6Sell§8] §aSikeresen eladtál §f" + count + " §atárgyat §e" + totalMoney + "$ §aértékben!");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(guiTitle)) return;
        if (event.getCurrentItem() == null) return;
        int slot = event.getRawSlot();
        if (slot >= 27 && slot < 36) {
            event.setCancelled(true);
            if (slot == 31) {
                processSell((Player) event.getWhoClicked(), event.getInventory());
                event.getWhoClicked().closeInventory();
            } else if (slot == 35) {
                event.getWhoClicked().closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(guiTitle)) return;
        Player player = (Player) event.getPlayer();
        for (int i = 0; i < 27; i++) {
            ItemStack item = event.getInventory().getItem(i);
            if (item != null) player.getInventory().addItem(item).values().forEach(r -> player.getWorld().dropItem(player.getLocation(), r));
        }
    }

    private ItemStack createGuiItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(List.of(lore)); item.setItemMeta(meta); }
        return item;
    }
}
