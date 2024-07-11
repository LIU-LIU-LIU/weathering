package cc.ahaly.weathering;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import net.coreprotect.CoreProtectAPI;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Weathering extends JavaPlugin {

    @Override
    public void onEnable() {
        DynmapHandler dynmapHandler = new DynmapHandler();
        DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI api) {
                dynmapHandler.handleApiEnabled(api);
            }
        });

        CoreProtectHandler coreProtectHandler = new CoreProtectHandler(this);
        CoreProtectAPI api = coreProtectHandler.getCoreProtect();
        if (api != null) {
//            List<File> mcaFiles = getMCAFiles("/opt/MinecraftServer-AHA/survival/world/region");
            List<File> mcaFiles = Arrays.asList(
                    new File("/opt/MinecraftServer-AHA/survival/world/region/r.1.-2.mca"),
                    new File("/opt/MinecraftServer-AHA/survival/world/region/r.2.0.mca"),
                    new File("/opt/MinecraftServer-AHA/survival/world/region/r.3.-5.mca"),
                    new File("/opt/MinecraftServer-AHA/survival/world/region/r.5.1.mca")
            );
//            int time = 365 * 86400; // 过去一年的数据
            int time = 1 * 86400;
            List<String> restrictUsers = Collections.emptyList();
            List<String> excludeUsers = Collections.emptyList();
            List<Object> restrictBlocks = Collections.emptyList();
            List<Object> excludeBlocks = Collections.emptyList();
            List<Integer> actionList = Arrays.asList(1, 2); // 1: 放置方块, 2: 拆除方块

            List<File> hasEvents = new ArrayList<>();
            List<File> noEvents = new ArrayList<>();

            for (File mcaFile : mcaFiles) {
                String[] parts = mcaFile.getName().split("\\.");
                int regionX = Integer.parseInt(parts[1]);
                int regionZ = Integer.parseInt(parts[2]);

                int startX = regionX * 512;
                int startZ = regionZ * 512;
                Location center = new Location(getServer().getWorld("world"), startX + 256, 64, startZ + 256);

                List<String[]> results = coreProtectHandler.performLookup(api, time, center, 256, restrictUsers, excludeUsers, restrictBlocks, excludeBlocks, actionList);

                boolean hasPlayerEvents = false;
                for (String[] result : results) {
                    CoreProtectAPI.ParseResult parseResult = api.parseResult(result);
                    if (parseResult != null) {
                        hasPlayerEvents = true;
                        break;
                    }
                }

                if (hasPlayerEvents) {
                    hasEvents.add(mcaFile);
                } else {
                    noEvents.add(mcaFile);
                }
            }

            getLogger().info("有玩家事件的列表: " + hasEvents.size());
            getLogger().info("无玩家事件的列表: " + noEvents.size());

            // 使用 DynmapHandler 绘制区域
            for (File mcaFile : hasEvents) {
                String[] parts = mcaFile.getName().split("\\.");
                int regionX = Integer.parseInt(parts[1]);
                int regionZ = Integer.parseInt(parts[2]);

                int startX = regionX * 512;
                int startZ = regionZ * 512;
                Location center = new Location(getServer().getWorld("world"), startX + 256, 64, startZ + 256);

                dynmapHandler.drawSquareRegion("world", center.getX(), center.getY(), center.getZ(), 512);
            }
        }
    }

    public List<File> getMCAFiles(String directoryPath) {
        File dir = new File(directoryPath);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".mca"));
        return files != null ? Arrays.asList(files) : Collections.emptyList();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
