package br.vituz.core.vlogin.bukkit.compat;

import br.vituz.core.vlogin.common.platform.SoundEffect;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A ponte entre a API 1.8 e as versões novas.
 *
 * O módulo compila contra a 1.8 de propósito, então o que veio depois é
 * resolvido em tempo de execução; onde não havia API (título e action bar
 * até a 1.10), o pacote é montado direto.
 */
public final class PlayerCompat {
    private static final Map<SoundEffect, Sound> SOUNDS = new EnumMap<>(SoundEffect.class);
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private static Method sendTitleMethod;
    private static Method spigotSendMessage;
    private static Object actionBarType;
    private static Method textComponentFromLegacy;

    private static Method getHandle;
    private static Field playerConnection;
    private static Method sendPacket;
    private static Method chatSerializer;
    private static Constructor<?> titlePacket;
    private static Constructor<?> chatPacket;
    private static Object[] titleActions;

    private static boolean packetsReady;
    private static boolean packetsAttempted;

    static {
        try {
            sendTitleMethod = Player.class.getMethod("sendTitle", String.class, String.class,
                    int.class, int.class, int.class);
        } catch (NoSuchMethodException ignored) {
            sendTitleMethod = null;
        }
        try {
            Class<?> chatMessageType = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Class<?> baseComponent = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            spigotSendMessage = Class.forName("org.bukkit.entity.Player$Spigot")
                    .getMethod("sendMessage", chatMessageType, baseComponent);
            actionBarType = Enum.valueOf(chatMessageType.asSubclass(Enum.class), "ACTION_BAR");
            textComponentFromLegacy = textComponent.getMethod("fromLegacyText", String.class);
        } catch (ReflectiveOperationException ignored) {
            spigotSendMessage = null;
        }
    }

    private PlayerCompat() {
    }

    public static void sendTitle(Player player, String title, String subtitle,
                                 int fadeIn, int stay, int fadeOut) {
        if (sendTitleMethod != null) {
            try {
                sendTitleMethod.invoke(player, title, subtitle, fadeIn, stay, fadeOut);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        sendTitlePackets(player, title, subtitle, fadeIn, stay, fadeOut);
    }

    public static void clearTitle(Player player) {
        sendTitle(player, "", "", 0, 1, 0);
    }

    private static void sendTitlePackets(Player player, String title, String subtitle,
                                         int fadeIn, int stay, int fadeOut) {
        if (!initPackets()) {
            return;
        }
        try {
            Object connection = playerConnection.get(getHandle.invoke(player));
            sendPacket.invoke(connection, titlePacket.newInstance(titleActions[2], null, fadeIn, stay, fadeOut));
            if (subtitle != null && !subtitle.isEmpty()) {
                sendPacket.invoke(connection, titlePacket.newInstance(titleActions[1],
                        chatSerializer.invoke(null, toJson(subtitle)), 0, 0, 0));
            }
            sendPacket.invoke(connection, titlePacket.newInstance(titleActions[0],
                    chatSerializer.invoke(null, toJson(title)), 0, 0, 0));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    public static void sendActionBar(Player player, String message) {
        if (spigotSendMessage != null && actionBarType != null && textComponentFromLegacy != null) {
            try {
                Object components = textComponentFromLegacy.invoke(null, message);
                spigotSendMessage.invoke(player.spigot(), actionBarType, firstComponent(components));
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        sendActionBarPacket(player, message);
    }

    private static Object firstComponent(Object components) throws ReflectiveOperationException {
        Object[] array = (Object[]) components;
        Class<?> textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent");
        Constructor<?> constructor = textComponent.getConstructor(
                Class.forName("[Lnet.md_5.bungee.api.chat.BaseComponent;"));
        return constructor.newInstance((Object) array);
    }

    private static void sendActionBarPacket(Player player, String message) {
        if (!initPackets() || chatPacket == null) {
            return;
        }
        try {
            Object connection = playerConnection.get(getHandle.invoke(player));
            Object component = chatSerializer.invoke(null, toJson(message));
            sendPacket.invoke(connection, chatPacket.newInstance(component, (byte) 2));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    public static void playSound(Player player, SoundEffect effect) {
        Sound sound = SOUNDS.get(effect);
        if (sound == null) {
            sound = resolveSound(effect);
            if (sound == null) {
                return;
            }
            SOUNDS.put(effect, sound);
        }
        try {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (RuntimeException ignored) {
        }
    }

    private static Sound resolveSound(SoundEffect effect) {
        for (String candidate : effect.candidates()) {
            try {
                Object value = Sound.class.getField(candidate).get(null);
                if (value instanceof Sound) {
                    return (Sound) value;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    public static void hidePlayer(Plugin plugin, Player viewer, Player target) {
        Method method = method(Player.class, "hidePlayer", Plugin.class, Player.class);
        if (method != null) {
            try {
                method.invoke(viewer, plugin, target);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        viewer.hidePlayer(target);
    }

    @SuppressWarnings("deprecation")
    public static void showPlayer(Plugin plugin, Player viewer, Player target) {
        Method method = method(Player.class, "showPlayer", Plugin.class, Player.class);
        if (method != null) {
            try {
                method.invoke(viewer, plugin, target);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        viewer.showPlayer(target);
    }

    public static String inventoryTitle(org.bukkit.event.inventory.InventoryEvent event) {
        try {
            Object view = event.getView();
            if (view == null) {
                return null;
            }
            Method getTitle = Class.forName("org.bukkit.inventory.InventoryView").getMethod("getTitle");
            Object title = getTitle.invoke(view);
            return title == null ? null : title.toString();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        StringBuilder key = new StringBuilder(owner.getName()).append('#').append(name);
        for (Class<?> parameter : parameters) {
            key.append(':').append(parameter.getName());
        }
        String cacheKey = key.toString();
        Method cached = METHOD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            Method resolved = owner.getMethod(name, parameters);
            METHOD_CACHE.put(cacheKey, resolved);
            return resolved;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private static String toJson(String legacy) {
        return "{\"text\":\"" + legacy.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private static synchronized boolean initPackets() {
        if (packetsAttempted) {
            return packetsReady;
        }
        packetsAttempted = true;

        String version = ServerVersion.nmsPackage();
        if (version == null) {
            return false;
        }
        try {
            String nms = "net.minecraft.server." + version + ".";
            String craft = "org.bukkit.craftbukkit." + version + ".";

            Class<?> craftPlayer = Class.forName(craft + "entity.CraftPlayer");
            getHandle = craftPlayer.getMethod("getHandle");

            Class<?> entityPlayer = Class.forName(nms + "EntityPlayer");
            playerConnection = entityPlayer.getField("playerConnection");

            Class<?> packetClass = Class.forName(nms + "Packet");
            sendPacket = Class.forName(nms + "PlayerConnection").getMethod("sendPacket", packetClass);

            Class<?> chatComponent = Class.forName(nms + "IChatBaseComponent");
            chatSerializer = Class.forName(nms + "IChatBaseComponent$ChatSerializer")
                    .getMethod("a", String.class);

            Class<?> titleAction = Class.forName(nms + "PacketPlayOutTitle$EnumTitleAction");
            titleActions = new Object[]{
                    Enum.valueOf(titleAction.asSubclass(Enum.class), "TITLE"),
                    Enum.valueOf(titleAction.asSubclass(Enum.class), "SUBTITLE"),
                    Enum.valueOf(titleAction.asSubclass(Enum.class), "TIMES")
            };
            titlePacket = Class.forName(nms + "PacketPlayOutTitle").getConstructor(
                    titleAction, chatComponent, int.class, int.class, int.class);

            chatPacket = Class.forName(nms + "PacketPlayOutChat")
                    .getConstructor(chatComponent, byte.class);

            packetsReady = true;
        } catch (ReflectiveOperationException ex) {
            Bukkit.getLogger().fine("vLogin: legacy packet support unavailable (" + ex.getMessage() + ")");
            packetsReady = false;
        }
        return packetsReady;
    }
}
