package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.platform.AuthPlayer;

public final class RegisterCommand extends PlayerCommand {
    public RegisterCommand(VLoginCore core) {
        super(core, "register", "Cria o registro da conta.", "registrar", "register", "reg");
    }

    @Override
    protected void run(AuthPlayer player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(core.messages().get(MessageKey.REGISTER_USAGE));
            return;
        }
        core.auth().register(player, args[0], args[1]);
    }

    @Override
    public boolean allowedBeforeAuth() {
        return true;
    }
}
