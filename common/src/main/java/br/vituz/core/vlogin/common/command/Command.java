package br.vituz.core.vlogin.common.command;

import br.vituz.core.vlogin.common.platform.Sender;

import java.util.Collections;
import java.util.List;

/**
 * Um comando escrito uma vez e registrado na plataforma que estiver rodando.
 */
public abstract class Command {
    private final String name;
    private final List<String> aliases;
    private final String description;
    private final String permission;

    protected Command(String name, List<String> aliases, String description, String permission) {
        this.name = name;
        this.aliases = aliases;
        this.description = description;
        this.permission = permission;
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public String description() {
        return description;
    }

    public String permission() {
        return permission;
    }

    public abstract void execute(Sender sender, String[] args);

    public List<String> suggest(Sender sender, String[] args) {
        return Collections.emptyList();
    }

    public boolean allowedBeforeAuth() {
        return false;
    }
}
