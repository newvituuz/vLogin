package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.auth.AuthSession;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.model.Account;
import br.vituz.core.vlogin.common.platform.AuthPlayer;

import java.util.Optional;

public final class OfflineCommand extends PlayerCommand {
    public OfflineCommand(VLoginCore core) {
        super(core, "offline", "Marca a conta como offline.", "vlogin.command.offline",
                "offline", "pirata", "cracked");
    }

    @Override
    protected void run(AuthPlayer player, String[] args) {
        Optional<AuthSession> session = core.auth().session(player);
        if (!session.isPresent() || !session.get().isAuthenticated()) {
            player.sendMessage(core.messages().get(MessageKey.NOT_AUTHENTICATED));
            return;
        }
        Account account = session.get().account();
        if (account == null) {
            player.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
            return;
        }
        if (!account.isPremium()) {
            player.sendMessage(core.messages().get(MessageKey.NOT_PREMIUM));
            return;
        }
        if (!account.isRegistered()) {
            player.sendMessage(core.messages().get(MessageKey.REGISTER_REQUEST));
            return;
        }
        // Estar logado não prova que é o dono: numa sessão retomada, ou com a senha
        // vazada, quem está aqui pode ser outro. Desvincular derruba a entrada
        // automática do dono de vez, então vale pedir a senha de novo.
        if (args.length == 0 || !core.hashing().verify(args[0], account.password())) {
            player.sendMessage(core.messages().get(MessageKey.OFFLINE_NEEDS_PASSWORD));
            return;
        }

        account.mojangId(null);
        core.auth().saveAsync(account);
        core.mojang().invalidate(player.name());
        player.sendMessage(core.messages().get(MessageKey.PREMIUM_DISABLED));
    }
}
