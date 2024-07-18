package cc.ahaly.weathering;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;


public class CoreProtectHandler implements EventChecker {
    private final Plugin plugin;
    private final CoreProtectAPI coreProtectAPI;

    public CoreProtectHandler(Plugin plugin, CoreProtectAPI coreProtectAPI) {
        this.plugin = plugin;
        this.coreProtectAPI = coreProtectAPI;
    }

    @Override
    public CompletableFuture<List<String[]>> getEventsInRegion(String mcaRegion, Location center, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList) {
        return CompletableFuture.supplyAsync(() -> {
            if (coreProtectAPI == null) {
                plugin.getLogger().log(Level.SEVERE, "CoreProtect API is null");
                return Collections.emptyList();
            }
            List<String[]> results = performLookup(coreProtectAPI, Weathering.WEATHERING_TIME, center, 256, restrictUsers, excludeUsers, restrictBlocks, excludeBlocks, actionList);
            plugin.getLogger().log(Level.INFO, "CoreProtectHandler results size: " + results.size());
            return results;
        });
    }


    public List<String[]> performLookup(CoreProtectAPI api, int time, Location center, int radius, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList) {
        if (api == null) {
            plugin.getLogger().severe("CoreProtect API is not available");
            return Collections.emptyList();
        }

        List<Object> mutableRestrictBlocks = new ArrayList<>(restrictBlocks);
        List<Object> mutableExcludeBlocks = new ArrayList<>(excludeBlocks);
        List<String> mutableRestrictUsers = new ArrayList<>(restrictUsers);
        List<String> mutableExcludeUsers = new ArrayList<>(excludeUsers);

        return api.performLookup(time, mutableRestrictUsers, mutableExcludeUsers, mutableRestrictBlocks, mutableExcludeBlocks, actionList, radius, center);
    }


    public CoreProtectAPI getCoreProtect() {
        Plugin coreProtectPlugin = plugin.getServer().getPluginManager().getPlugin("CoreProtect");

        // Check that CoreProtect is loaded
        if (!(coreProtectPlugin instanceof CoreProtect)) {
            return null;
        }

        // Check that the API is enabled
        CoreProtectAPI coreProtectAPI = ((CoreProtect) coreProtectPlugin).getAPI();
        if (!coreProtectAPI.isEnabled()) {
            return null;
        }

        // Check that a compatible version of the API is loaded
        if (coreProtectAPI.APIVersion() < 10) {
            return null;
        }

        return coreProtectAPI;
    }
}
