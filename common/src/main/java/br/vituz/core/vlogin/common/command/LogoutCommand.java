package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.platform.AuthPlayer;

public final class LogoutCommand extends PlayerCommand {
    public LogoutCommand(VLoginCore core) {
        super(core, "logout", "Encerra a sessao atual.", "deslogar", "logout", "sair");
    }

    @Override
    protected void run(AuthPlayer player, String[] args) {
        core.auth().logout(player);
    }
}
