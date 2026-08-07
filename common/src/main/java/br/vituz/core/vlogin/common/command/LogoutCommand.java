package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.auth.AuthSession;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.platform.AuthPlayer;

import java.util.Optional;

public final class LogoutCommand extends PlayerCommand {
    private static final long COOLDOWN_MILLIS = 1000L;

    public LogoutCommand(VLoginCore core) {
        super(core, "logout", "Encerra a sessao atual.", null, "deslogar", "logout", "sair");
    }

    @Override
    protected void run(AuthPlayer player, String[] args) {
        // Cada logout publica no barramento da rede; sem cooldown, uma macro inunda.
        Optional<AuthSession> session = core.auth().session(player);
        if (session.isPresent() && !session.get().tryCommand(COOLDOWN_MILLIS)) {
            player.sendMessage(core.messages().get(MessageKey.COOLDOWN));
            return;
        }
        core.auth().logout(player);
    }
}
