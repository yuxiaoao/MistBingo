package net.starryisle.mistbingo;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

/**
 * Renders the chat card the way the original datapack does: a centred clickable
 * line whose hover opens a bundle preview laid out as the 3x3 grid.
 */
public final class ChatUi {

    private static final TextColor GOLD = TextColor.color(0xFFCC44);
    private static final TextColor GREEN = TextColor.color(0xAAFFAA);
    private static final TextColor DARK = NamedTextColor.DARK_GRAY;

    private final MistBingoPlugin plugin;

    public ChatUi(MistBingoPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendHeader(Player player) {
        player.sendMessage(Component.empty()
                .append(Component.text("--==--==--==--==", DARK))
                .append(Component.text("  Mist Bingo  ", GOLD))
                .append(Component.text("==--==--==--==--", DARK)));
    }

    public void sendFooter(Player player) {
        player.sendMessage(legacy(plugin.settings().message("footer",
                "&8------&7 愿你天天开心，把把 Bingo &8------")));
    }

    /**
     * One block per card.
     *
     * <p>{@code ui.style: bundle} renders the datapack's single hoverable line, but
     * the bundle tooltip always carries its own {@code n/64} capacity bar. The
     * default {@code text} style prints the 3x3 as three lines, one hoverable cell
     * per item, so the card reads as {@code 0/9}.
     */
    public void sendCard(Player player, Card card, int index, int totalCards, int streak) {
        if (plugin.settings().uiStyle().equalsIgnoreCase("bundle")) {
            sendBundleCard(player, card, index, streak, totalCards);
            return;
        }
        sendTextCard(player, card, index);
    }

    private void sendBundleCard(Player player, Card card, int index, int streak, int pending) {
        ItemStack bundle = gridBundle(card, index, streak, pending);
        HoverEvent<HoverEvent.ShowItem> hover =
                Bukkit.getItemFactory().asHoverEvent(bundle, event -> event);

        // Each line names its card so a backlog of pending cards stays distinguishable.
        String tag = index == 0 ? "今天的表" : "旧表 " + index;
        String date = card.date();
        Component label = Component.text(" " + tag + " · 查看集物表 ", GREEN)
                .hoverEvent(hover)
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player clicker) {
                        plugin.daily().submit(clicker, date);
                    }
                }));

        player.sendMessage(Component.empty()
                .append(Component.text("  ✧ ", GOLD))
                .append(label)
                .append(Component.text(" " + card.progress() + "/9 ✧", GOLD))
                .append(Component.text("  " + card.date(), DARK)));
    }

    /** Three rows of three hoverable cells; no capacity bar anywhere. */
    private void sendTextCard(Player player, Card card, int index) {
        Component title = legacy("&8==== &6集物表 #" + (index + 1) + " &8==== &7进度 &f"
                + card.progress() + "&7/&f9");
        player.sendMessage(title);

        for (int row = 0; row < 3; row++) {
            Component line = Component.text(" ");
            for (int column = 0; column < 3; column++) {
                int cell = row * 3 + column;
                line = line.append(cellComponent(card, cell));
                if (column < 2) {
                    line = line.append(Component.text("│", DARK));
                }
            }
            player.sendMessage(line);
        }

        player.sendMessage(legacy("&8点击任意一格提交 · 每日刷新 · 最多累积 "
                + plugin.settings().maxPending() + " 张"));
    }

    private Component cellComponent(Card card, int cell) {
        ItemStack item = card.items().get(cell).clone();
        int amount = card.amount(cell);
        boolean done = card.isSubmitted(cell);
        String date = card.date();

        HoverEvent<HoverEvent.ShowItem> itemHover =
                Bukkit.getItemFactory().asHoverEvent(item, event -> event);

        String label = (done ? "✔ " : "  ") + plugin.names().of(item.getType()) + " ×" + amount;
        // Fixed width keeps the three columns aligned despite different name lengths.
        String padded = pad(label, 20);
        Component cellText = Component.text(padded)
                .color(done ? NamedTextColor.DARK_GREEN : NamedTextColor.WHITE)
                .hoverEvent(itemHover)
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player clicker) {
                        plugin.daily().submit(clicker, date);
                    }
                }));
        return Component.text(" ").append(cellText).append(Component.text(" "));
    }

    /** Pads to a fixed character width so the three columns line up. */
    private static String pad(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        return text + " ".repeat(width - text.length());
    }

    /** A bundle whose contents are the nine cells, three per row. */
    private ItemStack gridBundle(Card card, int index, int streak, int pending) {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        if (meta == null) {
            return bundle;
        }

        ItemStack pane = lighter(new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1));
        List<ItemStack> contents = new ArrayList<>();
        for (int cell = 0; cell < 9; cell++) {
            // The bundle preview lays out four items per row, so a separator after
            // every third cell is what makes it read as a 3x3 grid.
            // Stack size carries the required amount so the preview shows the usual
            // count badge. Raising the max stack size keeps the bundle's weight low,
            // otherwise one potion (stack size 1) would read as a full bundle.
            ItemStack entry = card.items().get(cell).clone();
            int amount = card.amount(cell);
            entry.setAmount(Math.max(1, Math.min(amount, 64)));
            var entryMeta = entry.getItemMeta();
            if (entryMeta != null) {
                entryMeta.setMaxStackSize(99);
                if (card.isSubmitted(cell)) {
                    entryMeta.setDisplayName(ChatColor.GREEN + "✔ " + ChatColor.RESET
                            + ChatColor.WHITE + plugin.names().of(entry.getType()));
                }
                entry.setItemMeta(entryMeta);
            }
            contents.add(entry);
            if (cell % 3 == 2) {
                contents.add(pane);
            }
        }
        try {
            meta.setItems(contents);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("集物表预览物品过多，无法放入 bundle: "
                    + exception.getMessage());
        }

        meta.setDisplayName(color("&8--=&6 Mist Bingo &8=--"));

        List<String> lore = new ArrayList<>();
        lore.add(color("&a点击提交背包内的物品！"));
        lore.add(color(" "));
        lore.add(color("&7收集任意行、列、对角线"));
        lore.add(color("&7的 3 个物品来完成集物表"));
        lore.add(color(" "));
        lore.add(color("&7集物表每日刷新，未完成的"));
        lore.add(color("&7集物表将累计至最多 " + plugin.settings().maxPending() + " 张"));
        lore.add(color(" "));
        lore.add(color("&e已累计完成 &f" + streak + " &e张集物表！"));
        lore.add(color("&6还有 &e" + pending + " &6张集物表等待完成..."));
        meta.setLore(lore);
        bundle.setItemMeta(meta);
        return bundle;
    }

    private static String pretty(Material material) {
        String name = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Separator pane, also lightened so it costs almost no bundle capacity. */
    private static ItemStack lighter(ItemStack pane) {
        ItemStack copy = pane.clone();
        var meta = copy.getItemMeta();
        if (meta != null) {
            meta.setMaxStackSize(99);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    /** Converts '&' colour codes into components. */
    static Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text == null ? "" : text);
    }
}
