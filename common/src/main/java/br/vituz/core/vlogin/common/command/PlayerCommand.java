package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.platform.Sender;

import java.util.List;

abstract class PlayerCommand extends Command {
    protected final VLoginCore core;

    PlayerCommand(VLoginCore core, String key, String description, String... defaultAliases) {
        this(core, core.settings().aliases(key, defaultAliases), description);
    }

    private PlayerCommand(VLoginCore core, List<String> aliases, String description) {
        super(aliases.get(0), aliases, description, null);
        this.core = core;
    }

    @Override
    public final void execute(Sender sender, String[] args) {
        if (!(sender instanceof AuthPlayer)) {
            sender.sendMessage(core.messages().get(MessageKey.PLAYER_ONLY));
            return;
        }
        run((AuthPlayer) sender, args);
    }

    protected abstract void run(AuthPlayer player, String[] args);
}
