package net.starryisle.mistbingo;

import org.bukkit.inventory.ItemStack;

/** One shop offer: what the player receives and how many vouchers it costs. */
public record ShopEntry(ItemStack item, int price) {

    public ShopEntry copy() {
        return new ShopEntry(item.clone(), price);
    }
}
