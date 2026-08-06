package br.vituz.core.vlogin.bukkit.listener;

import br.vituz.core.vlogin.bukkit.BukkitAuthPlayer;
import br.vituz.core.vlogin.bukkit.VLoginBukkit;
import br.vituz.core.vlogin.bukkit.compat.PlayerCompat;
import br.vituz.core.vlogin.common.config.MessageKey;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueia tudo o que um jogador não autenticado poderia fazer.
 */
public final class RestrictionListener implements Listener {
    private static final long NOTICE_COOLDOWN_MILLIS = 2000L;

    private final VLoginBukkit plugin;
    private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();

    public RestrictionListener(VLoginBukkit plugin) {
        this.plugin = plugin;
    }

    public void forget(Player player) {
        lastNotice.remove(player.getUniqueId());
    }

    private boolean restricted(Player player) {
        return !plugin.core().auth().isAuthenticated(player.getName())
                && !plugin.core().auth().isUnrestricted(player.getName());
    }

    private void deny(Cancellable event, Player player, MessageKey message) {
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long previous = lastNotice.get(player.getUniqueId());
        if (previous != null && now - previous < NOTICE_COOLDOWN_MILLIS) {
            return;
        }
        lastNotice.put(player.getUniqueId(), now);
        new BukkitAuthPlayer(player).sendMessage(plugin.core().messages().get(message));
    }

    /**
     * Se o jogador saiu do lugar, e não apenas girou a cabeça.
     *
     * A comparação é pela posição exata, não pelo bloco: dentro de um bloco cabe
     * andar de um lado para o outro, e um pulo raramente troca de bloco em Y. Com a
     * checagem por bloco, quem não autenticou andava e pulava à vontade desde que
     * ficasse no mesmo cubo de um metro.
     */
    static boolean movedAway(Location from, Location to) {
        return from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.core().settings().freeze || !restricted(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (!movedAway(from, to)) {
            return;
        }
        Location anchor = plugin.limbo().anchor(event.getPlayer());
        Location target = anchor == null ? from : anchor;
        Location corrected = target.clone();
        corrected.setYaw(to.getYaw());
        corrected.setPitch(to.getPitch());
        event.setTo(corrected);
    }

    /**
     * Teleportes têm lista de handlers própria, então não passam pelo onMove acima.
     * Um jogador que entra dentro de um portal seria levado para outro mundo antes de
     * autenticar; o teleporte do próprio limbo usa a causa PLUGIN e continua valendo.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!restricted(event.getPlayer())) {
            return;
        }
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!restricted(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        new BukkitAuthPlayer(event.getPlayer())
                .sendMessage(plugin.core().messages().get(MessageKey.BLOCKED_CHAT));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatRecipients(AsyncPlayerChatEvent event) {
        if (!plugin.core().settings().hideChat) {
            return;
        }
        event.getRecipients().removeIf(recipient ->
                !plugin.core().auth().isAuthenticated(recipient.getName())
                        && !plugin.core().auth().isUnrestricted(recipient.getName()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!restricted(player)) {
            return;
        }
        String message = event.getMessage().toLowerCase(Locale.ROOT);
        int space = message.indexOf(' ');
        String label = space == -1 ? message : message.substring(0, space);

        if (plugin.commands().isAllowedBeforeAuth(label)
                || plugin.core().settings().allowedBeforeLogin.contains(label)) {
            return;
        }
        deny(event, player, MessageKey.BLOCKED_COMMAND);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (restricted(event.getPlayer())) {
            deny(event, event.getPlayer(), MessageKey.BLOCKED_ACTION);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (restricted(event.getPlayer())) {
            deny(event, event.getPlayer(), MessageKey.BLOCKED_ACTION);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (restricted(event.getPlayer())) {
            deny(event, event.getPlayer(), MessageKey.BLOCKED_ACTION);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (restricted(event.getPlayer())) {
            deny(event, event.getPlayer(), MessageKey.BLOCKED_ACTION);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (restricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (restricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (restricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (restricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player) || !restricted((Player) event.getPlayer())) {
            return;
        }
        if (isUnrestrictedInventory(event)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player) || !restricted((Player) event.getWhoClicked())) {
            return;
        }
        if (isUnrestrictedInventory(event)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player && restricted((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    private boolean isUnrestrictedInventory(org.bukkit.event.inventory.InventoryEvent event) {
        if (plugin.core().settings().allowedInventories.isEmpty()) {
            return false;
        }
        String title = PlayerCompat.inventoryTitle(event);
        if (title == null) {
            return false;
        }
        for (String allowed : plugin.core().settings().allowedInventories) {
            if (title.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && restricted((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && restricted((Player) event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player && restricted((Player) event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && restricted((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
