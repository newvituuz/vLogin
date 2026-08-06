package br.vituz.core.vlogin.bukkit.premium;

import io.netty.channel.Channel;
import org.bukkit.Bukkit;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A fase de login do servidor, que a API do Bukkit não expõe.
 *
 * Nada aqui é procurado por nome de campo: os nomes mudam a cada versão e entre
 * Spigot e Paper. A busca é sempre por tipo (o campo que guarda um GameProfile, o
 * enum que tem a constante KEY, a lista de ChannelFuture), que é o que se mantém
 * de 1.8 a 1.21.
 */
final class NmsLogin {
    private static final SecureRandom RANDOM = new SecureRandom();

    private NmsLogin() {
    }

    static Object minecraftServer() throws ReflectiveOperationException {
        Object server = Bukkit.getServer();
        Method getServer = server.getClass().getMethod("getServer");
        getServer.setAccessible(true);
        return getServer.invoke(server);
    }

    /**
     * Os canais que aceitam conexão, um por porta em escuta.
     *
     * A lista é a mesma que o servidor usa, e ele mexe nela de outra thread, então
     * quem itera precisa sincronizar no próprio objeto.
     */
    static List<?> listeningChannels(Object minecraftServer) throws ReflectiveOperationException {
        Object connection = findServerConnection(minecraftServer);
        if (connection == null) {
            throw new IllegalStateException("Não achei o ServerConnection do servidor.");
        }
        for (Field field : connection.getClass().getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            if (!listOf(field, "ChannelFuture")) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(connection);
            if (value != null) {
                return (List<?>) value;
            }
        }
        throw new IllegalStateException("Não achei a lista de canais em escuta.");
    }

    private static boolean listOf(Field field, String simpleName) {
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType)) {
            return false;
        }
        Type[] arguments = ((ParameterizedType) generic).getActualTypeArguments();
        return arguments.length == 1 && arguments[0].getTypeName().endsWith(simpleName);
    }

    private static Object findServerConnection(Object minecraftServer) throws ReflectiveOperationException {
        for (Method method : minecraftServer.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (!method.getReturnType().getSimpleName().contains("ServerConnection")) {
                continue;
            }
            method.setAccessible(true);
            Object value = method.invoke(minecraftServer);
            if (value != null) {
                return value;
            }
        }
        for (Class<?> type = minecraftServer.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getType().getSimpleName().contains("ServerConnection")) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(minecraftServer);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    static PublicKey publicKey(Object minecraftServer) throws ReflectiveOperationException {
        for (Method method : minecraftServer.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == KeyPair.class) {
                method.setAccessible(true);
                KeyPair pair = (KeyPair) method.invoke(minecraftServer);
                if (pair != null) {
                    return pair.getPublic();
                }
            }
        }
        for (Class<?> type = minecraftServer.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != KeyPair.class) {
                    continue;
                }
                field.setAccessible(true);
                KeyPair pair = (KeyPair) field.get(minecraftServer);
                if (pair != null) {
                    return pair.getPublic();
                }
            }
        }
        throw new IllegalStateException("Não achei o par de chaves do servidor.");
    }

    /** O handler que o próprio servidor põe na pipeline; é ele o NetworkManager. */
    static Object networkManager(Channel channel) {
        return channel.pipeline().get("packet_handler");
    }

    static Object loginListener(Object networkManager) throws ReflectiveOperationException {
        for (Class<?> type = networkManager.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(networkManager);
                if (value == null) {
                    continue;
                }
                String name = value.getClass().getSimpleName();
                if (name.contains("LoginListener") || name.contains("LoginPacketListener")) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Põe o login no mesmo ponto em que ele estaria num servidor online mode, logo
     * antes de mandar o pedido de criptografia.
     *
     * Daqui em diante quem trabalha é o servidor: ele decifra a resposta, liga a
     * cifra, consulta o sessionserver da Mojang e troca o perfil pelo verdadeiro,
     * com skin. Só o empurrão inicial é nosso.
     */
    static boolean prepare(Object loginListener, String playerName) throws ReflectiveOperationException {
        Object profile = newProfile(playerName);

        boolean profileSet = false;
        boolean nameSet = false;
        boolean stateSet = false;

        for (Class<?> type = loginListener.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);

                if (profile != null && !profileSet && field.getType() == profile.getClass()) {
                    field.set(loginListener, profile);
                    profileSet = true;
                    continue;
                }
                if (!stateSet && field.getType().isEnum()) {
                    Object key = enumConstant(field.getType(), "KEY");
                    if (key != null) {
                        field.set(loginListener, key);
                        stateSet = true;
                    }
                }
            }
        }

        // Só quando não há perfil para carregar o nickname é que ele precisa ir para um
        // campo String, e isso é o caso da 1.20.5 em diante. Fazer isso também nas
        // versões antigas seria perigoso: lá existe um campo String para o identificador
        // que entra no hash da Mojang, e escrever o nickname nele faria os dois lados
        // calcularem hashes diferentes, com a conta nunca sendo confirmada.
        if (!profileSet) {
            nameSet = fillRequestedName(loginListener, playerName);
        }
        // Basta o servidor saber por qual nickname perguntar, seja pelo perfil (até a
        // 1.20.4) ou pelo campo de nickname pedido (da 1.20.5 em diante).
        return (profileSet || nameSet) && stateSet;
    }

    /**
     * Grava o nickname pedido no campo que o servidor vai consultar.
     *
     * Só campos vazios são preenchidos: o identificador que entra no hash da Mojang
     * já vem com valor, e sobrescrevê-lo quebraria a conferência do outro lado.
     */
    private static boolean fillRequestedName(Object loginListener, String playerName)
            throws ReflectiveOperationException {
        boolean filled = false;
        for (Class<?> type = loginListener.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    continue;
                }
                field.setAccessible(true);
                if (field.get(loginListener) == null) {
                    field.set(loginListener, playerName);
                    filled = true;
                }
            }
        }
        return filled;
    }

    /**
     * Um perfil só com o nickname, que é o que as versões antigas esperam encontrar.
     *
     * Da 1.20.5 em diante o authlib recusa perfil sem id, e nessas versões ele nem é
     * preciso: o servidor guarda o nickname à parte e só monta o perfil depois de
     * confirmar a conta. Devolver null aqui é resposta legítima, não erro.
     */
    private static Object newProfile(String playerName) {
        try {
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Constructor<?> constructor = profileClass.getConstructor(UUID.class, String.class);
            return constructor.newInstance(null, playerName);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static Object enumConstant(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        return null;
    }

    /**
     * O identificador que entra no hash conferido com a Mojang.
     *
     * O vanilla usa string vazia desde sempre, e é com ela que o servidor vai
     * calcular o hash do outro lado. Só aceitamos como identificador um valor vazio
     * ou hexadecimal: mandar qualquer outro campo String que exista ali faria os dois
     * lados calcularem hashes diferentes, e a conta nunca seria confirmada.
     */
    static String serverId(Object loginListener) throws ReflectiveOperationException {
        for (Class<?> type = loginListener.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(loginListener);
                if (value instanceof String && looksLikeServerId((String) value)) {
                    return (String) value;
                }
            }
        }
        return "";
    }

    private static boolean looksLikeServerId(String value) {
        if (value.isEmpty()) {
            return true;
        }
        if (value.length() > 20) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /**
     * O desafio que o cliente devolve cifrado.
     *
     * Tem que ser o mesmo que o servidor guardou, senão ele mesmo recusa a resposta.
     * Se ainda não existir, geramos e gravamos no campo dele.
     */
    static byte[] challenge(Object loginListener) throws ReflectiveOperationException {
        Field candidate = null;
        for (Class<?> type = loginListener.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != byte[].class) {
                    continue;
                }
                field.setAccessible(true);
                byte[] value = (byte[]) field.get(loginListener);
                if (value != null && value.length > 0) {
                    return value;
                }
                if (candidate == null) {
                    candidate = field;
                }
            }
        }
        byte[] generated = new byte[4];
        RANDOM.nextBytes(generated);
        if (candidate != null) {
            candidate.set(loginListener, generated);
        }
        return generated;
    }

    /**
     * Monta o pacote de pedido de criptografia.
     *
     * A assinatura do construtor mudou três vezes: até a 1.20.4 recebe a PublicKey
     * inteira, da 1.20.5 em diante recebe os bytes dela, e da 1.21.2 em diante ainda
     * ganhou um booleano. Em vez de casar versão com assinatura, preenchemos cada
     * parâmetro pelo tipo dele.
     */
    static Object encryptionRequest(String loginPackage, String serverId, PublicKey publicKey,
                                    byte[] challenge) throws ReflectiveOperationException {
        Class<?> packetClass = findClass(
                loginPackage + ".PacketLoginOutEncryptionBegin",
                loginPackage + ".ClientboundHelloPacket");
        if (packetClass == null) {
            throw new IllegalStateException("Não achei o pacote de pedido de criptografia em " + loginPackage);
        }

        Constructor<?> constructor = widest(packetClass);
        Class<?>[] types = constructor.getParameterTypes();
        Object[] arguments = new Object[types.length];
        int byteArrays = 0;

        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == String.class) {
                arguments[i] = serverId;
            } else if (type == PublicKey.class) {
                arguments[i] = publicKey;
            } else if (type == byte[].class) {
                arguments[i] = byteArrays++ == 0 && !hasPublicKeyParameter(types)
                        ? publicKey.getEncoded() : challenge;
            } else if (type == boolean.class) {
                arguments[i] = Boolean.TRUE;
            } else {
                throw new IllegalStateException("Parâmetro inesperado em "
                        + packetClass.getSimpleName() + ": " + type.getName());
            }
        }
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static boolean hasPublicKeyParameter(Class<?>[] types) {
        for (Class<?> type : types) {
            if (type == PublicKey.class) {
                return true;
            }
        }
        return false;
    }

    private static Constructor<?> widest(Class<?> type) {
        Constructor<?> widest = null;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (widest == null || constructor.getParameterCount() > widest.getParameterCount()) {
                widest = constructor;
            }
        }
        if (widest == null) {
            throw new IllegalStateException("Sem construtor em " + type.getName());
        }
        return widest;
    }

    private static Class<?> findClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
                // A próxima da lista.
            }
        }
        return null;
    }

    /**
     * Manda o pacote pelo caminho do próprio servidor quando ele existe.
     *
     * Escrever direto no canal também funciona, mas passar pelo NetworkManager deixa
     * o servidor cuidar do estado do protocolo, que é o que ele faria sozinho.
     */
    /** Os nomes que o método de envio já teve, do mais provável para o menos. */
    private static int sendPriority(String methodName) {
        if (methodName.equals("send")) {
            return 3;
        }
        if (methodName.equals("handle") || methodName.equals("sendPacket")) {
            return 2;
        }
        return methodName.length() <= 2 ? 1 : 0;
    }

    static void send(Object networkManager, Channel channel, Object packet) {
        List<Method> candidates = new ArrayList<>();
        for (Method method : networkManager.getClass().getMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() == void.class
                    && method.getParameterTypes()[0].isInstance(packet)) {
                candidates.add(method);
            }
        }
        // getMethods() não promete ordem, e mais de um método pode aceitar um pacote.
        // Os nomes de envio vêm primeiro para a escolha não mudar de uma execução para
        // outra, o que daria um bug que só aparece às vezes.
        candidates.sort((first, second) ->
                Integer.compare(sendPriority(second.getName()), sendPriority(first.getName())));

        for (Method method : candidates) {
            try {
                method.setAccessible(true);
                method.invoke(networkManager, packet);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Tenta o próximo, e no fim escreve direto no canal.
            }
        }
        channel.writeAndFlush(packet);
    }
}
