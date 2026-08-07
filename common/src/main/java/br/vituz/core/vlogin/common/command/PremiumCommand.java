package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.model.Account;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.premium.MojangService;

import java.util.Optional;

public final class PremiumCommand extends PlayerCommand {
    private static final long COOLDOWN_MILLIS = 3000L;

    public PremiumCommand(VLoginCore core) {
        super(core, "premium", "Marca a conta como original.", "vlogin.command.premium",
                "original", "premium");
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
        // Sem isto, uma macro dispara consultas à Mojang em rajada.
        if (!session.get().tryCommand(COOLDOWN_MILLIS)) {
            player.sendMessage(core.messages().get(MessageKey.COOLDOWN));
            return;
        }

        // Existir uma conta na Mojang com este nickname não prova que ela é de quem
        // está digitando. Aqui a conta só fica marcada como candidata; o vínculo é
        // gravado na entrada seguinte, quando a conexão comprovar a conta.
        core.platform().scheduler().async(() -> {
            MojangService.Result result = core.mojang().lookup(player.name());
            if (!result.isPremium()) {
                player.sendMessage(core.messages().get(MessageKey.PREMIUM_NOT_FOUND));
                return;
            }
            core.auth().requestPremiumClaim(account);
            core.platform().scheduler().player(player, () ->
                    player.kick(core.messages().plain(MessageKey.PREMIUM_CLAIM_OPENED)));
        });
    }
}
