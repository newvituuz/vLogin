package br.vituz.core.vlogin.common.auth;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.bedrock.BedrockIdentity;
import br.vituz.core.vlogin.common.config.MessageKey;
import br.vituz.core.vlogin.common.config.Settings;
import br.vituz.core.vlogin.common.model.Account;
import br.vituz.core.vlogin.common.model.AccountSettings;
import br.vituz.core.vlogin.common.platform.AuthPlayer;
import br.vituz.core.vlogin.common.platform.Platform;
import br.vituz.core.vlogin.common.platform.Scheduler;
import br.vituz.core.vlogin.common.platform.SoundEffect;
import br.vituz.core.vlogin.common.premium.MojangService;
import br.vituz.core.vlogin.common.proxy.ProxyMessaging;
import br.vituz.core.vlogin.common.storage.StorageException;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * A máquina de estados da autenticação: quem está conectado, quem provou
 * quem é, e o que acontece com quem ainda não provou.
 */
public final class AuthManager {
    private static final long COMMAND_COOLDOWN_MILLIS = 250L;
    private static final int REQUEST_REMINDER_SECONDS = 10;
    private static final java.util.regex.Pattern SAFE_NAME =
            java.util.regex.Pattern.compile("[A-Za-z0-9_.-]{1,32}");

    private final VLoginCore core;
    private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, PendingLogin> pending = new ConcurrentHashMap<>();

    private static final long PENDING_TTL_MILLIS = 60_000L;

    private static final class PendingLogin {
        final Account account;
        final boolean premiumVerified;
        final MojangService.Result mojangResult;
        final long createdAt = System.currentTimeMillis();

        PendingLogin(Account account, boolean premiumVerified, MojangService.Result mojangResult) {
            this.account = account;
            this.premiumVerified = premiumVerified;
            this.mojangResult = mojangResult;
        }
    }

    private Scheduler.Cancellable timerTask;

    public AuthManager(VLoginCore core) {
        this.core = core;
    }

    public enum AuthReason {
        PASSWORD, REGISTRATION, SESSION, PREMIUM, BEDROCK, PROXY, ADMIN
    }

    public static final class PreLoginResult {
        public final boolean allowed;
        public final String kickMessage;
        public final boolean forceOnlineMode;

        private PreLoginResult(boolean allowed, String kickMessage, boolean forceOnlineMode) {
            this.allowed = allowed;
            this.kickMessage = kickMessage;
            this.forceOnlineMode = forceOnlineMode;
        }

        static PreLoginResult allow(boolean forceOnlineMode) {
            return new PreLoginResult(true, null, forceOnlineMode);
        }

        static PreLoginResult deny(String message) {
            return new PreLoginResult(false, message, false);
        }
    }

    public void start() {
        timerTask = core.platform().scheduler().repeatAsync(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        sessions.clear();
        pending.clear();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public Optional<AuthSession> session(String name) {
        return Optional.ofNullable(sessions.get(key(name)));
    }

    public Optional<AuthSession> session(AuthPlayer player) {
        return session(player.name());
    }

    public boolean isAuthenticated(String name) {
        AuthSession session = sessions.get(key(name));
        return session != null && session.isAuthenticated();
    }

    public boolean isAuthenticated(AuthPlayer player) {
        return isAuthenticated(player.name());
    }

    public Collection<AuthSession> sessions() {
        return sessions.values();
    }

    public boolean isUnrestricted(String name) {
        return core.settings().exemptUsernames.contains(key(name));
    }

    public PreLoginResult checkPreLogin(String name, String address) {
        Settings settings = core.settings();

        if (isUnrestricted(name)) {
            return PreLoginResult.allow(false);
        }

        if (!isUsernameAcceptable(name, settings)) {
            return PreLoginResult.deny(core.messages().plain(MessageKey.KICK_INVALID_USERNAME));
        }

        if (core.bruteForce().isBlocked(address)) {
            return PreLoginResult.deny(core.messages().plain(MessageKey.KICK_LOCKOUT,
                    "minutes", core.bruteForce().remainingMinutes(address)));
        }

        Account account;
        try {
            account = core.storage().findByName(name).orElse(null);
        } catch (StorageException ex) {
            core.logger().log(Level.SEVERE, "Could not load the account for " + name, ex);
            return PreLoginResult.deny(core.messages().plain(MessageKey.KICK_STORAGE_ERROR));
        }

        AuthSession current = sessions.get(key(name));
        if (current != null && current.player().isOnline()) {
            boolean sameAddress = settings.sameAddressRelogin
                    && address.equals(current.address());
            if (!sameAddress) {
                return PreLoginResult.deny(core.messages().plain(MessageKey.KICK_ALREADY_ONLINE));
            }
            current.player().kick(core.messages().plain(MessageKey.KICK_ALREADY_ONLINE));
        } else if (core.bus().isShared()) {
            Optional<br.vituz.core.vlogin.common.network.NetworkBus.Presence> elsewhere =
                    core.bus().presence(name);
            if (elsewhere.isPresent() && !elsewhere.get().nodeId.equals(core.bus().nodeId())) {
                boolean sameAddress = settings.sameAddressRelogin
                        && address.equals(elsewhere.get().address);
                if (!sameAddress) {
                    return PreLoginResult.deny(core.messages().plain(MessageKey.KICK_ALREADY_ONLINE));
                }
                core.bus().publish(br.vituz.core.vlogin.common.network.NetworkMessage.Action.KICK, name,
                        core.messages().plain(MessageKey.KICK_ALREADY_ONLINE));
            }
        }

        boolean isPremiumName = false;
        MojangService.Result mojangResult = null;

        if (account != null && account.isPremium()) {
            isPremiumName = true;
        } else if (account == null || !account.isRegistered() || hasOpenPremiumClaim(account)) {
            boolean verifies = !core.bedrock().looksBedrock(name)
                    && (settings.premiumMode == Settings.PremiumMode.VERIFY
                    || settings.premiumMode == Settings.PremiumMode.EXCLUSIVE);
            if (verifies) {
                mojangResult = core.mojang().lookup(name);
                if (mojangResult.status == MojangService.Status.UNKNOWN) {
                    warnMojangUnreachable(name);
                    return PreLoginResult.deny(
                            core.messages().plain(MessageKey.KICK_VERIFICATION_UNAVAILABLE));
                }
                if (mojangResult.isPremium()) {
                    isPremiumName = true;

                    // A identidade de uma conta original é o UUID da Mojang, não o
                    // nickname: quem troca de nome continua sendo a mesma pessoa, e
                    // quem compra um nome largado não herda a conta de quem o usava.
                    if (mojangResult.uniqueId != null) {
                        try {
                            Account byMojang = core.storage()
                                    .findByMojangId(mojangResult.uniqueId).orElse(null);
                            if (byMojang != null) {
                                byMojang.lastName(name);
                                account = byMojang;
                            }
                        } catch (StorageException ex) {
                            core.logger().log(Level.WARNING,
                                    "Falha ao buscar conta pelo mojangId de " + name, ex);
                        }
                    }
                }
            }
        }

        // O modo exclusive protege o nickname de quem tem conta na Mojang, e não
        // fecha o servidor para quem não tem: nickname sem conta segue para o
        // registro normalmente. A recusa só faz sentido quando o nickname é de
        // alguém e esta plataforma não consegue exigir a comprovação, porque aí
        // deixar entrar seria entregar o nickname a qualquer um que o digite.
        if (isPremiumName && settings.premiumMode == Settings.PremiumMode.EXCLUSIVE
                && !core.platform().canVerifyMojang()) {
            return PreLoginResult.deny(core.messages().plain(MessageKey.KICK_PREMIUM_ONLY));
        }

        if (settings.addressLimitEnabled) {
            String denial = checkAddressLimit(address, account, settings);
            if (denial != null) {
                return PreLoginResult.deny(denial);
            }
        }

        boolean forceOnlineMode = isPremiumName && settings.premiumAutoLogin;
        boolean premiumVerified = forceOnlineMode && core.platform().type().isProxy();
        pending.put(key(name), new PendingLogin(account, premiumVerified, mojangResult));
        return PreLoginResult.allow(forceOnlineMode);
    }

    /**
     * Se vale pedir a comprovação da conta na Mojang para este nickname.
     *
     * É consultado antes de existir jogador, direto da conexão, e por isso nunca
     * lança: qualquer dúvida responde não e a pessoa segue pelo caminho da senha.
     * Quem já tem conta com senha aqui e não é original também responde não, senão
     * o dono do nickname na Mojang tomaria a conta de quem registrou antes.
     */
    public boolean shouldVerifyWithMojang(String name) {
        try {
            Settings settings = core.settings();
            if (settings.premiumMode == Settings.PremiumMode.OFF || !settings.premiumAutoLogin) {
                return false;
            }
            if (name == null || !isUsernameAcceptable(name, settings)
                    || isUnrestricted(name) || core.bedrock().looksBedrock(name)) {
                return false;
            }

            Account account = core.storage().findByName(name).orElse(null);
            if (account != null && account.isPremium()) {
                return true;
            }
            if (account != null && account.isRegistered()) {
                return false;
            }
            MojangService.Result result = core.mojang().lookup(name);
            if (!result.isPremium()) {
                return false;
            }
            // Troca de nick: não achou pelo nome, mas a Mojang confirmou.
            // Se já existe conta com esse mojangId, é o dono legítimo com nick novo.
            if (account == null && result.uniqueId != null) {
                Account byMojang = core.storage().findByMojangId(result.uniqueId).orElse(null);
                if (byMojang != null) {
                    return true;
                }
            }
            return true;
        } catch (StorageException ex) {
            core.logger().log(Level.WARNING, "Não deu para checar a conta de " + name
                    + " antes do login; seguindo pelo caminho da senha.", ex);
            return false;
        } catch (RuntimeException ex) {
            core.logger().log(Level.WARNING, "Falha ao decidir a verificação de " + name
                    + "; seguindo pelo caminho da senha.", ex);
            return false;
        }
    }

    private volatile long lastMojangWarning;

    /**
     * Avisa no console quando alguém é recusado porque a Mojang não respondeu.
     *
     * Sem isto o operador vê jogadores caindo com uma mensagem sobre a Mojang e nada
     * no log. A causa real (sem internet, DNS bloqueado, firewall de saída, ou a
     * Mojang mesmo fora do ar) fica invisível. Uma vez por minuto basta para
     * diagnosticar sem encher o log durante uma queda longa.
     */
    private void warnMojangUnreachable(String name) {
        long now = System.currentTimeMillis();
        if (now - lastMojangWarning < 60_000L) {
            return;
        }
        lastMojangWarning = now;
        core.logger().warning("A Mojang não respondeu ao verificar '" + name + "', e a entrada foi"
                + " recusada em vez de tratada como offline. Enquanto isso durar, nickname novo não"
                + " entra. Se este servidor não tem acesso à internet, use accounts.premium.mode: off.");
    }

    private String checkAddressLimit(String address, Account account, Settings settings) {
        if (settings.addressAllowlist.contains(address)) {
            return null;
        }
        if (account != null) {
            if (!settings.addressLimitBlockLogin) {
                return null;
            }
            boolean bypass = (settings.addressLimitExemptRegistered && account.isRegistered())
                    || (settings.addressLimitExemptPremium && account.isPremium())
                    || (settings.addressLimitExemptBedrock && account.isBedrock());
            if (bypass) {
                return null;
            }
        }
        try {
            int registered = core.storage().countByAddress(address);
            if (registered >= settings.addressLimitMax) {
                return core.messages().plain(MessageKey.KICK_ADDRESS_LIMIT, "limit", settings.addressLimitMax);
            }
        } catch (StorageException ex) {
            core.logger().log(Level.WARNING, "Could not count accounts for " + address, ex);
        }
        return null;
    }

    public void handleJoin(AuthPlayer player) {
        String name = player.name();
        if (isUnrestricted(name)) {
            return;
        }
        PendingLogin prepared = pending.remove(key(name));
        if (prepared != null) {
            setUpSession(player, prepared.account, prepared.premiumVerified, prepared.mojangResult);
            return;
        }
        core.platform().scheduler().async(() -> {
            // Saiu enquanto o banco respondia.
            if (!player.isOnline()) {
                return;
            }
            Account account;
            try {
                account = core.storage().findByName(name).orElse(null);
            } catch (StorageException ex) {
                core.logger().log(Level.SEVERE, "Não foi possível carregar a conta de " + name
                        + "; a conexão será recusada em vez de tratada como conta nova.", ex);
                core.platform().scheduler().player(player,
                        () -> player.kick(core.messages().plain(MessageKey.KICK_STORAGE_ERROR)));
                return;
            }
            setUpSession(player, account, false, null);
        });
    }

    private void setUpSession(AuthPlayer player, Account account, boolean premiumVerified, MojangService.Result mojangResult) {
        UUID mojangUuid = null;
        if (account != null && account.mojangId() != null) {
            mojangUuid = account.mojangId();
        } else if (mojangResult != null && mojangResult.isPremium()) {
            mojangUuid = mojangResult.uniqueId;
        } else if (!core.bedrock().looksBedrock(player.name()) && core.settings().premiumMode != Settings.PremiumMode.OFF) {
            MojangService.Result lookupResult = mojangResult != null ? mojangResult : core.mojang().lookup(player.name());
            if (lookupResult != null && lookupResult.isPremium()) {
                mojangUuid = lookupResult.uniqueId;
            }
        }

        // Só conta como verificado o que a conexão provou: o proxy que forçou online
        // mode, o próprio servidor em online mode, ou o handshake que o vLogin fez
        // com a Mojang nesta conexão. Comparar o UUID recebido não prova nada: num
        // backend com forwarding legado do Bungee, que não é assinado, quem conecta
        // direto escolhe o UUID que quiser.
        boolean isVerified = premiumVerified
                || core.platform().enforcesOnlineMode()
                || core.platform().isMojangVerified(player);

        // Nickname bate mas o UUID não: a conta guardada é de outra pessoa, que
        // trocou de nome na Mojang e largou este. Quem chegou agora é o dono atual
        // do nickname, então entra, mas numa conta própria e vazia. A antiga solta
        // o nickname e continua encontrável pelo mojangId, que é a identidade real.
        if (isVerified && account != null && account.mojangId() != null
                && !account.mojangId().equals(player.uniqueId())) {
            releaseName(account, player);
            account = null;
            // O mojangUuid acima veio da conta que acabou de sair de cena. Deixá-lo
            // gravaria o UUID do antigo dono na conta nova, e a busca por identidade
            // passaria a devolver a pessoa errada.
            mojangUuid = player.uniqueId();
        }

        AuthSession session = new AuthSession(player, account);
        session.premiumVerified(isVerified);
        sessions.put(key(player.name()), session);
        core.bus().markOnline(player.name(), session.address());

        // Vincula só o que não tem dono declarado aqui. Uma conta com senha pertence a
        // quem a registrou, e gravar nela o UUID de quem chega com o mesmo nickname
        // faria a conexão seguinte entrar direto: o bloqueio do login automático não
        // adiantaria nada se o vínculo fosse criado assim mesmo. Quem tem senha e é
        // dono da conta original usa /original para pedir o vínculo.
        if (isVerified && account != null && account.mojangId() == null
                && (!account.isRegistered() || hasOpenPremiumClaim(account))) {
            account.mojangId(player.uniqueId());
            account.settings().setLong(AccountSettings.PREMIUM_CLAIM_AT, 0L);
            saveAsync(account);
        }

        AuthReason autoLogin = resolveAutoLogin(session);
        if (autoLogin != null) {
            if (session.account() == null) {
                createPasswordlessAccount(session, autoLogin, mojangUuid);
            } else {
                authenticate(session, autoLogin);
            }
            return;
        }

        core.platform().scheduler().player(player, () -> core.platform().setRestricted(player, true));
        promptAuthentication(session);
    }

    private void createPasswordlessAccount(AuthSession session, AuthReason reason, UUID mojangUuid) {
        AuthPlayer player = session.player();
        core.platform().scheduler().async(() -> {
            // Antes de criar, procura pelo UUID: quem trocou de nickname na Mojang é a
            // mesma pessoa, e criar de novo daria duas contas com a mesma identidade.
            if (reason == AuthReason.PREMIUM) {
                UUID identity = mojangUuid != null ? mojangUuid : player.uniqueId();
                Account existing = findByMojangIdQuietly(identity);
                if (existing != null) {
                    existing.lastName(player.name());
                    session.account(existing);
                    authenticate(session, reason);
                    return;
                }
            }

            Account account = Account.create(player.name(), player.uniqueId(), session.address());
            if (reason == AuthReason.PREMIUM) {
                account.mojangId(mojangUuid != null ? mojangUuid : player.uniqueId());
            } else if (reason == AuthReason.BEDROCK) {
                applyBedrockIdentity(account, player);
            }
            try {
                core.storage().insert(account);
            } catch (StorageException ex) {
                core.logger().log(Level.SEVERE, "Could not create the account of " + player.name(), ex);
                core.platform().scheduler().player(player, () ->
                        player.kick(core.messages().plain(MessageKey.KICK_STORAGE_ERROR)));
                return;
            }
            session.account(account);
            authenticate(session, reason);
        });
    }

    private Account findByMojangIdQuietly(UUID mojangId) {
        if (mojangId == null) {
            return null;
        }
        try {
            return core.storage().findByMojangId(mojangId).orElse(null);
        } catch (StorageException ex) {
            core.logger().log(Level.WARNING, "Falha ao procurar a conta de " + mojangId, ex);
            return null;
        }
    }

    /**
     * Tira o nickname de uma conta cujo dono já o trocou na Mojang.
     *
     * A conta não é apagada nem perde nada: só deixa de responder por um nome que
     * não é mais dela. Quando o dono voltar, a busca pelo mojangId acha a conta e
     * grava o nickname novo.
     */
    private void releaseName(Account account, AuthPlayer newcomer) {
        String previous = account.lastName();
        String freed = previous + "." + account.mojangId().toString().substring(0, 8);
        account.lastName(freed);
        core.logger().warning("O nickname '" + previous + "' agora pertence a "
                + newcomer.uniqueId() + " na Mojang. A conta de " + account.mojangId()
                + " passou a se chamar '" + freed + "' e mantém tudo o que tinha;"
                + " ela volta ao nome novo assim que o dono entrar.");
        saveAsync(account);
    }

    private AuthReason resolveAutoLogin(AuthSession session) {
        Settings settings = core.settings();
        Account account = session.account();
        AuthPlayer player = session.player();
        boolean registered = account != null && account.isRegistered();
        boolean premiumConnection = session.isPremiumVerified();

        // Num backend, o reconhecimento de Bedrock sai do UUID que chegou na conexão,
        // e quem alcança o backend por fora do proxy escolhe o UUID que quiser. Com o
        // forwarding legado do Bungee, que não é assinado, isso daria entrada sem senha
        // em qualquer nickname. Lá quem autentica é o proxy, e a decisão dele chega
        // assinada com o secret.key.
        boolean decidesLocally = !settings.isBackendBehindProxy();

        if (decidesLocally && settings.bedrockAutoLogin && isBedrockPlayer(player, account)
                && (registered || settings.bedrockSkipPassword)
                && allowsBedrockAutoLogin(player, settings)) {
            return AuthReason.BEDROCK;
        }

        // Só a conexão prova ser original. O UUID que chega junto com ela não serve:
        // quem conecta escolhe o que manda, e conferir contra o UUID guardado apenas
        // compara a alegação com ela mesma.
        //
        // Quando a conta já tem mojangId gravado, o UUID da sessão DEVE bater. Sem
        // isso, trocar o nickname da conta Mojang para o de outra pessoa daria acesso
        // à conta dela — o cenário clássico de nickname swap via NameMC.
        boolean provenPremium = premiumConnection;
        if (settings.premiumAutoLogin && provenPremium
                && (registered || settings.premiumSkipPassword)) {
            if (account != null && account.mojangId() != null
                    && !account.mojangId().equals(player.uniqueId())) {
                return null;
            }
            // Conta registrada aqui e sem vínculo é de quem criou a senha, não de quem
            // hoje tem o nickname na Mojang. Vincular exige entrar e usar /original.
            if (account != null && account.mojangId() == null && account.isRegistered()) {
                return null;
            }
            session.premiumAutologin(true);
            return AuthReason.PREMIUM;
        }

        if (!registered) {
            return null;
        }

        if (settings.sessionEnabled && hasValidSession(player, account)) {
            return AuthReason.SESSION;
        }
        return null;
    }

    /**
     * Valida o nickname.
     *
     * Gamertags do Bedrock não passam pelo padrão dos jogadores Java: chegam com o
     * prefixo do Floodgate e podem ter caracteres que o padrão Java não aceita. Sem
     * um padrão próprio, todo jogador Bedrock seria recusado antes mesmo de entrar.
     */
    private boolean isUsernameAcceptable(String name, Settings settings) {
        if (core.bedrock().looksBedrock(name)) {
            return settings.bedrockUsernamePattern == null
                    || settings.bedrockUsernamePattern.matcher(name).matches();
        }
        return settings.usernamePattern == null || settings.usernamePattern.matcher(name).matches();
    }

    /**
     * Se esta conexão é de um jogador Bedrock.
     *
     * Com o Floodgate presente a resposta vem dele, que já autenticou a conta Xbox.
     * Sem ele resta o formato do UUID, ou o vínculo já gravado na própria conta.
     */
    private boolean isBedrockPlayer(AuthPlayer player, Account account) {
        if (core.bedrock().isBedrock(player.uniqueId())) {
            return true;
        }
        return account != null && account.bedrockId() != null
                && account.bedrockId().equals(player.uniqueId());
    }

    /**
     * Se o login automático de Bedrock vale para esta conexão.
     *
     * Com {@code require-linked}, só passa quem tem conta Java vinculada no
     * Floodgate; os demais seguem pelo caminho normal, com senha.
     */
    /**
     * Grava na conta o que se sabe da identidade Bedrock.
     *
     * Com {@code use-database-uuid} ligado, o UUID já guardado é preservado: quem
     * migrou de outro plugin costuma ter inventário, economia e permissões presos ao
     * UUID antigo, e trocá-lo perderia tudo isso. O vínculo Bedrock é gravado de
     * qualquer forma, porque é ele que reconhece o jogador na próxima entrada.
     */
    private void applyBedrockIdentity(Account account, AuthPlayer player) {
        account.bedrockId(player.uniqueId());

        if (!core.settings().bedrockKeepStoredUuid || account.uniqueId() == null) {
            account.uniqueId(player.uniqueId());
        }

        core.bedrock().identify(player.uniqueId()).ifPresent(identity -> {
            if (identity.xuid() != null) {
                account.settings().setString(AccountSettings.BEDROCK_XUID, identity.xuid());
            }
            if (identity.gamertag() != null) {
                account.settings().setString(AccountSettings.BEDROCK_GAMERTAG, identity.gamertag());
            }
        });
    }

    private boolean allowsBedrockAutoLogin(AuthPlayer player, Settings settings) {
        if (!settings.bedrockRequireLinked) {
            return true;
        }
        Optional<BedrockIdentity> identity = core.bedrock().identify(player.uniqueId());
        return identity.isPresent() && identity.get().isLinked();
    }

    private boolean hasValidSession(AuthPlayer player, Account account) {
        Settings settings = core.settings();
        long duration = TimeUnit.MINUTES.toMillis(settings.sessionMinutes);
        if (duration <= 0) {
            return false;
        }

        Optional<String> busAddress = core.bus().session(player.name());
        if (busAddress.isPresent()) {
            return !settings.sessionBindAddress || busAddress.get().equals(player.address());
        }

        long startedAt = account.settings().getLong(AccountSettings.SESSION_STARTED, 0L);
        String address = account.settings().getString(AccountSettings.SESSION_ADDRESS, null);
        if (startedAt <= 0 || System.currentTimeMillis() - startedAt >= duration) {
            return false;
        }
        return !settings.sessionBindAddress || (address != null && address.equals(player.address()));
    }

    private void promptAuthentication(AuthSession session) {
        AuthPlayer player = session.player();
        Settings settings = core.settings();
        boolean registered = session.isRegistered();

        MessageKey request = registered ? MessageKey.LOGIN_REQUEST : MessageKey.REGISTER_REQUEST;
        MessageKey title = registered ? MessageKey.LOGIN_TITLE : MessageKey.REGISTER_TITLE;
        MessageKey subtitle = registered ? MessageKey.LOGIN_SUBTITLE : MessageKey.REGISTER_SUBTITLE;

        player.sendMessage(core.messages().get(request));
        if (settings.showTitle) {
            player.sendTitle(core.messages().plain(title), core.messages().plain(subtitle), 10, 100, 10);
        }
        if (settings.playSounds) {
            player.playSound(SoundEffect.NOTIFY);
        }
    }

    public void handleQuit(AuthPlayer player) {
        AuthSession session = sessions.remove(key(player.name()));
        pending.remove(key(player.name()));
        if (session == null) {
            return;
        }
        core.bus().clearOnline(player.name());
        if (session.isAuthenticated() && core.settings().sessionEnabled) {
            openResumableSession(session);
        }
    }

    private void openResumableSession(AuthSession session) {
        long duration = TimeUnit.MINUTES.toMillis(core.settings().sessionMinutes);
        if (duration <= 0) {
            return;
        }
        core.bus().openSession(session.player().name(), session.address(), duration);

        Account account = session.account();
        if (account == null) {
            return;
        }
        account.settings().setLong(AccountSettings.SESSION_STARTED, System.currentTimeMillis());
        account.settings().setString(AccountSettings.SESSION_ADDRESS, session.address());
        account.lastSeen(System.currentTimeMillis());
        saveAsync(account);
    }

    public void login(AuthPlayer player, String password) {
        AuthSession session = sessions.get(key(player.name()));
        if (session == null) {
            return;
        }
        if (session.isAuthenticated()) {
            player.sendMessage(core.messages().get(MessageKey.ALREADY_LOGGED));
            return;
        }
        if (!session.tryCommand(COMMAND_COOLDOWN_MILLIS)) {
            player.sendMessage(core.messages().get(MessageKey.COOLDOWN));
            return;
        }
        if (!session.isRegistered()) {
            player.sendMessage(core.messages().get(MessageKey.NOT_REGISTERED));
            return;
        }

        core.platform().scheduler().async(() -> {
            Account account = session.account();
            if (!core.hashing().verify(password, account.password())) {
                handleFailedLogin(session);
                return;
            }

            if (core.hashing().needsRehash(account.password())) {
                account.password(core.hashing().hash(password));
            }
            authenticate(session, AuthReason.PASSWORD);
        });
    }

    private void handleFailedLogin(AuthSession session) {
        AuthPlayer player = session.player();
        Settings settings = core.settings();

        int attempts = session.recordFailure();
        int global = core.bruteForce().recordFailure(session.address());

        if (settings.playSounds) {
            player.playSound(SoundEffect.FAILURE);
        }

        if (attempts >= settings.maxAttempts || global >= settings.maxAttempts) {
            String reason = core.bruteForce().isBlocked(session.address())
                    ? core.messages().plain(MessageKey.KICK_LOCKOUT,
                    "minutes", core.bruteForce().remainingMinutes(session.address()))
                    : core.messages().plain(MessageKey.KICK_WRONG_PASSWORD);
            core.platform().scheduler().player(player, () -> player.kick(reason));
            return;
        }

        player.sendMessage(core.messages().get(MessageKey.WRONG_PASSWORD,
                "tries", attempts, "max", settings.maxAttempts));
    }

    /** Se a conta pertence a uma identidade de fora: uma conta da Mojang ou do Xbox. */
    private boolean isLinkedAccount(Account account) {
        return account.isPremium() || account.isBedrock();
    }

    /**
     * Se esta conexão comprovou ser dona da conta ligada.
     *
     * Vale a prova que o transporte deu nesta entrada, e não o que a conta guarda:
     * o vínculo diz de quem a conta é, não quem está digitando agora.
     */
    private boolean provedOwnership(AuthSession session, Account account) {
        if (account.isPremium() && session.isPremiumVerified()) {
            return true;
        }
        return account.isBedrock() && isBedrockPlayer(session.player(), account);
    }

    public void register(AuthPlayer player, String password, String confirmation) {
        AuthSession session = sessions.get(key(player.name()));
        if (session == null) {
            return;
        }
        if (session.isAuthenticated() || session.isRegistered()) {
            player.sendMessage(core.messages().get(MessageKey.ALREADY_REGISTERED));
            return;
        }
        if (!session.tryCommand(COMMAND_COOLDOWN_MILLIS)) {
            player.sendMessage(core.messages().get(MessageKey.COOLDOWN));
            return;
        }
        if (confirmation != null && !password.equals(confirmation)) {
            player.sendMessage(core.messages().get(MessageKey.PASSWORD_MISMATCH));
            return;
        }

        String rejection = validatePassword(player.name(), password);
        if (rejection != null) {
            player.sendMessage(rejection);
            return;
        }

        core.platform().scheduler().async(() -> registerNow(player, password, session));
    }

    /**
     * Travas por endereço, para conferir o limite e gravar sem alguém entrar no meio.
     *
     * São poucas e fixas, escolhidas pelo hash do endereço: um mapa de travas por IP
     * cresceria sem limite, que é o problema que a trava veio evitar. Dois endereços
     * podem cair na mesma trava, e o custo disso é só um registro esperar o outro.
     */
    private final Object[] addressLocks = new Object[64];

    {
        for (int i = 0; i < addressLocks.length; i++) {
            addressLocks[i] = new Object();
        }
    }

    private Object addressLock(String address) {
        return addressLocks[Math.floorMod(String.valueOf(address).hashCode(), addressLocks.length)];
    }

    private void registerNow(AuthPlayer player, String password, AuthSession session) {
        // Contar e inserir precisam ser um passo só. Separados, vários registros do
        // mesmo endereço leem a mesma contagem antes de qualquer um gravar, e todos
        // passam por um limite que deveria ter barrado a partir do segundo.
        synchronized (addressLock(session.address())) {
            Settings settings = core.settings();
            if (settings.addressLimitEnabled && !settings.addressAllowlist.contains(session.address())) {
                try {
                    if (core.storage().countByAddress(session.address()) >= settings.addressLimitMax) {
                        player.sendMessage(core.messages().get(MessageKey.REGISTER_ADDRESS_LIMIT,
                                "limit", settings.addressLimitMax));
                        return;
                    }
                } catch (StorageException ex) {
                    core.logger().log(Level.WARNING, "Could not verify the address limit", ex);
                }
            }

            Account account = session.account();
            try {
                Account current = core.storage().findByName(player.name()).orElse(null);
                if (current != null && current.isRegistered()) {
                    session.account(current);
                    player.sendMessage(core.messages().get(MessageKey.ALREADY_REGISTERED));
                    return;
                }
                // Uma conta ligada à Mojang ou ao Xbox tem dono mesmo sem senha: é assim
                // que o login automático a cria. Deixar registrar aqui entregaria a conta
                // a quem só digitou o nickname, em qualquer momento em que a comprovação
                // não estiver disponível. Quem provou ser o dono nesta conexão pode.
                if (current != null && isLinkedAccount(current)
                        && !provedOwnership(session, current)) {
                    session.account(current);
                    player.sendMessage(core.messages().get(MessageKey.REGISTER_LINKED));
                    return;
                }
                if (current != null) {
                    account = current;
                }
            } catch (StorageException ex) {
                core.logger().log(Level.SEVERE, "Não foi possível confirmar se "
                        + player.name() + " já existe; registro abortado.", ex);
                player.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
                return;
            }

            boolean isNew = account == null;
            if (isNew) {
                account = Account.create(player.name(), player.uniqueId(), session.address());
            }
            account.lastName(player.name());
            account.password(core.hashing().hash(password));
            account.lastIp(session.address());
            account.lastSeen(System.currentTimeMillis());
            if (core.bedrock().isBedrock(player.uniqueId())) {
                applyBedrockIdentity(account, player);
            }

            try {
                if (isNew) {
                    core.storage().insert(account);
                } else {
                    core.storage().update(account);
                }
            } catch (StorageException ex) {
                core.logger().log(Level.SEVERE, "Could not register " + player.name(), ex);
                player.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
                return;
            }

            session.account(account);
            // Quem avisa é o authenticate abaixo, pela AuthReason.REGISTRATION.
            runCommands(core.settings().onRegister, player);
            authenticate(session, AuthReason.REGISTRATION);
        }
    }

    public String validatePassword(String playerName, String password) {
        Settings settings = core.settings();
        if (password.length() < settings.passwordMinLength) {
            return core.messages().get(MessageKey.TOO_SHORT, "min", settings.passwordMinLength);
        }
        if (password.length() > settings.passwordMaxLength) {
            return core.messages().get(MessageKey.TOO_LONG, "max", settings.passwordMaxLength);
        }
        if (password.equalsIgnoreCase(playerName)) {
            return core.messages().get(MessageKey.EQUALS_USERNAME);
        }
        if (settings.forbiddenPasswords.contains(password.toLowerCase(Locale.ROOT))) {
            return core.messages().get(MessageKey.FORBIDDEN);
        }
        if (settings.strengthEnabled && settings.strengthPattern != null
                && !settings.strengthPattern.matcher(password).matches()) {
            return core.messages().get(MessageKey.WEAK);
        }
        return null;
    }

    public void changePassword(AuthPlayer player, String current, String replacement) {
        AuthSession session = sessions.get(key(player.name()));
        if (session == null || !session.isAuthenticated()) {
            player.sendMessage(core.messages().get(MessageKey.NOT_AUTHENTICATED));
            return;
        }
        String rejection = validatePassword(player.name(), replacement);
        if (rejection != null) {
            player.sendMessage(rejection);
            return;
        }

        core.platform().scheduler().async(() -> {
            Account account = session.account();
            if (account == null || !core.hashing().verify(current, account.password())) {
                player.sendMessage(core.messages().get(MessageKey.WRONG_CURRENT));
                return;
            }
            account.password(core.hashing().hash(replacement));
            try {
                core.storage().update(account);
            } catch (StorageException ex) {
                core.logger().log(Level.SEVERE, "Could not change the password of " + player.name(), ex);
                player.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
                return;
            }
            player.sendMessage(core.messages().get(MessageKey.PASSWORD_CHANGED));
        });
    }

    public void unregister(AuthPlayer player, String password) {
        AuthSession session = sessions.get(key(player.name()));
        if (session == null || !session.isAuthenticated()) {
            player.sendMessage(core.messages().get(MessageKey.NOT_AUTHENTICATED));
            return;
        }
        core.platform().scheduler().async(() -> {
            Account account = session.account();
            if (account == null || !core.hashing().verify(password, account.password())) {
                player.sendMessage(core.messages().get(MessageKey.WRONG_CURRENT));
                return;
            }
            try {
                core.storage().delete(account.lastName());
            } catch (StorageException ex) {
                core.logger().log(Level.SEVERE, "Could not unregister " + player.name(), ex);
                player.sendMessage(core.messages().get(MessageKey.STORAGE_ERROR));
                return;
            }
            core.bus().clearSession(player.name());
            player.sendMessage(core.messages().get(MessageKey.KICK_UNREGISTERED));
            core.platform().scheduler().player(player, () ->
                    player.kick(core.messages().plain(MessageKey.KICK_UNREGISTERED)));
        });
    }

    public void logout(AuthPlayer player) {
        AuthSession session = sessions.get(key(player.name()));
        if (session == null || !session.isAuthenticated()) {
            player.sendMessage(core.messages().get(MessageKey.NOT_AUTHENTICATED));
            return;
        }
        session.authenticated(false);
        core.bus().clearSession(player.name());

        Account account = session.account();
        if (account != null) {
            account.settings().setLong(AccountSettings.SESSION_STARTED, 0L);
            account.settings().setString(AccountSettings.SESSION_ADDRESS, null);
            saveAsync(account);
        }

        core.platform().scheduler().player(player, () -> core.platform().setRestricted(player, true));
        notifyProxy(player, ProxyMessaging.Action.LOGOUT);
        core.bus().publish(br.vituz.core.vlogin.common.network.NetworkMessage.Action.LOGOUT, player.name(), "");
        player.sendMessage(core.messages().get(MessageKey.LOGGED_OUT));
        promptAuthentication(session);
    }

    public void applyNetworkMessage(br.vituz.core.vlogin.common.network.NetworkMessage message) {
        switch (message.action) {
            case KICK: {
                AuthSession session = sessions.get(key(message.player));
                if (session != null && session.player().isOnline()) {
                    AuthPlayer player = session.player();
                    String reason = message.extra.isEmpty()
                            ? core.messages().plain(MessageKey.KICK_ALREADY_ONLINE)
                            : message.extra;
                    core.platform().scheduler().player(player, () -> player.kick(reason));
                }
                break;
            }
            case LOGOUT: {
                AuthSession session = sessions.get(key(message.player));
                if (session != null && session.isAuthenticated()) {
                    session.authenticated(false);
                    AuthPlayer player = session.player();
                    core.platform().scheduler().player(player,
                            () -> core.platform().setRestricted(player, true));
                    promptAuthentication(session);
                }
                break;
            }
            case ACCOUNT_CHANGED: {
                pending.remove(key(message.player));
                AuthSession session = sessions.get(key(message.player));
                if (session != null) {
                    reloadAccount(session);
                }
                break;
            }
            case LOGIN:
            case RELOAD:
            default:
                break;
        }
    }

    private void reloadAccount(AuthSession session) {
        core.platform().scheduler().async(() -> {
            try {
                core.storage().findByName(session.player().name()).ifPresent(session::account);
            } catch (StorageException ex) {
                core.logger().log(Level.FINE, "Não foi possível recarregar a conta", ex);
            }
        });
    }

    public void authenticate(AuthSession session, AuthReason reason) {
        if (!session.markAuthenticated()) {
            return;
        }
        AuthPlayer player = session.player();
        Settings settings = core.settings();

        Account account = session.account();
        if (account != null) {
            account.lastIp(session.address());
            account.lastSeen(System.currentTimeMillis());
            account.lastName(player.name());
            if (settings.sessionEnabled) {
                account.settings().setLong(AccountSettings.SESSION_STARTED, System.currentTimeMillis());
                account.settings().setString(AccountSettings.SESSION_ADDRESS, session.address());
            }
            if (reason == AuthReason.PREMIUM && account.mojangId() == null) {
                account.mojangId(player.uniqueId());
            }
            saveAsync(account);
        }

        if (settings.sessionEnabled) {
            core.bus().openSession(player.name(), session.address(),
                    TimeUnit.MINUTES.toMillis(settings.sessionMinutes));
        }

        core.platform().scheduler().player(player, () -> {
            core.platform().setRestricted(player, false);
            if (settings.showTitle) {
                player.clearTitle();
            }
            player.sendMessage(core.messages().get(messageFor(reason)));
            if (settings.playSounds) {
                player.playSound(SoundEffect.SUCCESS);
            }
        });

        notifyProxy(player, ProxyMessaging.Action.LOGIN);
        announceAuthentication(player);
        core.bus().publish(br.vituz.core.vlogin.common.network.NetworkMessage.Action.LOGIN, player.name(), "");

        if (reason != AuthReason.REGISTRATION) {
            runCommands(settings.onLogin, player);
        }
        redirectAfterAuth(player);
        maybeAskAboutPremium(session);
    }

    private MessageKey messageFor(AuthReason reason) {
        switch (reason) {
            case SESSION:
                return MessageKey.SESSION_RESUMED;
            case PREMIUM:
                return MessageKey.PREMIUM_AUTO;
            case BEDROCK:
                return MessageKey.BEDROCK_AUTO;
            case REGISTRATION:
                return MessageKey.REGISTERED;
            default:
                return MessageKey.LOGGED_IN;
        }
    }

    private void maybeAskAboutPremium(AuthSession session) {
        Settings settings = core.settings();
        Account account = session.account();
        if (!settings.premiumAskOnLogin || account == null || account.isPremium()) {
            return;
        }
        if (account.settings().getBoolean(AccountSettings.PREMIUM_QUESTION_ANSWERED, false)) {
            return;
        }
        core.platform().scheduler().async(() -> {
            if (core.mojang().lookup(session.player().name()).isPremium()) {
                session.player().sendMessage(core.messages().get(MessageKey.PREMIUM_QUESTION));
            }
        });
    }

    private void redirectAfterAuth(AuthPlayer player) {
        Settings settings = core.settings();
        if (!core.platform().type().isProxy() || !settings.postLoginRedirect
                || settings.postLoginServers.isEmpty()) {
            return;
        }
        String target = settings.postLoginServers
                .get((int) (Math.random() * settings.postLoginServers.size()));
        core.platform().scheduler().laterSync(() -> core.platform().transfer(player, target),
                settings.postLoginDelayMillis, TimeUnit.MILLISECONDS);
    }

    private void notifyProxy(AuthPlayer player, ProxyMessaging.Action action) {
        if (core.platform().type().isProxy()) {
            return;
        }
        byte[] payload = core.proxyMessaging().encode(action, player.name(), "");
        core.platform().sendPluginMessage(player, ProxyMessaging.CHANNEL, payload);
    }

    public void announceAuthentication(AuthPlayer player) {
        if (!core.platform().type().isProxy() || !isAuthenticated(player.name())) {
            return;
        }
        byte[] payload = core.proxyMessaging()
                .encode(ProxyMessaging.Action.AUTHENTICATED, player.name(), "");
        core.platform().sendPluginMessage(player, ProxyMessaging.CHANNEL, payload);
    }

    public void applyProxyAuthentication(String playerName) {
        AuthSession session = sessions.get(key(playerName));
        if (session != null) {
            authenticate(session, AuthReason.PROXY);
        }
    }

    public void runCommands(java.util.List<String> commands, AuthPlayer player) {
        for (String raw : commands) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String command = raw.trim();
            boolean console = false;
            boolean proxy = false;
            long delayTicks = 0;
            String serverFilter = null;

            while (command.startsWith("@")) {
                int space = command.indexOf(' ');
                if (space == -1) {
                    break;
                }
                String directive = command.substring(1, space).toLowerCase(Locale.ROOT);
                String remainder = command.substring(space + 1).trim();

                if (directive.equals("console")) {
                    console = true;
                    command = remainder;
                } else if (directive.equals("proxy")) {
                    proxy = true;
                    command = remainder;
                } else if (directive.equals("delay")) {
                    int nextSpace = remainder.indexOf(' ');
                    if (nextSpace == -1) {
                        break;
                    }
                    try {
                        delayTicks = Long.parseLong(remainder.substring(0, nextSpace));
                    } catch (NumberFormatException ignored) {
                        delayTicks = 0;
                    }
                    command = remainder.substring(nextSpace + 1).trim();
                } else if (directive.equals("server")) {
                    int nextSpace = remainder.indexOf(' ');
                    if (nextSpace == -1) {
                        break;
                    }
                    serverFilter = remainder.substring(0, nextSpace);
                    command = remainder.substring(nextSpace + 1).trim();
                } else {
                    break;
                }
            }

            if (serverFilter != null && !matchesServer(player, serverFilter)) {
                continue;
            }
            if (proxy && !core.platform().type().isProxy()) {
                core.platform().sendPluginMessage(player, ProxyMessaging.CHANNEL,
                        core.proxyMessaging().encode(ProxyMessaging.Action.COMMAND, player.name(), command));
                continue;
            }

            if (!SAFE_NAME.matcher(player.name()).matches()) {
                core.logger().warning("Comando ignorado: o nickname '" + player.name()
                        + "' tem caracteres que não podem ser inseridos em um comando.");
                continue;
            }

            String finalCommand = command.replace("@player", player.name())
                    .replace("{player}", player.name());
            if (finalCommand.startsWith("/")) {
                finalCommand = finalCommand.substring(1);
            }

            boolean asConsole = console;
            String toRun = finalCommand;
            Runnable task = () -> {
                if (asConsole) {
                    core.platform().dispatchConsoleCommand(toRun);
                } else {
                    core.platform().dispatchPlayerCommand(player, toRun);
                }
            };
            if (delayTicks > 0) {
                core.platform().scheduler().laterSync(task, delayTicks * 50L, TimeUnit.MILLISECONDS);
            } else {
                core.platform().scheduler().sync(task);
            }
        }
    }

    private boolean matchesServer(AuthPlayer player, String filter) {
        Optional<String> server = core.platform().serverOf(player);
        if (!server.isPresent()) {
            return false;
        }
        for (String candidate : filter.split(",")) {
            if (candidate.trim().equalsIgnoreCase(server.get())) {
                return true;
            }
        }
        return false;
    }

    private void tick() {
        Settings settings = core.settings();
        Platform platform = core.platform();
        long now = System.currentTimeMillis();

        for (AuthSession session : sessions.values()) {
            AuthPlayer player = session.player();
            if (!player.isOnline()) {
                sessions.remove(key(player.name()));
                continue;
            }
            if (session.isAuthenticated()) {
                continue;
            }

            int remaining = session.remainingSeconds(settings.loginTimeoutSeconds);
            if (settings.loginTimeoutSeconds > 0 && remaining <= 0) {
                String reason = core.messages().plain(MessageKey.KICK_TIMEOUT);
                platform.scheduler().player(player, () -> player.kick(reason));
                continue;
            }

            if (settings.showActionBar && settings.showCountdown && settings.loginTimeoutSeconds > 0) {
                player.sendActionBar(core.messages().plain(MessageKey.COUNTDOWN,
                        "seconds", remaining));
            }

            if (session.shouldRemind(now, TimeUnit.SECONDS.toMillis(REQUEST_REMINDER_SECONDS))) {
                player.sendMessage(core.messages().get(session.isRegistered()
                        ? MessageKey.LOGIN_REQUEST : MessageKey.REGISTER_REQUEST));
            }
        }

        core.bruteForce().purgeExpired();
        pending.entrySet().removeIf(entry -> now - entry.getValue().createdAt > PENDING_TTL_MILLIS);
    }

    public void saveAsync(Account account) {
        core.platform().scheduler().async(() -> {
            try {
                core.storage().update(account);
            } catch (StorageException ex) {
                core.logger().log(Level.WARNING, "Could not save the account " + account.lastName(), ex);
            }
        });
    }

    public void setPremium(Account account, UUID mojangId) {
        account.mojangId(mojangId);
        saveAsync(account);
    }

    /** Janela em que o pedido de vínculo vale. Vencido, a conta volta ao normal. */
    private static final long PREMIUM_CLAIM_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);

    public void requestPremiumClaim(Account account) {
        account.settings().setLong(AccountSettings.PREMIUM_CLAIM_AT, System.currentTimeMillis());
        saveAsync(account);
    }

    /**
     * Se a conta pediu o vínculo há pouco e ainda não comprovou.
     *
     * Enquanto o pedido vale, a entrada dessa conta passa a exigir a comprovação na
     * Mojang. Quem pediu sem ser o dono não consegue entrar durante a janela, e
     * depois dela a conta volta a aceitar a senha normalmente.
     */
    private boolean hasOpenPremiumClaim(Account account) {
        if (account == null) {
            return false;
        }
        long claimedAt = account.settings().getLong(AccountSettings.PREMIUM_CLAIM_AT, 0L);
        return claimedAt > 0 && System.currentTimeMillis() - claimedAt < PREMIUM_CLAIM_TTL_MILLIS;
    }
}
