package br.vituz.core.vlogin.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Recusa toda conexão enquanto o vLogin não estiver rodando.
 */
public final class LockdownListener {
    private final Component reason;

    public LockdownListener(String detail) {
        this.reason = Component.text("Servidor em manutenção: o sistema de login não está disponível.")
                .color(NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text(detail).color(NamedTextColor.GRAY));
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(reason));
    }
}
