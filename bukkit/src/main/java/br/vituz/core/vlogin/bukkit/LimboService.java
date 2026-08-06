package br.vituz.core.vlogin.bukkit;

import br.vituz.core.vlogin.bukkit.compat.FoliaSupport;
import br.vituz.core.vlogin.bukkit.compat.PlayerCompat;
import br.vituz.core.vlogin.common.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Segura o jogador não autenticado num estado inofensivo e devolve tudo
 * quando ele entra.
 *
 * Esconder o inventário não é enfeite: sem isso qualquer pessoa vê os itens
 * de uma conta só conectando com o nickname dela e nunca logando.
 */
public final class LimboService {
    private final VLoginBukkit plugin;
    private final Map<UUID, LimboState> states = new ConcurrentHashMap<>();

    public LimboService(VLoginBukkit plugin) {
        this.plugin = plugin;
    }

    private static final class LimboState {
        ItemStack[] inventory;
        ItemStack[] armor;
        float walkSpeed;
        float flySpeed;
        boolean allowFlight;
        boolean flying;
        Location location;
    }

    public void restrict(Player player) {
        Settings settings = plugin.core().settings();
        LimboState state = new LimboState();

        if (settings.hideInventory) {
            state.inventory = player.getInventory().getContents();
            state.armor = player.getInventory().getArmorContents();
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.updateInventory();
        }

        if (settings.hideAttributes) {
            state.walkSpeed = player.getWalkSpeed();
            state.flySpeed = player.getFlySpeed();
            state.allowFlight = player.getAllowFlight();
            state.flying = player.isFlying();
        }

        if (settings.freeze) {
            state.location = player.getLocation().clone();
            player.setWalkSpeed(0f);
            player.setFlySpeed(0f);
        }

        if (settings.blindness) {
            int duration = Math.max(20, settings.loginTimeoutSeconds * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, 2, false, false));
        }

        Location spawn = settings.useSpawnPoint ? plugin.loginSpawn().location() : null;
        if (spawn != null) {
            FoliaSupport.teleport(player, spawn);
            state.location = spawn;
        } else if (settings.safeSpawn) {
            Location location = player.getLocation();
            Location highest = location.getWorld().getHighestBlockAt(location).getLocation().add(0.5, 1, 0.5);
            highest.setYaw(location.getYaw());
            highest.setPitch(location.getPitch());
            FoliaSupport.teleport(player, highest);
            state.location = highest;
        }

        states.put(player.getUniqueId(), state);

        if (settings.hidePlayers) {
            applyVisibility(player, false);
        }
    }

    public void release(Player player) {
        Settings settings = plugin.core().settings();
        LimboState state = states.remove(player.getUniqueId());

        if (state != null) {
            if (state.inventory != null) {
                player.getInventory().setContents(state.inventory);
                player.getInventory().setArmorContents(state.armor);
                player.updateInventory();
            }
            if (settings.freeze || settings.hideAttributes) {
                player.setWalkSpeed(state.walkSpeed <= 0f ? 0.2f : state.walkSpeed);
                player.setFlySpeed(state.flySpeed <= 0f ? 0.1f : state.flySpeed);
                player.setAllowFlight(state.allowFlight);
                if (state.allowFlight && state.flying) {
                    player.setFlying(true);
                }
            }
        }

        if (settings.blindness) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        if (settings.hidePlayers) {
            applyVisibility(player, true);
        }
    }

    public void forget(Player player) {
        states.remove(player.getUniqueId());
    }

    public Location anchor(Player player) {
        LimboState state = states.get(player.getUniqueId());
        return state == null ? null : state.location;
    }

    public void updateAnchor(Player player, Location location) {
        LimboState state = states.get(player.getUniqueId());
        if (state != null) {
            state.location = location;
        }
    }

    private void applyVisibility(Player player, boolean visible) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            Player viewer = other;
            FoliaSupport.runForPlayer(plugin, viewer, () -> {
                if (visible) {
                    PlayerCompat.showPlayer(plugin, viewer, player);
                } else {
                    PlayerCompat.hidePlayer(plugin, viewer, player);
                }
            });

            if (visible) {
                if (plugin.core().auth().isAuthenticated(viewer.getName())) {
                    PlayerCompat.showPlayer(plugin, player, viewer);
                }
            } else {
                PlayerCompat.hidePlayer(plugin, player, viewer);
            }
        }
    }

    public void refreshVisibilityFor(Player joining) {
        if (!plugin.core().settings().hidePlayers) {
            return;
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(joining)) {
                continue;
            }
            if (!plugin.core().auth().isAuthenticated(other.getName())) {
                PlayerCompat.hidePlayer(plugin, joining, other);
            }
        }
    }

    public void clear() {
        states.clear();
    }
}
