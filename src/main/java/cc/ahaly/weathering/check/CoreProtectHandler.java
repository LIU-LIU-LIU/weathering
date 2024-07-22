package cc.ahaly.weathering.check;

import cc.ahaly.weathering.Weathering;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cc.ahaly.weathering.util.McaTransform.getCenterLocation;

public class CoreProtectHandler implements  EventChecker{
    private final Plugin plugin;
    private final CoreProtectAPI coreProtectAPI;

    public CoreProtectHandler(Plugin plugin, CoreProtectAPI coreProtectAPI) {
        this.plugin = plugin;
        this.coreProtectAPI = coreProtectAPI;
    }

    @Override
    public boolean getEventsInRegion(String mcaRegion) {
        if (coreProtectAPI == null) {
            plugin.getLogger().severe("CoreProtect API 不可用。");
            return false;
        }

        //排除列表
        List<Object> excludeBlocks = Arrays.asList(
                Material.SHORT_GRASS, Material.TALL_GRASS,
                Material.FERN, Material.LARGE_FERN, Material.SNOW, Material.SEAGRASS, Material.KELP
        );
        //注意这个活动列表在api中会进行修改，务必使用new ArrayList<>()创建可修改的列表之前报错了很久，
        List<Integer> actionList = new ArrayList<>(Arrays.asList(0, 1, 2));// // 0: 破坏方块, 1: 放置方块, 2: 交互, 3: 击杀

        Location center = getCenterLocation(plugin, mcaRegion);
        List<String[]> results = coreProtectAPI.performLookup(Weathering.WEATHERING_TIME, null, null, null, excludeBlocks, actionList, 256 , center);

        int blockActivityCount = 0;     // 放置方块或拆除方块的次数
        int interactionActivityCount = 0;    // 交互的次数

        for (String[] result : results) {
            try {
                int actionType = coreProtectAPI.parseResult(result).getActionId();
                if (actionType == 0 || actionType == 1) {
                    blockActivityCount ++;
                } else if (actionType == 2) {
                    interactionActivityCount ++;
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("解析结果时发生错误: " + e.getMessage());
            }
        }
        plugin.getLogger().info("该区域 方块事件: " + blockActivityCount + " 交互事件: " + interactionActivityCount);
        return blockActivityCount > 256 || interactionActivityCount > 64;
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
