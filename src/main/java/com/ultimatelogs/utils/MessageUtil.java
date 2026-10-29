package com.ultimatelogs.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static Component parse(String text) {
        if (text == null) return Component.empty();
        return MM.deserialize(text);
    }

    public static void send(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isBlank()) {
            sender.sendMessage(parse(message));
        }
    }
}
