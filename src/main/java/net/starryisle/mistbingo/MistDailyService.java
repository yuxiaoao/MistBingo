package net.starryisle.mistbingo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Rolls cards, applies submissions and hands out vouchers. */
public final class MistDailyService {

    private final MistBingoPlugin plugin;
    /** Loaded states only; entries are dropped when a player leaves. */
    private final Map<UUID, PlayerState> states = new HashMap<>();

    public MistDailyService(MistBingoPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerState state(Player player) {
        PlayerState state = states.computeIfAbsent(player.getUniqueId(),
                uuid -> plugin.store().get(uuid));
        state.lastSeen(System.currentTimeMillis());
        return state;
    }

    /** How many player states are held in memory, used by {@code /mistdaily debug}. */
    public int loadedStates() {
        return states.size();
    }

    /** Writes the player's state back to the store and frees the cached copy. */
    public void unload(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerState state = states.remove(uuid);
        if (state != null) {
            plugin.store().save(uuid, state);
        }
    }

    /** Drops states of players who are no longer online, as a safety net. */
    public void evictOffline() {
        states.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
    }

    public void save(Player player) {
        PlayerState state = states.get(player.getUniqueId());
        if (state != null) {
            plugin.store().save(player.getUniqueId(), state);
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, PlayerState> entry : states.entrySet()) {
            plugin.store().save(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Makes sure the player owns today's card, keeping unfinished older cards as
     * pending ones (capped by {@code max-pending}).
     */
    public void ensureToday(Player player) {
        PlayerState state = state(player);
        String today = plugin.settings().today().toString();
        if (today.equals(state.lastRollDate())) {
            return;
        }

        Card card;
        try {
            card = plugin.pool().roll(today);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("生成 " + player.getName() + " 的集物表失败: "
                    + exception.getMessage());
            return;
        }
        state.cards().add(0, card);
        state.lastRollDate(today);

        // Drop the oldest finished cards, then cap the pending backlog.
        state.cards().removeIf(Card::completed);
        int maxCards = plugin.settings().maxPending() + 1;
        while (state.cards().size() > maxCards) {
            state.cards().remove(state.cards().size() - 1);
        }
        save(player);
    }

    /**
     * Picks the line to work on this click: one that can be finished now, else the
     * one furthest along. Returns -1 when no line has anything to submit.
     */
    private int chooseLine(Card card, PlayerInventory inventory) {
        Map<Material, Integer> available = new EnumMap<>(Material.class);
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && !plugin.voucher().isVoucher(stack)) {
                available.merge(stack.getType(), stack.getAmount(), Integer::sum);
            }
        }

        int best = -1;
        int bestScore = -1;
        for (int index = 0; index < Card.lineCount(); index++) {
            if (card.lineComplete(index)) {
                continue;
            }
            Map<Material, Integer> left = new EnumMap<>(Material.class);
            left.putAll(available);
            int done = 0;
            int ready = 0;
            for (int cell : Card.line(index)) {
                if (card.isSubmitted(cell)) {
                    done++;
                    continue;
                }
                ItemStack needed = card.items().get(cell);
                if (needed == null) {
                    continue;
                }
                Material material = needed.getType();
                int amount = card.amount(cell);
                int have = left.getOrDefault(material, 0);
                if (have >= amount) {
                    ready++;
                    left.put(material, have - amount);
                }
            }
            if (done + ready == 0) {
                continue;
            }
            boolean completes = done + ready == 3;
            int score = (completes ? 1000 : 0) + done * 20 + ready;
            if (score > bestScore) {
                bestScore = score;
                best = index;
            }
        }
        return best;
    }

    /** Human-readable line state, so the log shows why a card counted as complete. */
    private static String describeLines(Card card) {
        StringBuilder builder = new StringBuilder();
        for (int line = 0; line < Card.lineCount(); line++) {
            builder.append(card.lineComplete(line) ? "1" : "0");
        }
        return builder.toString();
    }

    public void show(Player player) {
        ensureToday(player);
        List<Card> cards = state(player).cards();
        if (cards.isEmpty()) {
            player.sendMessage(color(plugin.settings().message("no-card",
                    "&7你还没有集物表，稍后再试。")));
            player.sendMessage(color(plugin.settings().message("done-today",
                    "&7今天的集物表已经完成，明天 0 点刷新后再来。")));
            return;
        }
        for (int index = 0; index < cards.size(); index++) {
            if (index == 0) {
                plugin.chat().sendHeader(player);
            }
            plugin.chat().sendCard(player, cards.get(index), index, cards.size(),
                    state(player).streak());
        }
        plugin.chat().sendFooter(player);
    }

    /**
     * Submits the card with the given date, so an old chat message cannot act on a
     * different card after that card was completed or removed.
     */
    public void submit(Player player, String date) {
        ensureToday(player);
        List<Card> cards = state(player).cards();
        for (int index = 0; index < cards.size(); index++) {
            if (date != null && date.equals(cards.get(index).date())) {
                submit(player, index);
                return;
            }
        }
        player.sendMessage(color(plugin.settings().message("stale-card",
                "&7这张集物表已经刷新或完成，请重新打开 /mistdaily。")));
    }

    /**
     * Submits every cell of one card that the inventory can satisfy, then reports
     * what was handed in and whether the card completed.
     */
    public void submit(Player player, int cardIndex) {
        ensureToday(player);
        List<Card> cards = state(player).cards();
        if (cardIndex < 0 || cardIndex >= cards.size()) {
            player.sendMessage(color(plugin.settings().message("no-card",
                    "&7你还没有集物表，稍后再试。")));
            return;
        }

        Card card = cards.get(cardIndex);
        plugin.debugLog("[submit] " + player.getName() + " 卡片#" + cardIndex
                + " 进度 " + card.progress() + "/9");
        if (card.completed()) {
            player.sendMessage(color(plugin.settings().message("already", "&7这张表已经完成了。")));
            return;
        }

        PlayerInventory inventory = player.getInventory();
        List<String> handed = new ArrayList<>();
        int submittedCells = 0;

        // Line-first: commit to one line per click instead of scattering items
        // across the board. Prefer a line this click can actually finish.
        int chosen = chooseLine(card, inventory);
        plugin.debugLog("[submit] " + player.getName() + " 选中线路="
                + (chosen < 0 ? "无" : Card.lineName(chosen)));
        if (chosen >= 0) {
            for (int cell : Card.line(chosen)) {
                if (card.isSubmitted(cell)) {
                    continue;
                }
                ItemStack needed = card.items().get(cell);
                int amount = card.amount(cell);
                int have = countItem(inventory, needed.getType());
                plugin.debugLog("[submit]   格" + cell + " 需要 "
                        + needed.getType() + "×" + amount + " 持有=" + have);
                if (have < amount) {
                    continue;
                }
                takeItems(inventory, needed.getType(), amount);
                card.markSubmitted(cell);
                submittedCells++;
                handed.add(plugin.names().of(needed.getType()) + " ×" + amount);
            }
            if (submittedCells > 0) {
                player.sendMessage(color("§7正在凑 " + cardTag(cardIndex) + " §f"
                        + Card.lineName(chosen) + "§7："));
            }
        }

        if (submittedCells == 0) {
            // Nothing was handed in: show every row's shortfall, then stop. Printing
            // only the first three cells made it look like row 1 was required.
            player.sendMessage(color(plugin.settings().message("nothing",
                    "&7背包里没有可以提交的物品。")));
            for (String line : shortfallGrid(card, inventory)) {
                player.sendMessage(color(line));
            }
            return;
        }
        player.sendMessage(color(plugin.settings().message("submitted",
                "&a提交了 {count} 格：&f{items}")
                .replace("{count}", String.valueOf(submittedCells))
                .replace("{items}", String.join("、", handed))));

        boolean finished = card.completed();
        plugin.debugLog("[submit] " + player.getName() + " 卡片#" + cardIndex
                + " 本次提交 " + submittedCells + " 格，进度 " + card.progress()
                + "/9，完成=" + finished + "，行=" + describeLines(card));

        if (finished) {
            int reward = plugin.voucher().rewardPerCard();
            PlayerState state = state(player);
            state.streak(state.streak() + 1);

            // Mirrors the datapack: every fifth completion pays a bonus.
            int every = plugin.settings().bonusEvery();
            if (every > 0 && state.streak() % every == 0) {
                reward += plugin.settings().bonusExtra();
            }

            giveVoucher(player, reward);
            runRewardCommands(player);
            cards.remove(cardIndex);
            plugin.debugLog("[submit] " + player.getName() + " 完成集物表，发放 "
                    + reward + " 张券，累计完成 " + state.streak() + " 张");
            player.sendMessage(color(plugin.settings().message("completed",
                    "&6完成一张集物表！获得 &e{reward} &6张建材兑换券")
                    .replace("{reward}", String.valueOf(reward))));
        }

        save(player);
        show(player);
    }

    /** Label for a card position, matching the chat list. */
    private static String cardTag(int index) {
        return index == 0 ? "今天的表" : "旧表 " + index;
    }

    /**
     * Prints what every row still needs, so a failed submit cannot look like the
     * plugin only cares about the first row.
     */
    private List<String> shortfallGrid(Card card, PlayerInventory inventory) {
        List<String> lines = new ArrayList<>();
        String[] rowNames = {"行1", "行2", "行3"};
        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder("§7" + rowNames[row] + "：");
            for (int column = 0; column < 3; column++) {
                int cell = row * 3 + column;
                if (card.isSubmitted(cell)) {
                    line.append("§a✔");
                    continue;
                }
                Material material = card.items().get(cell).getType();
                int need = card.amount(cell);
                int have = 0;
                for (ItemStack stack : inventory.getStorageContents()) {
                    if (stack != null && stack.getType() == material
                            && !plugin.voucher().isVoucher(stack)) {
                        have += stack.getAmount();
                    }
                }
                line.append(have >= need
                        ? "§a" + plugin.names().shortOf(material)
                        : "§c" + plugin.names().shortOf(material) + "§8(" + have + "/" + need + ")");
                if (column < 2) {
                    line.append(" ");
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** Short material label so three cells fit on one chat line. */
    private static String shortName(Material material) {
        String name = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return name.length() > 10 ? name.substring(0, 10) + "…" : name;
    }

    private void giveVoucher(Player player, int amount) {
        for (ItemStack voucher : plugin.voucher().createStacks(amount)) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(voucher);
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
    }

    /** Runs the configured console rewards once per completed card. */
    private void runRewardCommands(Player player) {
        for (String command : plugin.settings().rewardCommands()) {
            String resolved = command.replace("%player%", player.getName());
            if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved)) {
                plugin.debugLog("[reward] " + player.getName() + " -> " + resolved);
            } else {
                plugin.getLogger().warning("完成集物表的奖励命令执行失败: " + resolved);
            }
        }
    }

    private int countItem(PlayerInventory inventory, Material material) {
        int found = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.getType() == material
                    && !plugin.voucher().isVoucher(stack)) {
                found += stack.getAmount();
            }
        }
        return found;
    }

    private void takeItems(PlayerInventory inventory, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack stack = contents[index];
            if (stack == null || stack.getType() != material
                    || plugin.voucher().isVoucher(stack)) {
                continue;
            }
            int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (stack.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        inventory.setStorageContents(contents);
    }

    private static String pretty(Material material) {
        String name = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
