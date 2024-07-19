package cc.ahaly.weathering;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandHandler implements CommandExecutor {
    private final Weathering plugin;
    private final EventChecker eventChecker;
    private final DynmapHandler dynmapHandler;
    private final List<File> mcaFiles;
    private final List<File> hasEvents;
    private final List<File> noEvents;
    private final boolean isDynmapEnabled;
    private final ExecutorService executor;

    public CommandHandler(Weathering plugin, EventChecker eventChecker, DynmapHandler dynmapHandler, List<File> mcaFiles, List<File> hasEvents, List<File> noEvents, boolean isDynmapEnabled) {
        this.plugin = plugin;
        this.eventChecker = eventChecker;
        this.dynmapHandler = dynmapHandler;
        this.mcaFiles = mcaFiles;
        this.hasEvents = Collections.synchronizedList(new ArrayList<>(hasEvents));
        this.noEvents = Collections.synchronizedList(new ArrayList<>(noEvents));
        this.isDynmapEnabled = isDynmapEnabled;
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
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
                    boolean hasEventsInRegion = checkEventsInRegionSync(mcaFile.getName());

                    synchronized (hasEvents) {
                        if (hasEventsInRegion) {
                            hasEvents.add(mcaFile);
                        } else {
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

    private boolean checkEventsInRegionSync(String mcaRegion) {
        try {
            String[] parts = mcaRegion.split("\\.");
            int regionX = Integer.parseInt(parts[1]);
            int regionZ = Integer.parseInt(parts[2]);

            int startX = regionX * 512;
            int startZ = regionZ * 512;
            Location center = new Location(plugin.getServer().getWorld("world"), startX + 256, 64, startZ + 256);

            List<String> restrictUsers = Collections.emptyList();
            List<String> excludeUsers = Collections.emptyList();
            List<Object> restrictBlocks = Collections.emptyList();
            List<Object> excludeBlocks = Collections.emptyList();
            List<Integer> actionList = Arrays.asList(1, 2, 6);

            List<String[]> results = eventChecker.getEventsInRegion(mcaRegion, center, restrictUsers, excludeUsers, restrictBlocks, excludeBlocks, actionList);

            return processResults(results);
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking region: " + mcaRegion);
            e.printStackTrace();
            return false;
        }
    }

    private boolean processResults(List<String[]> results) {
        int blockActivityCount = 0;
        int interactionActivityCount = 0;

        for (String[] result : results) {
            int actionType = Integer.parseInt(result[0]);
            int count = Integer.parseInt(result[1]);

            if (actionType == 1 || actionType == 2) {
                blockActivityCount += count;
            } else if (actionType == 6) {
                interactionActivityCount += count;
            }
        }

        return blockActivityCount > 128 || interactionActivityCount > 64;
    }

    private Location getCenterLocation(String mcaRegion) {
        String[] parts = mcaRegion.split("\\.");
        int regionX = Integer.parseInt(parts[1]);
        int regionZ = Integer.parseInt(parts[2]);

        int startX = regionX * 512;
        int startZ = regionZ * 512;
        return new Location(plugin.getServer().getWorld("world"), startX + 256, 64, startZ + 256);
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
        sender.sendMessage("在服务器运行中无法通过删除mca重置，请先运行list命令生成文件列表然后使用插件目录的delete_files.sh脚本进行手动删除，");
    }

    private BukkitTask remindTask;
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

        if (remindTask != null && !remindTask.isCancelled()) {
            sender.sendMessage("提醒任务已经在运行。");
            return;
        }

        remindTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location location = player.getLocation();
                    String mcaRegion = getMCARegion(location);

                    boolean willBeWeathered = !checkEventsInRegionSync(mcaRegion);
                    if (willBeWeathered) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("你所在的区域将会被风化,注意活动!"));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, interval * 20L); // 使用正确的 plugin 对象

        sender.sendMessage("提醒任务已启动，时间间隔为 " + interval + " 秒。");
    }

    private void handleQueryCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                Location location = player.getLocation();
                String mcaRegion = getMCARegion(location);
                handleRegionCheckAsync(sender, mcaRegion);
            } else {
                sender.sendMessage("此命令只允许玩家使用。");
            }
        } else if (args.length == 4 && args[1].equalsIgnoreCase("mca")) {
            String mcaRegion = "r." + args[2] + "." + args[3] + ".mca";
            handleRegionCheckAsync(sender, mcaRegion);
        } else {
            sender.sendMessage("用法: /weathering query [player|mca <x> <z>]");
        }
    }

    private void handleRegionCheckAsync(CommandSender sender, String mcaRegion) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> handleRegionCheck(sender, mcaRegion));
    }

    private void handleRegionCheck(CommandSender sender, String mcaRegion) {
        Location center = getCenterLocation(mcaRegion);

        List<String[]> results = eventChecker.getEventsInRegion(mcaRegion, center, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Arrays.asList(1, 2, 6));
        boolean hasEvents = processResults(results);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (hasEvents) {
                sender.sendMessage("MCA 区域 " + mcaRegion + " 有玩家活动，不会被风化。");
            } else {
                sender.sendMessage("MCA 区域 " + mcaRegion + " 没有玩家活动，会被风化。");
            }
        });
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
