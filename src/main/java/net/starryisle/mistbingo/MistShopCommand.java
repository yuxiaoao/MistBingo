package net.starryisle.mistbingo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /shop [edit|list|remove <index>]}. */
public final class MistShopCommand implements CommandExecutor, TabCompleter {

    private final MistBingoPlugin plugin;

    public MistShopCommand(MistBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("这个命令只能玩家使用");
            return true;
        }

        if (args.length == 0) {
            if (!player.hasPermission("mistbingo.shop")) {
                player.sendMessage("§c没有权限");
                return true;
            }
            plugin.shop().open(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (!player.hasPermission("mistbingo.admin")) {
                    player.sendMessage("§c需要管理权限");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /mistshop add <价格> （手持要出售的物品）");
                    return true;
                }
                int price;
                try {
                    price = Integer.parseInt(args[1]);
                } catch (NumberFormatException exception) {
                    player.sendMessage("§c价格必须是数字");
                    return true;
                }
                if (price <= 0) {
                    player.sendMessage("§c价格必须大于 0");
                    return true;
                }
                if (price > ShopService.MAX_PRICE) {
                    player.sendMessage("§c价格不能超过 " + ShopService.MAX_PRICE);
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir() || hand.getAmount() <= 0) {
                    player.sendMessage("§c手上没有物品");
                    return true;
                }
                ShopEntry offer = new ShopEntry(hand.clone(), price);
                plugin.shop().add(offer);
                player.sendMessage("§a已上架 §f" + hand.getType() + " ×" + hand.getAmount()
                        + " §7价格 §e" + price + " §7张券（共 "
                        + plugin.shop().offers().size() + " 条）");
            }
            case "generate", "build" -> {
                if (!player.hasPermission("mistbingo.admin")) {
                    player.sendMessage("§c需要管理权限");
                    return true;
                }
                int price = plugin.getConfig().getInt("shop.catalog-price", 1);
                if (args.length >= 2) {
                    try {
                        price = Integer.parseInt(args[1]);
                    } catch (NumberFormatException exception) {
                        player.sendMessage("§c价格必须是数字");
                        return true;
                    }
                }
                if (price <= 0) {
                    player.sendMessage("§c价格必须大于 0");
                    return true;
                }
                if (price > ShopService.MAX_PRICE) {
                    player.sendMessage("§c价格不能超过 " + ShopService.MAX_PRICE);
                    return true;
                }
                List<ShopEntry> catalog = new ShopCatalog(plugin).build(price);
                plugin.shop().replace(catalog);
                player.sendMessage("§a已生成商店目录：§f" + catalog.size()
                        + " §a件（16 色混凝土 + 16 色染色玻璃），单价 §e" + price + " §a券");
            }
            case "edit" -> {
                player.sendMessage("§7编辑界面已移除，请用指令上架：");
                player.sendMessage("§f  手持物品执行 §e/mistshop add <价格>");
                player.sendMessage("§f  §e/mistshop list §7查看，§e/mistshop remove <序号> §7下架");
            }            case "list" -> {
                if (!player.hasPermission("mistbingo.shop")
                        && !player.hasPermission("mistbingo.admin")) {
                    player.sendMessage("§c没有权限");
                    return true;
                }
                List<ShopEntry> offers = plugin.shop().offers();
                if (offers.isEmpty()) {
                    player.sendMessage("§7商店还没有商品");
                    return true;
                }
                player.sendMessage("§6商店商品（" + offers.size() + "）：");
                for (int index = 0; index < offers.size(); index++) {
                    ShopEntry entry = offers.get(index);
                    player.sendMessage("§7#" + index + " §f" + entry.item().getType()
                            + " ×" + entry.item().getAmount()
                            + " §7价格 §e" + entry.price() + " §7券");
                }
            }
            case "remove" -> {
                if (!player.hasPermission("mistbingo.admin")) {
                    player.sendMessage("§c需要管理权限");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /mistshop remove <序号>");
                    return true;
                }
                try {
                    int index = Integer.parseInt(args[1]);
                    ShopEntry removed = plugin.shop().remove(index);
                    if (removed == null) {
                        player.sendMessage("§c序号超出范围");
                        return true;
                    }
                    player.sendMessage("§a已下架 §f" + removed.item().getType());
                } catch (NumberFormatException exception) {
                    player.sendMessage("§c序号必须是数字");
                }
            }
            case "open", "close" -> {
                if (!player.hasPermission("mistbingo.admin")) {
                    player.sendMessage("§c需要管理权限");
                    return true;
                }
                boolean want = args[0].equalsIgnoreCase("open");
                if (plugin.shop().isOpen() != want) {
                    plugin.shop().toggle();
                }
                player.sendMessage(want ? "§a商店已开启" : "§c商店已关闭");
            }
            default -> {
                if (!player.hasPermission("mistbingo.shop")
                        && !player.hasPermission("mistbingo.admin")) {
                    player.sendMessage("§c没有权限");
                    return true;
                }
                plugin.shop().open(player);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            for (String option : new String[]{"list", "remove", "add", "open", "close", "generate"}) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    suggestions.add(option);
                }
            }
        }
        return suggestions;
    }
}
