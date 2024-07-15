package cc.ahaly.weathering;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.coreprotect.CoreProtectAPI;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandHandler implements CommandExecutor {

    private final Weathering plugin;
    private final CoreProtectHandler coreProtectHandler;
    private final DynmapHandler dynmapHandler;
    private final List<File> mcaFiles;
    private final List<File> hasEvents;
    private final List<File> noEvents;
    private final boolean isDynmapEnabled;
//    private static final int WEATHERING_TIME = 2 * 365 * 86400; // 风化时间，单位为秒
    private static final int WEATHERING_TIME = 7 * 86400;

    public CommandHandler(Weathering plugin, CoreProtectHandler coreProtectHandler, DynmapHandler dynmapHandler, List<File> mcaFiles, List<File> hasEvents, List<File> noEvents, boolean isDynmapEnabled) {
        this.plugin = plugin;
        this.coreProtectHandler = coreProtectHandler;
        this.dynmapHandler = dynmapHandler;
        this.mcaFiles = mcaFiles;
        this.hasEvents = hasEvents;
        this.noEvents = noEvents;
        this.isDynmapEnabled = isDynmapEnabled;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("weathering")) {
            if (args.length == 0) {
                sender.sendMessage("Usage: /weathering <query|list|draw>");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "query":
                    handleQueryCommand(sender, args);
                    return true;
                case "list":
                    handleListCommand(sender);
                    return true;
                case "draw":
                    handleDrawCommand(sender, args);
                    return true;
                default:
                    sender.sendMessage("Unknown command. Usage: /weathering <query|list|draw>");
                    return true;
            }
        }
        return false;
    }

    private void handleQueryCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // 没有传入参数，查询玩家所在的mca区域
            if (sender instanceof Player) {
                Player player = (Player) sender;
                Location location = player.getLocation();
                String mcaRegion = getMCARegion(location);
                queryEventsForRegion(sender, mcaRegion);
            } else {
                sender.sendMessage("This command can only be used by a player.");
            }
        } else if (args.length == 4 && args[1].equalsIgnoreCase("mca")) {
            // 传入了mca坐标
            String mcaRegion = "r." + args[2] + "." + args[3] + ".mca";
            queryEventsForRegion(sender, mcaRegion);
        } else {
            sender.sendMessage("Usage: /weathering query [player|mca <x> <z>]");
        }
    }

    private void handleDrawCommand(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /weathering draw <events|weathers|clear>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "events":
                drawRegionsWithEvents();
                sender.sendMessage("Drawing regions with events.");
                break;
            case "weathers":
                drawRegionsWithoutEvents();
                sender.sendMessage("Drawing regions without events.");
                break;
            case "clear":
                clearDrawnRegions();
                sender.sendMessage("Cleared drawn regions.");
                break;
            default:
                sender.sendMessage("Unknown draw command. Usage: /weathering draw <events|weathers|clear>");
                break;
        }
    }

    private void queryEventsForRegion(CommandSender sender, String mcaRegion) {
        boolean hasEvents = checkEventsInRegion(mcaRegion);
        if (hasEvents) {
            sender.sendMessage("MCA region " + mcaRegion + " has events and will not be weathered.");
        } else {
            sender.sendMessage("MCA region " + mcaRegion + " will be weathered in 2 years.");
        }
    }

    private void handleListCommand(CommandSender sender) {
        hasEvents.clear();
        noEvents.clear();

        for (File mcaFile : mcaFiles) {
            if (checkEventsInRegion(mcaFile.getName())) {
                hasEvents.add(mcaFile);
            } else {
                noEvents.add(mcaFile);
            }
        }

        // 将有玩家事件和无玩家事件的列表写入文件
        writeFile("hasEvents.txt", hasEvents);
        writeFile("noEvents.txt", noEvents);
        sender.sendMessage("查询完成。" + hasEvents.size() + " 个区域有玩家事件，" + noEvents.size() + " 个区域无玩家事件。" + " 已写入文件。");
    }

    private void drawRegionsWithEvents() {
        if (!isDynmapEnabled || dynmapHandler == null) {
            plugin.getLogger().severe("Dynmap is not enabled or not available.");
            return;
        }
        for (File mcaFile : hasEvents) {
            drawRegion(mcaFile, true);
        }
        plugin.getLogger().info("绘制完成。" + hasEvents.size() + " 个活跃区域已绘制。");
    }

    private void drawRegionsWithoutEvents() {
        if (!isDynmapEnabled || dynmapHandler == null) {
            plugin.getLogger().severe("Dynmap is not enabled or not available.");
            return;
        }
        for (File mcaFile : noEvents) {
            drawRegion(mcaFile, false);
        }
        plugin.getLogger().info("绘制完成。" + noEvents.size() + " 个不活跃区域已绘制。");
    }

    private void clearDrawnRegions() {
        if (!isDynmapEnabled || dynmapHandler == null) {
            plugin.getLogger().severe("Dynmap is not enabled or not available.");
            return;
        }
        dynmapHandler.clearDrawnRegions();
        plugin.getLogger().info("清除完成。");
    }

    private String getMCARegion(Location location) {
        int regionX = location.getBlockX() >> 9;
        int regionZ = location.getBlockZ() >> 9;
        return "r." + regionX + "." + regionZ + ".mca";
    }

    private boolean checkEventsInRegion(String mcaRegion) {
        CoreProtectAPI coreProtectAPI = coreProtectHandler.getCoreProtect();
        if (coreProtectAPI == null) {
            plugin.getLogger().severe("CoreProtect API is not available.");
            return false;
        }

        List<String> restrictUsers = Collections.emptyList();
        List<String> excludeUsers = Collections.emptyList();
        List<Object> restrictBlocks = Collections.emptyList();
        List<Object> excludeBlocks = Collections.emptyList();
        List<Integer> actionList = Arrays.asList(1, 2); // 1: 放置方块, 2: 拆除方块

        String[] parts = mcaRegion.split("\\.");
        int regionX = Integer.parseInt(parts[1]);
        int regionZ = Integer.parseInt(parts[2]);

        int startX = regionX * 512;
        int startZ = regionZ * 512;
        Location center = new Location(plugin.getServer().getWorld("world"), startX + 256, 64, startZ + 256);

        List<String[]> results = coreProtectHandler.performLookup(coreProtectAPI, WEATHERING_TIME, center, 256, restrictUsers, excludeUsers, restrictBlocks, excludeBlocks, actionList);

        for (String[] result : results) {
            CoreProtectAPI.ParseResult parseResult = coreProtectAPI.parseResult(result);
            if (parseResult != null) {
                return true;
            }
        }
        return false;
    }

    private void drawRegion(File mcaFile, boolean hasEvents) {
        String[] parts = mcaFile.getName().split("\\.");
        int regionX = Integer.parseInt(parts[1]);
        int regionZ = Integer.parseInt(parts[2]);

        int startX = regionX * 512;
        int startZ = regionZ * 512;
        Location center = new Location(plugin.getServer().getWorld("world"), startX + 256, 64, startZ + 256);

        dynmapHandler.drawSquareRegion("world", center.getX(), center.getY(), center.getZ(), 512, hasEvents);
    }

    private void writeFile(String fileName, List<File> files) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(plugin.getDataFolder(), fileName)))) {
            for (File file : files) {
                writer.write(file.getName());
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            plugin.getLogger().severe("Error writing to file " + fileName + ": " + e.getMessage());
        }
    }
}
