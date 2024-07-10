package cc.ahaly.weathering;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.MarkerSet;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MyDynmapListener extends DynmapCommonAPIListener {
    @Override
    public void apiEnabled(DynmapCommonAPI api) {
        // API 启用时调用
        handleApiEnabled(api);
    }

    private void handleApiEnabled(DynmapCommonAPI api) {
        MarkerSet markerSet = api.getMarkerAPI().createMarkerSet("myMarkers", "My Markers", null, false);
        if (markerSet == null) {
            getLogger().severe("Error creating marker set");
            return;
        }

        // 在地图上绘制一个圆形
        double x = 100;
        double y = 64;
        double z = 100;
        int radius = 10;

        for (int i = 0; i <= 360; i++) {
            double angle = i * Math.PI / 180;
            double dx = x + radius * Math.cos(angle);
            double dz = z + radius * Math.sin(angle);
            markerSet.createMarker("marker_" + i, "Marker " + i, "world", dx, y, dz, api.getMarkerAPI().getMarkerIcon("default"), false);
        }
    }
}

public final class Weathering extends JavaPlugin {

    @Override
    public void onEnable() {
        DynmapCommonAPIListener.register(new MyDynmapListener());

        CoreProtectAPI api = getCoreProtect();
        if (api != null) {
            List<File> mcaFiles = getMCAFiles("/opt/MinecraftServer-AHA/survival/world/region");
            for (File mcaFile : mcaFiles) {
                String[] parts = mcaFile.getName().split("\\.");
                int regionX = Integer.parseInt(parts[1]);
                int regionZ = Integer.parseInt(parts[2]);

                int startX = regionX * 512;
                int endX = startX + 511;
                int startZ = regionZ * 512;
                int endZ = startZ + 511;

                // 假设查询过去一年的数据
                int startTime = 0;
                int endTime = 365 * 86400;

                List<String[]> results = api.performLookup(endTime, null, null, Arrays.asList(startX, endX, startZ, endZ), 0);
                List<String[]> hasEvents = new ArrayList<>();
                List<String[]> noEvents = new ArrayList<>();

                for (String[] result : results) {
                    CoreProtectAPI.ParseResult parseResult = api.parseResult(result);
                    if (parseResult != null) {
                        hasEvents.add(result);
                    } else {
                        noEvents.add(result);
                    }
                }

                // 输出结果
                getLogger().info("有玩家事件的列表: " + hasEvents.size());
                getLogger().info("无玩家事件的列表: " + noEvents.size());
            }
        }
    }


    private CoreProtectAPI getCoreProtect() {
        Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");

        // Check that CoreProtect is loaded
        if (plugin == null || !(plugin instanceof CoreProtect)) {
            return null;
        }

        // Check that the API is enabled
        CoreProtectAPI CoreProtect = ((CoreProtect) plugin).getAPI();
        if (CoreProtect.isEnabled() == false) {
            return null;
        }

        // Check that a compatible version of the API is loaded
        if (CoreProtect.APIVersion() < 10) {
            return null;
        }

        return CoreProtect;
    }

    private List<File> getMCAFiles(String directoryPath) {
        File dir = new File(directoryPath);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".mca"));
        return files != null ? Arrays.asList(files) : Collections.emptyList();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
