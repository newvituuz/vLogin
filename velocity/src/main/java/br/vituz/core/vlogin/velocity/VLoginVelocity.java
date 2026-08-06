package br.vituz.core.vlogin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import br.vituz.core.vlogin.common.VLoginCore;

import java.nio.file.Path;
import java.util.logging.Level;

public final class VLoginVelocity {
    private final ProxyServer server;
    private final Path dataDirectory;

    private VelocityPlatform platform;
    private VLoginCore core;

    @Inject
    public VLoginVelocity(ProxyServer server, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.platform = new VelocityPlatform(this, server, dataDirectory);
        this.core = new VLoginCore(platform);

        try {
            core.enable();
        } catch (RuntimeException ex) {
            platform.logger().log(Level.SEVERE, "vLogin não conseguiu iniciar. O proxy vai recusar"
                    + " conexões até isso ser resolvido, porque aceitá-las sem autenticação"
                    + " deixaria todas as contas abertas.", ex);
            server.getEventManager().register(this,
                    new LockdownListener(String.valueOf(ex.getMessage())));
            return;
        }

        server.getChannelRegistrar().register(VelocityPlatform.CHANNEL);
        server.getEventManager().register(this, new VelocityListener(this));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (core != null) {
            core.disable();
        }
    }

    public VLoginCore core() {
        return core;
    }

    public VelocityPlatform platform() {
        return platform;
    }
}
