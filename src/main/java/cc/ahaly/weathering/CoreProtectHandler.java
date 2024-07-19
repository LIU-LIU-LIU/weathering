package cc.ahaly.weathering;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CoreProtectHandler implements EventChecker {
    private final Plugin plugin;
    private final CoreProtectAPI coreProtectAPI;

    public CoreProtectHandler(Plugin plugin, CoreProtectAPI coreProtectAPI) {
        this.plugin = plugin;
        this.coreProtectAPI = coreProtectAPI;
    }

    @Override
    public List<String[]> getEventsInRegion(String mcaRegion, Location center, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList) {
        if (coreProtectAPI == null) {
            plugin.getLogger().severe("CoreProtect API is not available");
            return Collections.emptyList();
        }

        List<Object> mutableRestrictBlocks = new ArrayList<>(restrictBlocks);
        List<Object> mutableExcludeBlocks = new ArrayList<>(excludeBlocks);
        List<String> mutableRestrictUsers = new ArrayList<>(restrictUsers);
        List<String> mutableExcludeUsers = new ArrayList<>(excludeUsers);

        return coreProtectAPI.performLookup(Weathering.WEATHERING_TIME, mutableRestrictUsers, mutableExcludeUsers, mutableRestrictBlocks, mutableExcludeBlocks, actionList, 256, center);
    }

    public CompletableFuture<List<String[]>> getEventsInRegionAsync(String mcaRegion, Location center, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList) {
        CompletableFuture<List<String[]>> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    List<String[]> results = getEventsInRegion(mcaRegion, center, restrictUsers, excludeUsers, restrictBlocks, excludeBlocks, actionList);
                    future.complete(results);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error checking region: " + mcaRegion);
                    e.printStackTrace();
                    future.completeExceptionally(e);
                }
            }
        }.runTask(plugin);

        return future;
    }


    public CoreProtectAPI getCoreProtect() {
        Plugin coreProtectPlugin = plugin.getServer().getPluginManager().getPlugin("CoreProtect");

        if (coreProtectPlugin == null) {
            return null;
        }

        CoreProtectAPI coreProtectAPI = ((CoreProtect) coreProtectPlugin).getAPI();
        if (!coreProtectAPI.isEnabled()) {
            return null;
        }

        if (coreProtectAPI.APIVersion() < 10) {
            return null;
        }

        return coreProtectAPI;
    }
}
