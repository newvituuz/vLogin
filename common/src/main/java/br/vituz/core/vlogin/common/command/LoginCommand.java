package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.platform.AuthPlayer;

public final class LoginCommand extends PlayerCommand {
    public LoginCommand(VLoginCore core) {
        super(core, "login", "Autentica no servidor.", null, "logar", "login", "log");
    }

    @Override
    protected void run(AuthPlayer player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(core.messages().get(MessageKey.LOGIN_USAGE));
            return;
        }
        core.auth().login(player, args[0]);
    }

    @Override
    public boolean allowedBeforeAuth() {
        return true;
    }
}
