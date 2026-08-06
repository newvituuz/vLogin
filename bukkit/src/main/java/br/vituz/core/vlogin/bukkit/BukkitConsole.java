package br.vituz.core.vlogin.bukkit;

import br.vituz.core.vlogin.common.platform.Sender;
import org.bukkit.command.CommandSender;

public final class BukkitConsole implements Sender {
    private final CommandSender sender;

    public BukkitConsole(CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public String name() {
        return sender.getName();
    }

    @Override
    public void sendMessage(String message) {
        sender.sendMessage(message);
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public boolean isPlayer() {
        return false;
    }
}
