package br.vituz.core.vlogin.bukkit.listener;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Recusa toda conexão enquanto o vLogin não estiver rodando.
 *
 * Desabilitar o plugin e seguir aceitando jogadores deixaria todas as
 * contas abertas. Recusar é barulhento de propósito.
 */
public final class LockdownListener implements Listener {
    private final String reason;

    public LockdownListener(String detail) {
        this.reason = ChatColor.RED + "Servidor em manutenção: o sistema de login não está disponível."
                + "\n" + ChatColor.GRAY + detail;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, reason);
    }
}
