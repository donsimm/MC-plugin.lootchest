package org.bukkit;
public final class Bukkit {
    public static Server server;
    public static Server getServer() { return server; }
    public static String getVersion() { return server.getVersion(); }
    public static String getBukkitVersion() { return server.getBukkitVersion(); }
}
