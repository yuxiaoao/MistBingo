package net.starryisle.mistbingo;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Opens the shop when a voucher is right-clicked.
 *
 * <p>The voucher is the only shop entrance, so without this listener the item
 * would be inert; everything else (editing, prices) exists around it.
 */
public final class VoucherListener implements Listener {

    private final MistBingoPlugin plugin;

    public VoucherListener(MistBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!plugin.voucher().isVoucher(event.getItem())) {
            return;
        }

        // Never place the voucher as a block or trigger the clicked block.
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("mistbingo.shop")) {
            player.sendMessage("§c没有权限使用商店");
            return;
        }
        plugin.shop().open(player);
    }
}
