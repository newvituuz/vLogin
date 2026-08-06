package br.vituz.core.vlogin.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

/**
 * Recusa toda conexão enquanto o vLogin não estiver rodando.
 */
public final class LockdownListener implements Listener {
    private final String reason;

    public LockdownListener(String detail) {
        this.reason = ChatColor.RED + "Servidor em manutenção: o sistema de login não está disponível."
                + "\n" + ChatColor.GRAY + detail;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(PreLoginEvent event) {
        event.setCancelled(true);
        event.setCancelReason(TextComponent.fromLegacyText(reason));
    }
}
