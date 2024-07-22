package cc.ahaly.weathering.util;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public class McaTransform {

    public static String getMCARegion(Location location) {
        int regionX = location.getBlockX() >> 9;
        int regionZ = location.getBlockZ() >> 9;
        return "r." + regionX + "." + regionZ + ".mca";
    }
    public static Location getCenterLocation(Plugin plugin, String mcaRegion) {
        String[] parts = mcaRegion.split("\\.");
        int regionX = Integer.parseInt(parts[1]);
        int regionZ = Integer.parseInt(parts[2]);

        int startX = regionX * 512;
        int startZ = regionZ * 512;
        return new Location(plugin.getServer().getWorld("world"), startX + 256, 64, startZ + 256);
    }
}
