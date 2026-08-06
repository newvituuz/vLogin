package br.vituz.core.vlogin.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;
import java.util.logging.Level;

/**
 * O ponto onde o jogador não autenticado fica, definido por /vlogin spawn set.
 */
public final class LoginSpawn {
    private final File file;
    private final VLoginBukkit plugin;

    private volatile String worldName;
    private volatile double x;
    private volatile double y;
    private volatile double z;
    private volatile float yaw;
    private volatile float pitch;

    public LoginSpawn(VLoginBukkit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawn.properties");
        load();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            properties.load(in);
            worldName = properties.getProperty("world");
            x = Double.parseDouble(properties.getProperty("x", "0"));
            y = Double.parseDouble(properties.getProperty("y", "64"));
            z = Double.parseDouble(properties.getProperty("z", "0"));
            yaw = Float.parseFloat(properties.getProperty("yaw", "0"));
            pitch = Float.parseFloat(properties.getProperty("pitch", "0"));
        } catch (IOException | NumberFormatException ex) {
            plugin.getLogger().log(Level.WARNING, "spawn.properties inválido, ignorando", ex);
            worldName = null;
        }
    }

    public boolean isSet() {
        return worldName != null;
    }

    public Location location() {
        String name = worldName;
        if (name == null) {
            return null;
        }
        World world = Bukkit.getWorld(name);
        return world == null ? null : new Location(world, x, y, z, yaw, pitch);
    }

    public boolean save(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Properties properties = new Properties();
        properties.setProperty("world", location.getWorld().getName());
        properties.setProperty("x", Double.toString(location.getX()));
        properties.setProperty("y", Double.toString(location.getY()));
        properties.setProperty("z", Double.toString(location.getZ()));
        properties.setProperty("yaw", Float.toString(location.getYaw()));
        properties.setProperty("pitch", Float.toString(location.getPitch()));

        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return false;
        }
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            properties.store(out, "vLogin - posição onde jogadores não autenticados ficam");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar o ponto de login", ex);
            return false;
        }

        this.worldName = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        return true;
    }
}
