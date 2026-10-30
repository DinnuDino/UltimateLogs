package com.ultimatelogs.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ItemBuilder {

    public static final NamespacedKey GUI_BUTTON_KEY = new NamespacedKey("ultimatelogs", "gui_button");
    private static final NamespacedKey LEGACY_ACTION_KEY = new NamespacedKey("ultimatelogs", "gui_action");

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material != null ? material : Material.STONE);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item != null ? item.clone() : new ItemStack(Material.STONE);
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder name(String miniMessageName) {
        if (meta != null) {
            meta.displayName(MessageUtil.parse(miniMessageName));
        }
        return this;
    }

    public ItemBuilder lore(List<String> miniMessageLore) {
        if (meta != null && miniMessageLore != null) {
            List<Component> components = new ArrayList<>();
            for (String line : miniMessageLore) {
                components.add(MessageUtil.parse(line));
            }
            meta.lore(components);
        }
        return this;
    }

    public ItemBuilder addLore(String line) {
        if (meta != null && line != null) {
            List<Component> current = meta.lore();
            if (current == null) current = new ArrayList<>();
            else current = new ArrayList<>(current);
            current.add(MessageUtil.parse(line));
            meta.lore(current);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null && flags != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder hideAllFlags() {
        if (meta != null) {
            meta.addItemFlags(
                    ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                    ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_DESTROYS,
                    ItemFlag.HIDE_PLACED_ON
            );
        }
        return this;
    }

    public ItemBuilder skullOwner(String playerName) {
        if (meta instanceof SkullMeta skullMeta && playerName != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            skullMeta.setOwningPlayer(offlinePlayer);
        }
        return this;
    }

    public ItemBuilder asGuiButton() {
        if (meta != null) {
            meta.getPersistentDataContainer().set(GUI_BUTTON_KEY, PersistentDataType.BYTE, (byte) 1);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack pane(Material material) {
        return new ItemBuilder(material).name(" ").asGuiButton().hideAllFlags().build();
    }

    public static boolean isGuiButton(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // 1. Explicit PersistentDataContainer tag (primary and legacy keys)
        if (meta.getPersistentDataContainer().has(GUI_BUTTON_KEY, PersistentDataType.BYTE) ||
            meta.getPersistentDataContainer().has(LEGACY_ACTION_KEY, PersistentDataType.STRING)) {
            return true;
        }

        // 2. GUI Stained Glass Panes (check for custom empty or placeholder slot name)
        Material type = item.getType();
        if (type.name().endsWith("_STAINED_GLASS_PANE")) {
            if (meta.hasDisplayName()) {
                Component comp = meta.displayName();
                if (comp != null) {
                    String name = PlainTextComponentSerializer.plainText().serialize(comp).trim().toLowerCase(Locale.ROOT);
                    if (name.isEmpty() || name.contains("slot")) {
                        return true;
                    }
                }
            } else if (meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES) || meta.hasItemFlag(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)) {
                return true;
            }
        }

        // 3. Fallback check for GUI bottles (empty archives)
        if (type == Material.GLASS_BOTTLE && meta.hasDisplayName()) {
            Component comp = meta.displayName();
            if (comp != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(comp).toLowerCase(Locale.ROOT);
                if (plain.contains("no ender chest snapshots") ||
                    plain.contains("no inventory snapshots") ||
                    plain.contains("no position records") ||
                    plain.contains("no deaths recorded") ||
                    plain.contains("no log records") ||
                    plain.contains("no audit logs")) {
                    return true;
                }
            }
        }

        // 4. Fallback check for previously spawned GUI buttons (e.g. from user screenshot)
        if (meta.hasDisplayName()) {
            Component displayName = meta.displayName();
            if (displayName != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(displayName).toLowerCase(Locale.ROOT);
                if (plain.contains("live ender chest") ||
                    plain.contains("ender chest time") ||
                    plain.contains("real-time live inspection") ||
                    plain.contains("snapshot time") ||
                    plain.contains("return / back") ||
                    plain.contains("switch to inventory") ||
                    plain.contains("quick switch menu") ||
                    plain.contains("view death history") ||
                    plain.contains("browse echest archive") ||
                    plain.contains("browse snapshot archive") ||
                    plain.contains("enchantment levels") ||
                    plain.contains("close menu") ||
                    plain.contains("empty helmet slot") ||
                    plain.contains("empty chestplate slot") ||
                    plain.contains("empty leggings slot") ||
                    plain.contains("empty boots slot") ||
                    plain.contains("empty offhand slot") ||
                    plain.contains("live active inspection") ||
                    plain.contains("latest log record") ||
                    plain.contains("type time in chat") ||
                    plain.contains("back to players") ||
                    plain.contains("back to categories") ||
                    plain.contains("select target player") ||
                    plain.contains("previous page") ||
                    plain.contains("next page") ||
                    plain.contains("inventory audit & recovery") ||
                    plain.contains("ender chest audit & history") ||
                    plain.contains("coordinate trail") ||
                    plain.contains("death investigation archive") ||
                    plain.contains("item transactions & drops") ||
                    plain.contains("sessions & block actions") ||
                    plain.contains("coordinate breadcrumbs archive") ||
                    plain.contains("no ender chest snapshots") ||
                    plain.contains("echest snapshot") ||
                    plain.contains("no inventory snapshots") ||
                    plain.contains("no position records") ||
                    plain.contains("no deaths recorded") ||
                    plain.contains("no log records") ||
                    plain.contains("no audit logs") ||
                    plain.contains("type custom / offline player") ||
                    plain.contains("investigation hub") ||
                    plain.contains("audit trail") ||
                    plain.contains("snapshot archive")) {
                    return true;
                }
            }
        }

        if (meta.hasLore()) {
            List<Component> lore = meta.lore();
            if (lore != null) {
                for (Component comp : lore) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(comp).toLowerCase(Locale.ROOT);
                    if (plain.contains("taking items: enabled") ||
                        plain.contains("placing items: enabled") ||
                        plain.contains("placing items: blocked") ||
                        plain.contains("live 10-tick sync") ||
                        plain.contains("click to go back") ||
                        plain.contains("click to open inventory") ||
                        plain.contains("click to view deaths") ||
                        plain.contains("click to browse snapshots") ||
                        plain.contains("click to select") ||
                        plain.contains("click to return") ||
                        plain.contains("click to inspect") ||
                        plain.contains("click to query") ||
                        plain.contains("click to teleport") ||
                        plain.contains("teleport to location") ||
                        plain.contains("teleport to death location") ||
                        plain.contains("historical snapshot")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void cleanPlayerInventory(Player player) {
        if (player == null || !player.isOnline()) return;
        boolean modified = false;

        // 1. Storage contents (slots 0-35)
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && isGuiButton(contents[i])) {
                contents[i] = null;
                modified = true;
            }
        }
        if (modified) {
            player.getInventory().setContents(contents);
        }

        // 2. Offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && isGuiButton(offhand)) {
            player.getInventory().setItemInOffHand(null);
            modified = true;
        }

        // 3. Armor contents
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean armorModified = false;
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && isGuiButton(armor[i])) {
                armor[i] = null;
                armorModified = true;
            }
        }
        if (armorModified) {
            player.getInventory().setArmorContents(armor);
            modified = true;
        }

        // 4. Item on cursor
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && isGuiButton(cursor)) {
            player.setItemOnCursor(null);
            modified = true;
        }

        if (modified) {
            player.updateInventory();
        }
    }
}
