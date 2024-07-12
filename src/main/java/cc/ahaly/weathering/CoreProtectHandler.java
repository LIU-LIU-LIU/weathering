package cc.ahaly.weathering;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CoreProtectHandler {
    private final Plugin plugin;

    public CoreProtectHandler(Plugin plugin) {
        this.plugin = plugin;
    }

    public List<String[]> performLookup(CoreProtectAPI api, int time, Location center, int radius, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList) {
        if (api == null) {
            plugin.getLogger().severe("CoreProtect API is not available");
            return Collections.emptyList();
        }

        // 使用可变的列表
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
