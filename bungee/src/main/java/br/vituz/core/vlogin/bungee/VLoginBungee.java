package br.vituz.core.vlogin.bungee;

import br.vituz.core.vlogin.common.VLoginCore;
import br.vituz.core.vlogin.common.proxy.ProxyMessaging;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.logging.Level;

public final class VLoginBungee extends Plugin {
    private VLoginCore core;
    private BungeePlatform platform;

    @Override
    public void onEnable() {
        this.platform = new BungeePlatform(this);
        this.core = new VLoginCore(platform);

        try {
            core.enable();
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "vLogin não conseguiu iniciar. O proxy vai recusar"
                    + " conexões até isso ser resolvido, porque aceitá-las sem autenticação"
                    + " deixaria todas as contas abertas.", ex);
            ProxyServer.getInstance().getPluginManager()
                    .registerListener(this, new LockdownListener(String.valueOf(ex.getMessage())));
            return;
        }

        ProxyServer.getInstance().registerChannel(ProxyMessaging.CHANNEL);
        ProxyServer.getInstance().registerChannel(ProxyMessaging.LEGACY_CHANNEL);
        ProxyServer.getInstance().getPluginManager().registerListener(this, new ProxyListener(this));
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.disable();
        }
    }

    public VLoginCore core() {
        return core;
    }

    public BungeePlatform platform() {
        return platform;
    }
}
