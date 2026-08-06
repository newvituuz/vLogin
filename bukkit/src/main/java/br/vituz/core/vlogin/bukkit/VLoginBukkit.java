package br.vituz.core.vlogin.bukkit;

import br.vituz.core.vlogin.bukkit.listener.ConnectionListener;
import br.vituz.core.vlogin.bukkit.listener.LockdownListener;
import br.vituz.core.vlogin.bukkit.listener.PickupListener;
import br.vituz.core.vlogin.bukkit.listener.ProxyMessageListener;
import br.vituz.core.vlogin.bukkit.listener.RestrictionListener;
import br.vituz.core.vlogin.bukkit.premium.PremiumLogin;
import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.proxy.ProxyMessaging;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class VLoginBukkit extends JavaPlugin {
    private static VLoginBukkit instance;

    private VLoginCore core;
    private LimboService limbo;
    private BukkitPlatform platform;
    private LoginSpawn loginSpawn;
    private RestrictionListener restrictions;
    private PremiumLogin premiumLogin;

    public static VLoginBukkit instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.loginSpawn = new LoginSpawn(this);
        this.limbo = new LimboService(this);
        this.platform = new BukkitPlatform(this);
        this.core = new VLoginCore(platform);

        try {
            core.enable();
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "vLogin não conseguiu iniciar. O servidor vai"
                    + " recusar conexões até isso ser resolvido, porque aceitá-las sem"
                    + " autenticação deixaria todas as contas abertas.", ex);
            Bukkit.getPluginManager().registerEvents(
                    new LockdownListener(String.valueOf(ex.getMessage())), this);
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                player.kickPlayer(ChatColor.RED + "Servidor em manutenção: sistema de login indisponível.");
            }
            return;
        }

        if (core.settings().premiumStandaloneAuth) {
            this.premiumLogin = new PremiumLogin(core);
            String skipped = premiumLogin.install();
            if (skipped != null) {
                this.premiumLogin = null;
                getLogger().info("Login direto de contas originais desligado: " + skipped + ".");
            } else {
                getLogger().info("Login direto de contas originais ligado: quem tem conta na"
                        + " Mojang entra sem senha e com a skin.");
            }
        } else {
            getLogger().info("Login direto de contas originais desligado em"
                    + " accounts.premium.standalone-auth.");
        }
        getLogger().info("Modo premium: " + core.settings().premiumMode.name().toLowerCase(java.util.Locale.ROOT)
                + ", online-mode do servidor: " + Bukkit.getOnlineMode()
                + ", consegue comprovar conta na Mojang: " + platform.canVerifyMojang() + ".");

        this.restrictions = new RestrictionListener(this);
        Bukkit.getPluginManager().registerEvents(new ConnectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(restrictions, this);
        new PickupListener(this).register();

        Bukkit.getMessenger().registerOutgoingPluginChannel(this, ProxyMessaging.CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, ProxyMessaging.CHANNEL,
                new ProxyMessageListener(this));

        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            core.auth().handleJoin(new BukkitAuthPlayer(player));
        }
    }

    @Override
    public void onDisable() {
        if (premiumLogin != null) {
            premiumLogin.uninstall();
            premiumLogin = null;
        }
        if (core != null) {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (!core.auth().isAuthenticated(player.getName())) {
                    limbo.release(player);
                }
            }
            core.disable();
        }
        if (limbo != null) {
            limbo.clear();
        }
        instance = null;
    }

    public VLoginCore core() {
        return core;
    }

    public LimboService limbo() {
        return limbo;
    }

    public LoginSpawn loginSpawn() {
        return loginSpawn;
    }

    public RestrictionListener restrictions() {
        return restrictions;
    }

    public PremiumLogin premiumLogin() {
        return premiumLogin;
    }

    public BukkitCommands commands() {
        return platform.commands();
    }
}
