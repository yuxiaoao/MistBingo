package net.starryisle.mistbingo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/** Base64 round trip for ItemStacks, so NBT survives a config reload. */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(stack);
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("无法序列化物品", exception);
        }
    }

    public static ItemStack decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        byte[] data = Base64.getDecoder().decode(encoded);
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            return (ItemStack) in.readObject();
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("无法读取物品", exception);
        }
    }
}
