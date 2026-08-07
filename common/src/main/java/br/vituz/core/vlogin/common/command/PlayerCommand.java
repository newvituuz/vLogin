package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.platform.Sender;

import java.util.List;

abstract class PlayerCommand extends Command {
    protected final VLoginCore core;

    PlayerCommand(VLoginCore core, String key, String description, String permission,
                  String... defaultAliases) {
        this(core, core.settings().aliases(key, defaultAliases), description, permission);
    }

    private PlayerCommand(VLoginCore core, List<String> aliases, String description,
                          String permission) {
        super(aliases.get(0), aliases, description, permission);
        this.core = core;
    }

    @Override
    public final void execute(Sender sender, String[] args) {
        if (!(sender instanceof AuthPlayer)) {
            sender.sendMessage(core.messages().get(MessageKey.PLAYER_ONLY));
            return;
        }
        // A permissão declarada no descritor não era conferida em lugar nenhum:
        // quem a removesse continuava podendo usar o comando.
        if (permission() != null && !sender.hasPermission(permission())) {
            sender.sendMessage(core.messages().get(MessageKey.NO_PERMISSION));
            return;
        }
        run((AuthPlayer) sender, args);
    }

    protected abstract void run(AuthPlayer player, String[] args);
}
