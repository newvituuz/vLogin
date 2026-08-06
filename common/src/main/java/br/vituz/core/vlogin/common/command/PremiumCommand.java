package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.model.Account;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.premium.MojangService;

import java.util.Optional;

public final class PremiumCommand extends PlayerCommand {
    public PremiumCommand(VLoginCore core) {
        super(core, "premium", "Marca a conta como original.", "original", "premium");
    }

    @Override
    protected void run(AuthPlayer player, String[] args) {
        Optional<br.vituz.core.vlogin.common.auth.AuthSession> session = core.auth().session(player);
        if (!session.isPresent() || !session.get().isAuthenticated()) {
            player.sendMessage(core.messages().get(MessageKey.NOT_AUTHENTICATED));
            return;
        }
        Account account = session.get().account();
        if (account == null) {
            player.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
            return;
        }
        if (account.isPremium()) {
            player.sendMessage(core.messages().get(MessageKey.ALREADY_PREMIUM));
            return;
        }

        core.platform().scheduler().async(() -> {
            MojangService.Result result = core.mojang().lookup(player.name());
            if (!result.isPremium()) {
                player.sendMessage(core.messages().get(MessageKey.PREMIUM_NOT_FOUND));
                return;
            }
            core.auth().setPremium(account, result.uniqueId);
            player.sendMessage(core.messages().get(MessageKey.PREMIUM_ENABLED));
            core.platform().scheduler().player(player, () ->
                    player.kick(core.messages().plain(MessageKey.PREMIUM_ENABLED)));
        });
    }
}
