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

/** {@code /daily [submit <card>|pending|debug|reset]}. */
public final class MistDailyCommand implements CommandExecutor, TabCompleter {

    private final MistBingoPlugin plugin;

    public MistDailyCommand(MistBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("这个命令只能玩家使用");
            return true;
        }
        if (!player.hasPermission("mistbingo.use")
                && !player.hasPermission("mistbingo.admin")) {
            player.sendMessage("§c没有权限使用这个命令");
            return true;
        }

        if (args.length == 0) {
            plugin.daily().show(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "submit" -> {
                if (args.length < 2) {
                    plugin.daily().show(player);
                    return true;
                }
                int index;
                try {
                    index = Integer.parseInt(args[1]);
                } catch (NumberFormatException exception) {
                    player.sendMessage("§c集物表序号必须是数字，例如 /mistdaily submit 0");
                    return true;
                }
                plugin.daily().submit(player, index);
            }
            case "pending" -> {
                PlayerState state = plugin.daily().state(player);
                player.sendMessage("§7当前有 §f" + state.cards().size()
                        + " §7张集物表，累计完成 §f" + state.streak() + " §7张。");
            }
            case "debug" -> {
                if (!player.hasPermission("mistbingo.admin")) {
                    player.sendMessage("§c需要管理权限");
                    return true;
                }
                PlayerState state = plugin.daily().state(player);
                player.sendMessage("§7随机池: §f" + plugin.pool().size() + " §7种物品");
                player.sendMessage("§7今天: §f" + plugin.settings().today());
                player.sendMessage("§7内存中的玩家状态: §f" + plugin.daily().loadedStates());
                player.sendMessage("§7玩家记录: §f" + state.lastRollDate()
                        + " §7卡片数 §f" + state.cards().size());
                for (int index = 0; index < state.cards().size(); index++) {
                    Card card = state.cards().get(index);
                    player.sendMessage("§8--- 卡片 #" + index + " " + card.date()
                            + " 进度 " + card.progress() + "/9 ---");
                    for (int row = 0; row < 3; row++) {
                        StringBuilder line = new StringBuilder("§7");
                        for (int column = 0; column < 3; column++) {
                            int cell = row * 3 + column;
                            line.append(card.isSubmitted(cell) ? "§a[✔]" : "§8[ ]");
                        }
                        player.sendMessage(line.toString());
                    }
                    StringBuilder states = new StringBuilder("§7线路: ");
                    String[] names = {"行1", "行2", "行3", "列1", "列2", "列3", "主对", "副对"};
                    for (int line = 0; line < Card.lineCount(); line++) {
                        states.append(card.lineComplete(line)
                                ? "§a" + names[line] : "§8" + names[line]).append(" ");
                    }
                    player.sendMessage(states.toString());

                    player.sendMessage("§8格子需求（需要/持有）：");
                    for (int cell = 0; cell < 9; cell++) {
                        var item = card.items().get(cell);
                        if (item == null) {
                            continue;
                        }
                        int have = 0;
                        for (var stack : player.getInventory().getStorageContents()) {
                            if (stack != null && stack.getType() == item.getType()) {
                                have += stack.getAmount();
                            }
                        }
                        player.sendMessage("§7  " + cell + " " + item.getType()
                                + " 需" + card.amount(cell) + " 有" + have
                                + (card.isSubmitted(cell) ? " §a已交" : ""));
                    }
                }
            }
            case "reset" -> {
                if (!player.hasPermission("mistbingo.admin")) {
                    player.sendMessage("§c需要管理权限");
                    return true;
                }
                PlayerState state = plugin.daily().state(player);
                state.cards().clear();
                state.lastRollDate("");
                state.streak(0);
                plugin.daily().save(player);
                player.sendMessage("§a已重置你的集物表与累计完成数");
            }
            case "voucher", "give" -> {
                if (!player.hasPermission("mistbingo.admin")) {
                    player.sendMessage("§c需要管理权限");
                    return true;
                }
                giveVouchers(player, args);
            }
            default -> plugin.daily().show(player);
        }
        return true;
    }

    /** {@code /mistdaily voucher [player] <count>} — admins hand out vouchers directly. */
    private void giveVouchers(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /mistdaily voucher <玩家> <数量>  或  /mistdaily voucher <数量>");
            return;
        }

        Player target = sender;
        int amount;

        if (args.length >= 3) {
            target = sender.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§c找不到在线玩家: " + args[1]);
                return;
            }
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§c数量必须是数字");
                return;
            }
        } else {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§c数量必须是数字");
                return;
            }
        }

        if (amount <= 0 || amount > 2304) {
            sender.sendMessage("§c数量必须在 1 到 2304 之间");
            return;
        }

        final Player receiver = target;
        for (ItemStack voucher : plugin.voucher().createStacks(amount)) {
            var leftover = receiver.getInventory().addItem(voucher);
            leftover.values().forEach(stack ->
                    receiver.getWorld().dropItemNaturally(receiver.getLocation(), stack));
        }

        sender.sendMessage("§a已给 §f" + receiver.getName() + " §a发放 §e" + amount + " §a张建材兑换券");
        if (receiver != sender) {
            receiver.sendMessage("§a你收到了 §e" + amount + " §a张建材兑换券");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            for (String option : new String[]{"submit", "pending", "debug", "reset", "voucher"}) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    suggestions.add(option);
                }
            }
            return suggestions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("voucher")) {
            for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT)
                        .startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    suggestions.add(online.getName());
                }
            }
        }
        return suggestions;
    }
}
