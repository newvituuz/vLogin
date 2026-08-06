package br.vituz.core.vlogin.bukkit.premium;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.util.UUIDs;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * O login premium num Paper sozinho, sem proxy.
 *
 * Num servidor offline o Bukkit não oferece jeito de autenticar um jogador na
 * Mojang: quando o AsyncPlayerPreLoginEvent dispara, a fase de login já acabou. A
 * decisão de criptografar acontece antes disso, na pipeline Netty, e é lá que este
 * handler entra: ele segura o pacote de entrada, pergunta se o nickname é de uma
 * conta original e, se for, coloca o login no mesmo trilho de um servidor online
 * mode. O servidor faz o resto sozinho, inclusive trazer a skin.
 *
 * Nada aqui pode abrir uma conta por engano. Toda falha, de reflexão a rede, cai
 * no caminho normal: o jogador entra offline e o vLogin pede a senha.
 */
public final class PremiumLogin {
    private static final String HANDLER = "vlogin_login_watcher";
    private static final long VERIFICATION_TTL_MILLIS = 120_000L;

    private final VLoginCore core;
    private final Map<String, Long> verified = new ConcurrentHashMap<>();

    private final Acceptor acceptor = new Acceptor();
    private final Preparer preparer = new Preparer();

    private PublicKey publicKey;
    private List<?> channels;
    private boolean installed;

    public PremiumLogin(VLoginCore core) {
        this.core = core;
    }

    /**
     * @return o motivo de não ter instalado, ou null quando instalou
     */
    public String install() {
        if (Bukkit.getOnlineMode()) {
            return "o servidor já está em online mode";
        }
        if (behindProxy()) {
            return "este servidor está atrás de um proxy, que é quem autentica";
        }

        try {
            Object server = NmsLogin.minecraftServer();
            this.publicKey = NmsLogin.publicKey(server);
            this.channels = NmsLogin.listeningChannels(server);

            synchronized (channels) {
                for (Object entry : channels) {
                    ((ChannelFuture) entry).channel().pipeline().addFirst(acceptor);
                }
            }
            installed = true;
            return null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            core.logger().log(Level.WARNING, "Não consegui preparar o login de contas originais"
                    + " neste servidor; todo mundo vai entrar com senha.", ex);
            return "não foi possível acessar a fase de login desta versão";
        }
    }

    public void uninstall() {
        if (!installed || channels == null) {
            return;
        }
        installed = false;
        try {
            synchronized (channels) {
                for (Object entry : channels) {
                    Channel channel = ((ChannelFuture) entry).channel();
                    channel.eventLoop().execute(() -> {
                        if (channel.pipeline().get(Acceptor.class) != null) {
                            channel.pipeline().remove(acceptor);
                        }
                    });
                }
            }
        } catch (RuntimeException ex) {
            core.logger().log(Level.FINE, "Falha ao remover o handler de login: " + ex.getMessage());
        }
        verified.clear();
    }

    /**
     * Se esta conexão comprovou a conta na Mojang.
     *
     * Duas coisas precisam bater: termos pedido a comprovação para este endereço há
     * pouco, e o jogador ter chegado com um UUID que não é o offline. Num servidor
     * offline o UUID só deixa de ser o offline quando o handshake com a Mojang foi
     * até o fim, então quem não passou por ele não consegue fingir que passou.
     */
    public boolean isVerified(String name, UUID uniqueId, String address) {
        Long requestedAt = verified.remove(key(name, address));
        if (requestedAt == null || System.currentTimeMillis() - requestedAt > VERIFICATION_TTL_MILLIS) {
            return false;
        }
        return uniqueId != null && !UUIDs.offline(name).equals(uniqueId);
    }

    /**
     * Descarta os pedidos que já passaram do prazo.
     *
     * Um pedido só sai do mapa quando alguém entra e ele é lido. Quem não completa o
     * handshake nunca chega lá, e sem esta limpeza bastaria abrir conexões com
     * nicknames originais diferentes para o mapa crescer sem parar.
     */
    private void forgetExpired() {
        long limit = System.currentTimeMillis() - VERIFICATION_TTL_MILLIS;
        verified.values().removeIf(requestedAt -> requestedAt < limit);
    }

    private static String key(String name, String address) {
        return name.toLowerCase(Locale.ROOT) + '|' + address;
    }

    /**
     * Avisa quando não conseguimos assumir o login de alguém que é original.
     *
     * Sem isto o recurso falha calado: o jogador entra pedindo senha e não há nada no
     * console dizendo por quê. Uma vez por minuto chega para diagnosticar sem encher o
     * log a cada tentativa de entrada.
     */
    private void warnTakeoverFailed(String name) {
        long now = System.currentTimeMillis();
        if (now - lastTakeoverWarning < 60_000L) {
            return;
        }
        lastTakeoverWarning = now;
        core.logger().warning("Não consegui assumir o login de " + name + " para comprovar a conta"
                + " na Mojang; ele entra pelo caminho da senha. Isso costuma ser incompatibilidade"
                + " com esta versão do servidor. Para desligar o recurso e parar o aviso, use"
                + " accounts.premium.standalone-auth: false.");
    }

    private volatile long lastTakeoverWarning;

    private boolean behindProxy() {
        if (core.settings().isBackendBehindProxy()) {
            return true;
        }
        try {
            Field bungee = Class.forName("org.spigotmc.SpigotConfig").getField("bungee");
            return Modifier.isStatic(bungee.getModifiers()) && bungee.getBoolean(null);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @ChannelHandler.Sharable
    private final class Acceptor extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Channel) {
                ((Channel) msg).pipeline().addFirst(preparer);
            }
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Entra antes de o servidor montar a pipeline, e por isso adia o próprio
     * trabalho: só depois que "packet_handler" existe é que dá para se posicionar
     * na frente dele.
     */
    @ChannelHandler.Sharable
    private final class Preparer extends ChannelInitializer<Channel> {
        @Override
        protected void initChannel(Channel channel) {
            channel.eventLoop().execute(() -> {
                try {
                    if (channel.pipeline().get("packet_handler") != null
                            && channel.pipeline().get(HANDLER) == null) {
                        channel.pipeline().addBefore("packet_handler", HANDLER, new LoginWatcher());
                    }
                } catch (RuntimeException ex) {
                    core.logger().log(Level.FINE, "Conexão sem pipeline de login: " + ex.getMessage());
                }
            });
        }
    }

    private final class LoginWatcher extends ChannelInboundHandlerAdapter {
        private boolean decided;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (decided || !isLoginStart(msg)) {
                super.channelRead(ctx, msg);
                return;
            }
            decided = true;

            String name = readName(msg);
            if (name == null || name.isEmpty()) {
                super.channelRead(ctx, msg);
                return;
            }

            Channel channel = ctx.channel();
            channel.config().setAutoRead(false);
            core.platform().scheduler().async(() -> {
                // A decisão pode consultar banco e rede, e qualquer coisa que escape
                // daqui deixaria a conexão pendurada com a leitura desligada. Seguir
                // como não-premium é sempre uma saída válida: pede a senha.
                boolean premium = false;
                try {
                    premium = core.auth().shouldVerifyWithMojang(name);
                } catch (Throwable failure) {
                    core.logger().log(Level.WARNING, "Falha ao decidir o login de " + name
                            + "; ele entra pelo caminho da senha.", failure);
                } finally {
                    boolean decision = premium;
                    channel.eventLoop().execute(() -> resume(ctx, msg, name, decision));
                }
            });
        }

        private void resume(ChannelHandlerContext ctx, Object loginStart, String name, boolean premium) {
            boolean tookOver = false;
            try {
                if (premium) {
                    tookOver = beginEncryption(ctx.channel(), loginStart, name);
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                core.logger().log(Level.WARNING, "Não consegui pedir a comprovação da conta de "
                        + name + " à Mojang; ele vai entrar pelo caminho da senha.", ex);
                tookOver = false;
            } finally {
                ctx.channel().config().setAutoRead(true);
            }

            if (!tookOver) {
                if (premium) {
                    warnTakeoverFailed(name);
                }
                // Ninguém assumiu o login: devolve o pacote e o servidor segue offline,
                // que é o caminho de quem usa senha.
                ctx.fireChannelRead(loginStart);
            }
        }

        private boolean beginEncryption(Channel channel, Object loginStart, String name)
                throws ReflectiveOperationException {
            Object networkManager = NmsLogin.networkManager(channel);
            if (networkManager == null) {
                return false;
            }
            Object listener = NmsLogin.loginListener(networkManager);
            if (listener == null) {
                return false;
            }

            // Lidos antes de mexer no listener: preparar preenche campos vazios.
            String serverId = NmsLogin.serverId(listener);
            byte[] challenge = NmsLogin.challenge(listener);

            if (!NmsLogin.prepare(listener, name)) {
                return false;
            }

            String loginPackage = loginStart.getClass().getPackage().getName();
            Object request = NmsLogin.encryptionRequest(loginPackage, serverId, publicKey, challenge);

            forgetExpired();
            verified.put(key(name, addressOf(channel.remoteAddress())), System.currentTimeMillis());
            NmsLogin.send(networkManager, channel, request);
            return true;
        }
    }

    /**
     * O pacote que abre o login, reconhecido pelo nome da classe.
     *
     * O pacote Java não serve para isso: até a 1.16 o servidor põe tudo num só,
     * net.minecraft.server.v1_8_R3 e parentes, e só da 1.17 em diante existe um
     * net.minecraft.network.protocol.login. Procurar por ".login" no caminho fazia o
     * login premium simplesmente não acontecer em metade das versões suportadas.
     */
    static boolean isLoginStart(Object packet) {
        Class<?> type = packet.getClass();
        if (!type.getName().startsWith("net.minecraft")) {
            return false;
        }
        String name = type.getSimpleName();
        return (name.contains("Login") && name.contains("Start"))
                || (name.contains("Hello") && name.startsWith("Serverbound"));
    }

    /**
     * O nickname dentro do pacote de entrada.
     *
     * Até a 1.18 ele vem embrulhado num GameProfile, e da 1.19 em diante é um campo
     * String solto, então a busca é pelos dois.
     */
    static String readName(Object packet) {
        try {
            for (Field field : packet.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(packet);
                if (value instanceof String) {
                    return (String) value;
                }
                if (value != null && value.getClass().getName().equals("com.mojang.authlib.GameProfile")) {
                    Object profileName = value.getClass().getMethod("getName").invoke(value);
                    if (profileName instanceof String) {
                        return (String) profileName;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Sem nome legível, o login segue pelo caminho normal.
        }
        return null;
    }

    private static String addressOf(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getAddress().getHostAddress();
        }
        return String.valueOf(address);
    }
}
