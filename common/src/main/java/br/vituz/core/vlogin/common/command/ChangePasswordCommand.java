package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.platform.AuthPlayer;

public final class ChangePasswordCommand extends PlayerCommand {
    public ChangePasswordCommand(VLoginCore core) {
        super(core, "changepassword", "Altera a propria senha.", null, "trocarsenha", "mudarsenha", "changepassword");
    }

    @Override
    protected void run(AuthPlayer player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(core.messages().get(MessageKey.CHANGE_USAGE));
            return;
        }
        core.auth().changePassword(player, args[0], args[1]);
    }
}
