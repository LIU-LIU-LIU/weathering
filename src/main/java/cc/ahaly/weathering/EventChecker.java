package cc.ahaly.weathering;

import org.bukkit.Location;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EventChecker {
    CompletableFuture<List<String[]>> getEventsInRegion(String mcaRegion, Location center, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList);
}
