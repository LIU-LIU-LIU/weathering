package cc.ahaly.weathering;

import org.bukkit.Location;

import java.util.List;

public interface EventChecker {
    List<String[]> getEventsInRegion(String mcaRegion, Location center, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList);
}
