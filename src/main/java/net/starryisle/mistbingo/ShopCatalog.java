package net.starryisle.mistbingo;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

/**
 * Builds the shop catalog: one shulker box per colour, filled with concrete and
 * with stained glass, so players buy in bulk instead of single blocks.
 */
public final class ShopCatalog {

    /** Stone types players usually throw away, sold in shulker boxes. */
    private static final String[] JUNK_STONES = {
            "GRANITE", "DIORITE", "ANDESITE", "TUFF", "CALCITE", "BASALT",
    };

    private final MistBingoPlugin plugin;

    public ShopCatalog(MistBingoPlugin plugin) {
        this.plugin = plugin;
    }

    /** 16 concrete boxes, 16 stained-glass boxes, then the six bulk stones. */
    public List<ShopEntry> build(int price) {
        price = Math.min(ShopService.MAX_PRICE, Math.max(1, price));
        List<ShopEntry> entries = new ArrayList<>();

        for (DyeColor color : DyeColor.values()) {
            Material concrete = Material.matchMaterial(color.name() + "_CONCRETE");
            if (concrete != null) {
                entries.add(box(concrete, price));
            }
        }
        for (DyeColor color : DyeColor.values()) {
            Material glass = Material.matchMaterial(color.name() + "_STAINED_GLASS");
            if (glass != null) {
                entries.add(box(glass, price));
            }
        }
        for (String name : JUNK_STONES) {
            Material stone = Material.matchMaterial(name);
            if (stone != null) {
                entries.add(box(stone, price));
            }
        }
        entries.addAll(woodBoxes(price));
        return entries;
    }

    /** Every plain log and stem, discovered from the material list so new woods appear. */
    private List<ShopEntry> woodBoxes(int price) {
        List<Material> woods = new ArrayList<>();
        for (Material material : Material.values()) {
            String name = material.name();
            if (material.isLegacy() || !material.isItem()) {
                continue;
            }
            if (name.startsWith("STRIPPED_")) {
                continue;
            }
            if (name.endsWith("_LOG") || name.endsWith("_STEM")) {
                woods.add(material);
            }
        }
        woods.sort(java.util.Comparator.comparing(Enum::name));

        List<ShopEntry> entries = new ArrayList<>();
        for (Material wood : woods) {
            entries.add(box(wood, price));
        }
        return entries;
    }

    private ShopEntry box(Material content, int price) {
        String name = plugin.names().of(content);
        return new ShopEntry(filledBox(content, name), price);
    }

    /** A shulker box holding 27 full stacks of the given block, named after it. */
    private ItemStack filledBox(Material content, String contentName) {
        ItemStack box = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        if (meta == null) {
            return box;
        }

        int perStack = content.getMaxStackSize();
        ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
        for (int slot = 0; slot < 27; slot++) {
            shulker.getInventory().setItem(slot, new ItemStack(content, perStack));
        }
        meta.setBlockState(shulker);

        meta.setDisplayName("§f" + contentName + "潜影盒");
        meta.setLore(List.of(
                "§7内含 §f27 §7组" + contentName,
                "§7共 §f" + (27 * perStack) + " §7个",
                "",
                "§7购买后直接拿到整盒"));
        box.setItemMeta(meta);
        return box;
    }
}
