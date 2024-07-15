package cc.ahaly.weathering;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static cc.ahaly.weathering.Weathering.WEATHERING_TIME;

public class CommandHandler implements CommandExecutor {

    private final Weathering plugin;
    private final CoreProtectHandler coreProtectHandler;
    private final DynmapHandler dynmapHandler;
    private final List<File> mcaFiles;
    private final List<File> hasEvents;
    private final List<File> noEvents;
    private final boolean isDynmapEnabled;

    private final ThreadPoolExecutor executor;

    public CommandHandler(Weathering plugin, CoreProtectHandler coreProtectHandler, DynmapHandler dynmapHandler, List<File> mcaFiles, List<File> hasEvents, List<File> noEvents, boolean isDynmapEnabled) {
        this.plugin = plugin;
        this.coreProtectHandler = coreProtectHandler;
        this.dynmapHandler = dynmapHandler;
        this.mcaFiles = mcaFiles;
        this.hasEvents = hasEvents;
        this.noEvents = noEvents;
        this.isDynmapEnabled = isDynmapEnabled;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("weathering")) {
            if (args.length == 0) {
                sender.sendMessage("用法: /weathering <query|list|draw|reset|remind>");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "query":
                    handleQueryCommand(sender, args);
                    return true;
                case "list":
                    handleListCommandAsync(sender);
                    return true;
                case "draw":
                    handleDrawCommand(sender, args);
                    return true;
                case "reset":
                    handleResetCommand(sender);
                    return true;
                case "remind":
                    handleRemindCommand(sender, args);
                    return true;
                default:
                    sender.sendMessage("未知命令. 用法: /weathering <query|list|draw|reset|remind>");
                    return true;
            }
        }
        return false;
    }

    private void handleListCommandAsync(CommandSender sender) {
        new BukkitRunnable() {
            @Override
            public void run() {
                handleListCommand(sender);
            }
        }.runTaskAsynchronously(plugin);
    }

    private void handleListCommand(CommandSender sender) {
        sender.sendMessage("此命令需要较长时间加载，请等待...");

        hasEvents.clear();
        noEvents.clear();

        int totalFiles = mcaFiles.size();
        int batchSize = 100; // 每批处理的文件数量
        AtomicInteger processedFiles = new AtomicInteger();

        for (int i = 0; i < totalFiles; i += batchSize) {
            int start = i;
            int end = Math.min(i + batchSize, totalFiles);

            executor.submit(() -> {
                for (int j = start; j < end; j++) {
                    File mcaFile = mcaFiles.get(j);
                    if (checkEventsInRegion(mcaFile.getName())) {
                        synchronized (hasEvents) {
                            hasEvents.add(mcaFile);
                        }
                    } else {
                        synchronized (noEvents) {
                            noEvents.add(mcaFile);
                        }
                    }
                }

                synchronized (sender) {
                    processedFiles.addAndGet((end - start));
                    sender.sendMessage("已处理 " + processedFiles + " / " + totalFiles + " 个文件...");
                }

                if (processedFiles.get() >= totalFiles) {
                    // 将有玩家事件和无玩家事件的列表写入文件
                    writeFileAsync("hasEvents.txt", hasEvents, () -> {
                        writeFileAsync("noEvents.txt", noEvents, () -> {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    sender.sendMessage("查询完成。" + hasEvents.size() + " 个区域有玩家事件，" + noEvents.size() + " 个区域无玩家事件。结果已写入文件。");
                                }
                            }.runTask(plugin);
                        });
                    });
                }
            });
        }
    }

    private void writeFileAsync(String fileName, List<File> files, Runnable callback) {
        executor.submit(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(plugin.getDataFolder(), fileName)))) {
                for (File file : files) {
                    writer.write(file.getName());
                    writer.newLine();
                }
                writer.flush();
            } catch (IOException e) {
                plugin.getLogger().severe("写入文件 " + fileName + " 时出错: " + e.getMessage());
            } finally {
                callback.run();
            }
        });
    }

    private void handleResetCommand(CommandSender sender) {
        int resetCount = 0;
        for (File mcaFile : noEvents) {
            // 获取区域坐标
            String[] parts = mcaFile.getName().split("\\.");
            int regionX = Integer.parseInt(parts[1]);
            int regionZ = Integer.parseInt(parts[2]);

            World world = Bukkit.getWorld("world");
            if (world == null) {
                plugin.getLogger().severe("世界 'world' 未找到。");
                return;
            }

            boolean hasPlayers = false;
            for (Player player : world.getPlayers()) {
                Location loc = player.getLocation();
                int playerRegionX = loc.getBlockX() >> 9;
                int playerRegionZ = loc.getBlockZ() >> 9;
                if (playerRegionX == regionX && playerRegionZ == regionZ) {
                    hasPlayers = true;
                    player.sendMessage("你所在的区域即将被重置，请暂时离开该区域。");
                }
            }

            if (hasPlayers) {
                sender.sendMessage("区域 " + regionX + "," + regionZ + " 有玩家存在，无法重置。");
                continue;
            }

            // 卸载区域内所有区块
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    int chunkX = (regionX * 32) + x;
                    int chunkZ = (regionZ * 32) + z;
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    if (world.isChunkLoaded(chunk)) {
                        world.unloadChunk(chunk);
                    }
                }
            }

            // 删除文件
            if (mcaFile.delete()) {
                resetCount++;
            } else {
                plugin.getLogger().severe("删除文件失败: " + mcaFile.getName());
            }
        }
        sender.sendMessage("重置了 " + resetCount + " 个区域。");
    }

    private void handleRemindCommand(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("用法: /weathering remind <间隔时间>");
            return;
        }

        int interval;
        try {
            interval = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("无效的时间间隔。");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location location = player.getLocation();
                    String mcaRegion = getMCARegion(location);
                    boolean willBeWeathered = !checkEventsInRegion(mcaRegion);
                    if (willBeWeathered) {
                        player.sendTitle("", "你所在的区域将会被风化!", 10, 70, 20);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, interval * 20L); // interval * 20L 转换为 tick（1秒 = 20 tick）

        sender.sendMessage("提醒任务已启动，时间间隔为 " + interval + " 秒。");
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
                sender.sendMessage("此命令只允许玩家使用。");
            }
        } else if (args.length == 4 && args[1].equalsIgnoreCase("mca")) {
            // 传入了mca坐标
            String mcaRegion = "r." + args[2] + "." + args[3] + ".mca";
            queryEventsForRegion(sender, mcaRegion);
        } else {
            sender.sendMessage("用法: /weathering query [player|mca <x> <z>]");
        }
    }

    private void handleDrawCommand(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("用法: /weathering draw <events|weathers|clear>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "events":
                drawRegionsWithEvents();
                sender.sendMessage("绘制有事件的区域。");
                break;
            case "weathers":
                drawRegionsWithoutEvents();
                sender.sendMessage("绘制无事件的区域。");
                break;
            case "clear":
                clearDrawnRegions();
                sender.sendMessage("已清除绘制的区域。");
                break;
            default:
                sender.sendMessage("未知绘制命令。用法: /weathering draw <events|weathers|clear>");
                break;
        }
    }

    private void queryEventsForRegion(CommandSender sender, String mcaRegion) {
        boolean hasEvents = checkEventsInRegion(mcaRegion);
        if (hasEvents) {
            sender.sendMessage("MCA 区域 " + mcaRegion + " 有玩家活动，不会被风化。");
        } else {
            sender.sendMessage("MCA 区域 " + mcaRegion + " 没有玩家活动，会被风化。");
        }
    }

    private boolean checkEventsInRegion(String mcaRegion) {
        CoreProtectAPI coreProtectAPI = coreProtectHandler.getCoreProtect();
        if (coreProtectAPI == null) {
            plugin.getLogger().severe("CoreProtect API 不可用。");
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

    private void drawRegionsWithEvents() {
        if (!isDynmapEnabled || dynmapHandler == null) {
            plugin.getLogger().severe("Dynmap 未启用或不可用。");
            return;
        }
        for (File mcaFile : hasEvents) {
            drawRegion(mcaFile, true);
        }
        plugin.getLogger().info("绘制完成。" + hasEvents.size() + " 个活跃区域已绘制。");
    }

    private void drawRegionsWithoutEvents() {
        if (!isDynmapEnabled || dynmapHandler == null) {
            plugin.getLogger().severe("Dynmap 未启用或不可用。");
            return;
        }
        for (File mcaFile : noEvents) {
            drawRegion(mcaFile, false);
        }
        plugin.getLogger().info("绘制完成。" + noEvents.size() + " 个不活跃区域已绘制。");
    }

    private void clearDrawnRegions() {
        if (!isDynmapEnabled || dynmapHandler == null) {
            plugin.getLogger().severe("Dynmap 未启用或不可用。");
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

    private void drawRegion(File mcaFile, boolean hasEvents) {
        String[] parts = mcaFile.getName().split("\\.");
        int regionX = Integer.parseInt(parts[1]);
        int regionZ = Integer.parseInt(parts[2]);

        int startX = regionX * 512;
        int startZ = regionZ * 512;
        Location center = new Location(plugin.getServer().getWorld("world"), startX + 256, 64, startZ + 256);

        dynmapHandler.drawSquareRegion("world", center.getX(), center.getY(), center.getZ(), 512, hasEvents);
    }
}
