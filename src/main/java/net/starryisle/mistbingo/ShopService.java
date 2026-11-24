package net.starryisle.mistbingo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;

/**
 * Shop state and the one place where a purchase actually happens.
 *
 * <p>Both shop windows (the clickable grid and the vanilla trade UI) end up here,
 * so charging, stock checks and messages cannot drift apart.
 */
public final class ShopService {

    /** Upper bound for a shop price; keeps merchant setup from building huge stacks. */
    public static final int MAX_PRICE = 2304;
    /** Vanilla merchant recipes only reliably show one ingredient stack. */
    private static final int MERCHANT_MAX_PRICE = 64;

    private final MistBingoPlugin plugin;
    private final List<ShopEntry> offers = new ArrayList<>();
    private boolean open = true;

    public ShopService(MistBingoPlugin plugin) {
        this.plugin = plugin;
    }

    public List<ShopEntry> offers() {
        return offers;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean toggle() {
        open = !open;
        plugin.shopStore().saveOpen(open);
        return open;
    }

    public void reload() {
        offers.clear();
        offers.addAll(plugin.shopStore().load());
        open = plugin.shopStore().loadOpen();
    }

    public void replace(List<ShopEntry> entries) {
        offers.clear();
        for (ShopEntry entry : entries) {
            offers.add(entry.copy());
        }
        plugin.shopStore().save(offers);
    }

    public void add(ShopEntry entry) {
        offers.add(entry);
        plugin.shopStore().save(offers);
    }

    public ShopEntry remove(int index) {
        if (index < 0 || index >= offers.size()) {
            return null;
        }
        ShopEntry removed = offers.remove(index);
        plugin.shopStore().save(offers);
        return removed;
    }

    /** Entry point for both the voucher and {@code /shop}. */
    public void open(Player player) {
        if (!open) {
            player.sendMessage("§7商店当前已关闭。");
            return;
        }
        if (offers.isEmpty()) {
            player.sendMessage("§7商店里还没有商品，请管理员手持物品执行 §f/mistshop add <价格> §7上架。");
            return;
        }
        if (plugin.settings().shopStyle().equalsIgnoreCase("merchant")) {
            openMerchant(player);
            return;
        }
        plugin.shopGui().open(player);
    }

    private void openMerchant(Player player) {
        Merchant merchant = Bukkit.createMerchant(Component.text("建材兑换"));
        List<MerchantRecipe> recipes = new ArrayList<>();
        int skipped = 0;
        for (ShopEntry entry : offers) {
            if (entry.price() > MERCHANT_MAX_PRICE) {
                skipped++;
                continue;
            }
            MerchantRecipe recipe = new MerchantRecipe(entry.item().clone(), 0, 999_999, false);
            for (ItemStack ingredient : plugin.voucher().createStacks(entry.price())) {
                recipe.addIngredient(ingredient);
            }
            recipes.add(recipe);
        }
        if (skipped > 0) {
            plugin.getLogger().warning("[shop] 商人模式跳过 " + skipped
                    + " 件价格超过 " + MERCHANT_MAX_PRICE + " 的商品，请改用 gui 模式");
        }
        merchant.setRecipes(recipes);
        player.openMerchant(merchant, true);
    }

    /**
     * Charges the player and hands over the item.
     *
     * @return true when the trade completed
     */
    public boolean purchase(Player player, ShopEntry entry) {
        if (!open) {
            player.sendMessage("§7商店当前已关闭。");
            return false;
        }
        int price = entry.price();
        if (price > MAX_PRICE) {
            plugin.getLogger().warning("[shop] 商品价格超过上限，已拒绝: "
                    + entry.item().getType() + " 价格=" + price);
            player.sendMessage("§c这件商品价格异常，请联系管理员");
            return false;
        }
        int have = countVouchers(player);
        plugin.debugLog("[shop] " + player.getName() + " 购买 "
                + entry.item().getType() + "×" + entry.item().getAmount()
                + " 价格=" + price + " 持有券=" + have);

        if (price <= 0) {
            plugin.getLogger().warning("[shop] 商品价格为 0，已拒绝: " + entry.item().getType());
            player.sendMessage("§c这件商品价格异常，请联系管理员");
            return false;
        }
        if (have < price) {
            player.sendMessage("§c兑换券不足：需要 §e" + price
                    + " §c张，你有 §e" + have + " §c张");
            return false;
        }

        Inventory inventory = player.getInventory();
        if (!hasRoomFor(inventory, entry.item())) {
            player.sendMessage("§c背包空间不足，请先清理一下");
            return false;
        }

        takeVouchers(player, price);

        int after = countVouchers(player);
        if (after != have - price) {
            // The deduction did not match what the check saw; put the player back
            // where they started instead of handing out a free item.
            plugin.getLogger().severe("[shop] 扣券异常: 之前=" + have + " 之后=" + after
                    + " 价格=" + price + "，已退款并中止交易");
            refundVouchers(player, price);
            player.sendMessage("§c交易异常，已取消（券已退回，请联系管理员）");
            return false;
        }

        Map<Integer, ItemStack> leftover = inventory.addItem(entry.item().clone());
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
        player.sendMessage("§a兑换成功：§f" + entry.item().getType() + " ×"
                + entry.item().getAmount() + " §7(-" + price + " 券，剩余 " + after + " 张)");
        return true;
    }

    public int countVouchers(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (plugin.voucher().isVoucher(stack)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private void takeVouchers(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack stack = contents[index];
            if (stack == null || !plugin.voucher().isVoucher(stack)) {
                continue;
            }
            int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (stack.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        player.getInventory().setContents(contents);
    }

    /** Returns a failed purchase's vouchers to the player. */
    private void refundVouchers(Player player, int amount) {
        for (ItemStack stack : plugin.voucher().createStacks(amount)) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack dropped : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), dropped);
            }
        }
    }

    private static boolean hasRoomFor(Inventory inventory, ItemStack item) {
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null) {
                return true;
            }
            if (stack.isSimilar(item)
                    && stack.getAmount() + item.getAmount() <= stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }
}
