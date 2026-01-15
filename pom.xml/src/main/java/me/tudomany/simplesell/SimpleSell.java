package me.tudomany.simplesell;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
        getCommand("simplesellsetbutton").setExecutor(this);
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

        // ADMIN: Eladó gomb beállítása (teljes item mentése)
        if (command.getName().equalsIgnoreCase("simplesellsetbutton")) {
            if (!player.hasPermission("simplesell.admin")) {
                player.sendMessage("§cEhhez nincs jogosultságod!");
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand().clone();
            if (hand.getType() == Material.AIR) {
                player.sendMessage("§cFogj egy tárgyat a kezedbe!");
                return true;
            }
            getConfig().set("custom-sell-button", hand);
            saveConfig();
            player.sendMessage("§8[§6Sell§8] §aAz eladó gomb sikeresen beállítva a kezedben lévő tárgyra!");
            return true;
        }

        // ADMIN: Tárgy hozzáadása az álistához
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
                player.sendMessage("§cFogj egy eladható tárgyat a kezedbe!");
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

        // JÁTÉKOS: GUI megnyitása
        if (command.getName().equalsIgnoreCase("sell")) {
            Inventory gui = Bukkit.createInventory(null, 45, guiTitle);
            
            // Felső sor dekoráció (0-8 slot)
            ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ", "");
            for (int i = 0; i < 9; i++) {
                gui.setItem(i, filler);
            }

            // Gomb betöltése
            ItemStack sellButton = getConfig().getItemStack("custom-sell-button");
            if (sellButton == null) {
                sellButton = createGuiItem(Material.GOLD_BLOCK, "§a§lELADÁS", "§7Kattints ide az eladáshoz!");
            }
            gui.setItem(4, sellButton);

            player.openInventory(gui);
            return true;
        }
        return true;
    }

    private void processSell(Player player, Inventory inv) {
        double totalMoney = 0;
        int count = 0;
        ConfigurationSection itemsSection = getConfig().getConfigurationSection("items");

        // Csak a 9-44 slotokat vizsgáljuk (a felső sort nem)
        for (int i = 9; i < 45; i++) {
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
            
            // GUI bezárása sikeres eladás után
            player.closeInventory();
            
            // Hang lejátszása
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1.0f, 1.0f);
            
            // Chat üzenet küldése
            String successMsg = formatMsg(getConfig().getString("messages.success", "&8[&6Sell&8] &aSikeres eladás: &f{amount} db &e{money}$"), count, totalMoney);
            player.sendMessage(successMsg);

            // Title és Subtitle küldése
            String title = formatMsg(getConfig().getString("messages.title", "&a&lSIKERES ELADÁS"), count, totalMoney);
            String subtitle = formatMsg(getConfig().getString("messages.subtitle", "&e+ {money}$"), count, totalMoney);
            player.sendTitle(title, subtitle, 10, 70, 20);
            
        } else {
            // Hiba: Nincs eladható tárgy
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(formatMsg(getConfig().getString("messages.no-items", "&cCsak eladható tárgyakat tegyél be!"), 0, 0));
        }
    }

    // Segédfüggvény az üzenetek formázásához
    private String formatMsg(String msg, int amount, double money) {
        return msg.replace("{amount}", String.valueOf(amount))
                  .replace("{money}", String.format("%.2f", money))
                  .replace("&", "§");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(guiTitle)) return;
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();
        if (slot >= 0 && slot < 9) {
            event.setCancelled(true);
            if (slot == 4) {
                processSell((Player) event.getWhoClicked(), event.getInventory());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(guiTitle)) return;
        Player player = (Player) event.getPlayer();
        // Visszaadjuk a tárgyakat, amik a 9-44 slotokban maradtak
        for (int i = 9; i < 45; i++) {
            ItemStack item = event.getInventory().getItem(i);
            if (item != null) {
                player.getInventory().addItem(item).values().forEach(r -> 
                    player.getWorld().dropItem(player.getLocation(), r));
            }
        }
    }

    private ItemStack createGuiItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { 
            meta.setDisplayName(name); 
            if (!lore.isEmpty()) meta.setLore(List.of(lore)); 
            item.setItemMeta(meta); 
        }
        return item;
    }
}
