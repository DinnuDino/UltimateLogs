package com.ultimatelogs.serialization;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class InventorySerializer {

    public String serializeItemArray(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return "";
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (ItemStack item : items) {
                if (item == null || item.getType() == Material.AIR) {
                    baos.write(new byte[]{0, 0, 0, 0});
                    continue;
                }
                byte[] bytes = item.serializeAsBytes();
                int len = bytes.length;
                baos.write((len >> 24) & 0xFF);
                baos.write((len >> 16) & 0xFF);
                baos.write((len >> 8) & 0xFF);
                baos.write(len & 0xFF);
                baos.write(bytes);
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public ItemStack[] deserializeItemArray(String base64) {
        if (base64 == null || base64.isBlank()) {
            return new ItemStack[0];
        }
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            List<ItemStack> items = new ArrayList<>();
            while (bais.available() >= 4) {
                int len = ((bais.read() & 0xFF) << 24)
                        | ((bais.read() & 0xFF) << 16)
                        | ((bais.read() & 0xFF) << 8)
                        | (bais.read() & 0xFF);
                if (len == 0) {
                    items.add(new ItemStack(Material.AIR));
                    continue;
                }
                byte[] itemBytes = new byte[len];
                int read = bais.read(itemBytes);
                if (read == len) {
                    items.add(ItemStack.deserializeBytes(itemBytes));
                } else {
                    items.add(new ItemStack(Material.AIR));
                }
            }
            return items.toArray(new ItemStack[0]);
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }

    public String serializeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "";
        }
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Exception e) {
            return "";
        }
    }

    public ItemStack deserializeItem(String base64) {
        if (base64 == null || base64.isBlank()) {
            return new ItemStack(Material.AIR);
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
        } catch (Exception e) {
            return new ItemStack(Material.AIR);
        }
    }
}
