package br.vituz.core.vlogin.common.config;

/**
 * Todas as mensagens visíveis ao jogador, cada uma com o texto padrão
 * usado quando o arquivo de idioma não a define.
 */
public enum MessageKey {
    PREFIX("prefix", "&8[&bvLogin&8] &r"),

    REGISTER_REQUEST("auth.register-request", "&fCrie sua senha para entrar&7: &b/registrar <senha> <senha>"),
    REGISTER_TITLE("auth.register-title", "&b&lCRIE SUA SENHA"),
    REGISTER_SUBTITLE("auth.register-subtitle", "&7digite &f/registrar <senha> <senha>"),
    REGISTERED("auth.registered", "&aPronto! Sua senha foi criada."),
    ALREADY_REGISTERED("auth.already-registered", "&cVocê já tem uma senha criada."),
    OFFLINE_NEEDS_PASSWORD("account.offline-needs-password",
            "&cDesvincular a conta da Mojang tira a sua entrada automática.\n"
            + "&7Confirme com a sua senha&7: &b/offline <senha>"),
    REGISTER_LINKED("auth.register-linked",
            "&cEste nickname pertence a uma conta original ou Bedrock.\n"
            + "&7Entre por ela para poder criar uma senha aqui."),
    PASSWORD_MISMATCH("auth.password-mismatch", "&cAs duas senhas precisam ser iguais."),
    REGISTER_USAGE("auth.register-usage", "&cFaltou repetir a senha&7: &b/registrar <senha> <senha>"),
    REGISTER_ADDRESS_LIMIT("auth.register-address-limit",
            "&cEste endereço já tem &b{limit} &ccontas."),

    LOGIN_REQUEST("auth.login-request", "&fEntre com sua senha&7: &b/logar <senha>"),
    LOGIN_TITLE("auth.login-title", "&b&lENTRE COM SUA SENHA"),
    LOGIN_SUBTITLE("auth.login-subtitle", "&7digite &f/logar <senha>"),
    LOGGED_IN("auth.logged-in", "&aBem-vindo de volta!"),
    ALREADY_LOGGED("auth.already-logged", "&cVocê já entrou."),
    NOT_REGISTERED("auth.not-registered", "&cEste nickname ainda não tem senha criada."),
    WRONG_PASSWORD("auth.wrong-password", "&cSenha errada. &7Tentativa {attempts} de {max}."),
    LOGIN_USAGE("auth.login-usage", "&cFaltou a senha&7: &b/logar <senha>"),
    SESSION_RESUMED("auth.session-resumed", "&aReconhecemos você, pode jogar."),
    PREMIUM_AUTO("auth.premium-auto", "&aConta da Mojang confirmada, sem senha desta vez."),
    BEDROCK_AUTO("auth.bedrock-auto", "&aConta Xbox confirmada, sem senha desta vez."),
    LOGGED_OUT("auth.logged-out", "&aSaímos da sua conta. Entre de novo quando quiser."),

    PASSWORD_CHANGED("password.changed", "&aSenha trocada."),
    CHANGE_USAGE("password.change-usage", "&cInforme a senha de agora e a nova&7: &b/trocarsenha <atual> <nova>"),
    WRONG_CURRENT("password.wrong-current", "&cA senha de agora não confere."),
    TOO_SHORT("password.too-short", "&cA senha ficou curta. &7Mínimo de &b{min} &7caracteres."),
    TOO_LONG("password.too-long", "&cA senha ficou longa. &7Máximo de &b{max} &7caracteres."),
    WEAK("password.weak", "&cEssa senha é fácil demais. &7Misture maiúsculas, minúsculas, números e símbolos."),
    FORBIDDEN("password.forbidden", "&cEssa senha está entre as mais usadas do mundo. Escolha outra."),
    EQUALS_USERNAME("password.equals-username", "&cA senha não pode ser o seu próprio nickname."),

    PREMIUM_ENABLED("account.premium-enabled", "&aConta ligada à Mojang. Entre de novo para valer."),
    PREMIUM_DISABLED("account.premium-disabled", "&aConta desligada da Mojang. Volte a usar a senha."),
    PREMIUM_NOT_FOUND("account.premium-not-found", "&cA Mojang não tem nenhuma conta com este nickname."),
    ALREADY_PREMIUM("account.already-premium", "&cSua conta já está ligada à Mojang."),
    NOT_PREMIUM("account.not-premium", "&cSua conta não está ligada à Mojang."),
    PREMIUM_QUESTION("account.premium-question",
            "&7Tem conta da Mojang? &b/original &7dispensa a senha nas próximas entradas."),

    KICK_TIMEOUT("disconnect.timeout", "&cTempo esgotado. &7Entre de novo e digite a senha."),
    KICK_LOCKOUT("disconnect.lockout",
            "&cSenha errada vezes demais.\n&7Espere &b{minutes} &7minutos antes de tentar outra vez."),
    KICK_INVALID_USERNAME("disconnect.invalid-username", "&cEste nickname tem caracteres que o servidor não aceita."),
    KICK_ALREADY_ONLINE("disconnect.already-online", "&cEste nickname já está em jogo agora."),
    KICK_ADDRESS_LIMIT("disconnect.address-limit", "&cEste endereço chegou ao limite de &b{limit} &ccontas."),
    KICK_PREMIUM_ONLY("disconnect.premium-only", "&cEste nickname é de uma conta da Mojang."),
    KICK_VERIFICATION_UNAVAILABLE("disconnect.verification-unavailable",
            "&cA Mojang não respondeu para confirmar este nickname.\n&7Tente daqui a alguns minutos."),
    KICK_STORAGE_ERROR("disconnect.storage-error", "&cNão consegui checar sua conta agora. &7Tente daqui a pouco."),
    KICK_WRONG_PASSWORD("disconnect.wrong-password", "&cSenha errada."),
    NOT_AUTHENTICATED("disconnect.not-authenticated", "&cEntre com sua senha antes."),
    KICK_UNREGISTERED("disconnect.unregistered", "&cSua conta foi apagada."),

    BLOCKED_CHAT("blocked.chat", "&cSó dá para conversar depois de entrar."),
    BLOCKED_COMMAND("blocked.command", "&cSó dá para usar comandos depois de entrar."),
    BLOCKED_ACTION("blocked.action", "&cIsso só depois de entrar."),
    COUNTDOWN("blocked.countdown", "&7restam &b{seconds}s &7para entrar"),

    ADMIN_USAGE("admin.usage", "&cSubcomando desconhecido. &b/vlogin help &cmostra a lista."),
    ADMIN_HELP_HEADER("admin.help-header", "&8┌ &bvLogin &8· comandos"),
    ADMIN_HELP_ENTRY("admin.help-entry", "&8│ &b{command} &8· &7{description}"),
    ADMIN_RELOADED("admin.reloaded", "&aConfiguração relída."),
    ADMIN_NOT_FOUND("admin.not-found", "&cNão achei nenhuma conta chamada &b{player}&c."),
    ADMIN_CONSOLE_ONLY("admin.console-only", "&b{command} &csó roda pelo console. &7É proposital."),
    ADMIN_REGISTERED("admin.registered", "&aConta &b{player} &acriada."),
    ADMIN_UNREGISTERED("admin.unregistered", "&aConta &b{player} &aapagada."),
    ADMIN_PASSWORD_CHANGED("admin.password-changed", "&aSenha de &b{player} &adefinida."),
    ADMIN_UUID_CHANGED("admin.uuid-changed", "&b{player} &aagora usa o UUID &b{uuid}&a."),
    ADMIN_UUID_INVALID("admin.uuid-invalid", "&cNão entendi. Passe um UUID, ou &boffline&c, ou &bpremium&c."),
    ADMIN_ADDRESS_CLEARED("admin.address-cleared", "&aEsqueci o endereço guardado de &b{player}&a."),
    ADMIN_UNBANNED("admin.unbanned", "&b{address} &aliberado."),
    ADMIN_NOT_BLOCKED("admin.not-blocked", "&b{address} &cnão está bloqueado."),
    ADMIN_FORCED_LOGIN("admin.forced-login", "&b{player} &aentrou sem precisar da senha."),
    ADMIN_NOT_ONLINE("admin.not-online", "&b{player} &cnão está em jogo."),
    ADMIN_SPAWN_SET("admin.spawn-set", "&aMarquei este ponto para o login."),
    ADMIN_SPAWN_MISSING("admin.spawn-missing", "&cNenhum ponto marcado ainda. Fique onde quer e use &b/vlogin spawn set&c."),
    ADMIN_SPAWN_UNSUPPORTED("admin.spawn-unsupported", "&cIsso precisa de um mundo; o proxy não tem coordenadas."),
    ADMIN_PRUNE_USAGE("admin.prune-usage", "&cInforme os dias e repita com &bconfirmar&c: &b/vlogin prune 90 confirmar"),
    ADMIN_PRUNE_PREVIEW("admin.prune-preview",
            "&7Encontrei &e{count} &7contas paradas há mais de &e{days} &7dias. Nada foi apagado ainda."),
    ADMIN_PRUNED("admin.pruned", "&a{count} contas apagadas."),
    ADMIN_EXPORT_DONE("admin.export-done", "&a{count} contas gravadas em &b{file}&a."),
    ADMIN_EXPORT_FAILED("admin.export-failed", "&cNão consegui gravar o arquivo: {error}"),
    ADMIN_IMPORT_USAGE("admin.import-usage", "&cIsto copia contas de outro banco. Repita com &b/vlogin import confirmar&c."),
    ADMIN_IMPORT_START("admin.import-start", "&7Lendo &b{table} &7em &8{driver}&7, somente leitura..."),
    ADMIN_IMPORT_FOUND("admin.import-found", "&7Encontrei &e{count} &7contas na origem. Copiando..."),
    ADMIN_IMPORT_PROGRESS("admin.import-progress", "&8· &7{done} de {total}"),
    ADMIN_IMPORT_DONE("admin.import-done", "&a{imported} contas trazidas &8· &7{skipped} já existiam &8· &7{failed} com problema &8· &7{seconds}s"),
    ADMIN_IMPORT_FAILED("admin.import-failed", "&cA cópia parou: {error}"),
    ADMIN_NLOGIN_USAGE("admin.nlogin-usage", "&cIsto copia as contas do nLogin para cá, sem tocar no banco dele.\n&7Confira &bstorage.import.sql &7no config e repita com &b/vlogin nlogin confirmar&c."),
    ADMIN_BUSY("admin.busy", "&cTem outra operação rodando. Espere ela terminar."),

    INFO_HEADER("info.header", "&8┌ &b{player}"),
    INFO_ROW("info.row", "&8│ &7{label} &8· &f{value}"),
    INFO_FOOTER("info.footer", "&8└ &8{ms} ms"),
    INFO_LABEL_STATE("info.label.state", "situação"),
    INFO_LABEL_LAST_SEEN("info.label.last-seen", "visto em"),
    INFO_LABEL_CREATED("info.label.created", "criada em"),
    INFO_LABEL_UUID("info.label.uuid", "identidade"),
    INFO_LABEL_ADDRESS("info.label.address", "endereço"),
    INFO_LABEL_XBOX("info.label.xbox", "xbox"),
    INFO_LABEL_PAIRED("info.label.paired", "pareada com"),
    INFO_LABEL_NEIGHBOURS("info.label.neighbours", "mesmo endereço"),
    INFO_LABEL_SERVER("info.label.server", "jogando em"),
    INFO_STATE_ONLINE("info.state-online", "&aentrou"),
    INFO_STATE_PENDING("info.state-pending", "&eaguardando senha"),
    INFO_NEVER("info.never", "&8nunca"),
    INFO_TYPE_PREMIUM("info.type-premium", "conta original"),
    INFO_TYPE_BEDROCK("info.type-bedrock", "conta bedrock"),
    INFO_TYPE_OFFLINE("info.type-offline", "conta com senha"),
    INFO_TYPE_UNREGISTERED("info.type-unregistered", "sem cadastro"),

    DIAGNOSE_HEADER("admin.diagnose-header", "&8┌ &bvLogin &8· diagnóstico"),
    DIAGNOSE_ROW("admin.diagnose-row", "&8│ &7{name} &8· &f{value}"),

    NO_PERMISSION("general.no-permission", "&cVocê não tem permissão para isso."),
    PLAYER_ONLY("general.player-only", "&cSó quem está em jogo pode usar isso."),
    STORAGE_ERROR("general.storage-error", "&cAlgo falhou aqui dentro. Avise quem administra o servidor."),
    COOLDOWN("general.cooldown", "&cCalma, espere um instante.");

    private final String path;
    private final String fallback;

    MessageKey(String path, String fallback) {
        this.path = path;
        this.fallback = fallback;
    }

    public String path() {
        return path;
    }

    public String fallback() {
        return fallback;
    }
}
