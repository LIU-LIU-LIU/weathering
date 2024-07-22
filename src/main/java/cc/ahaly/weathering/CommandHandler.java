package cc.ahaly.weathering;

import cc.ahaly.weathering.check.EventChecker;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static cc.ahaly.weathering.util.McaTransform.*;

public class CommandHandler implements CommandExecutor {
    private final Weathering plugin;
    private final DynmapHandler dynmapHandler;
    private final EventChecker eventChecker;
    private final List<File> mcaFiles;
    private final List<File> hasEvents;
    private final List<File> noEvents;
    private final ExecutorService executor;

    public CommandHandler(Weathering plugin, DynmapHandler dynmapHandler, EventChecker eventChecker, List<File> mcaFiles, List<File> hasEvents, List<File> noEvents) {
        this.plugin = plugin;
        this.dynmapHandler = dynmapHandler;
        this.eventChecker = eventChecker;
        this.mcaFiles = mcaFiles;
        this.hasEvents = Collections.synchronizedList(new ArrayList<>(hasEvents));
        this.noEvents = Collections.synchronizedList(new ArrayList<>(noEvents));
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
        sender.sendMessage("此命令需要较长时间加载，请等待...");

        // 使用并发集合来处理线程安全问题
        ConcurrentLinkedQueue<File> hasEvents = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<File> noEvents = new ConcurrentLinkedQueue<>();

        int totalFiles = mcaFiles.size();
        int batchSize = 50; // 每批处理的文件数量
        AtomicInteger processedFiles = new AtomicInteger();
        AtomicBoolean isShuttingDown = new AtomicBoolean(false);

        // 使用固定大小的线程池
        int poolSize = Math.min(10, totalFiles / batchSize + 1); // 线程池大小，至少1，最多10
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        for (int i = 0; i < totalFiles; i += batchSize) {
            int start = i;
            int end = Math.min(i + batchSize, totalFiles);

            executor.submit(() -> {
                try {
                    for (int j = start; j < end; j++) {
                        File mcaFile = mcaFiles.get(j);
                        boolean hasEventsInRegion = eventChecker.getEventsInRegion(mcaFile.getName());
                        // 打印信息
                        plugin.getLogger().info("正在检查 " + mcaFile.getName() + " 区域的事件 " + hasEventsInRegion);

                        if (hasEventsInRegion) {
                            hasEvents.add(mcaFile);
                        } else {
                            noEvents.add(mcaFile);
                        }
                    }

                    int processed = processedFiles.addAndGet((end - start));
                    sender.sendMessage("已处理 " + processed + " / " + totalFiles + " 个文件...");
                    plugin.getLogger().info("已处理 " + processed + " / " + totalFiles + " 个文件...");

                    if (processed >= totalFiles && isShuttingDown.compareAndSet(false, true)) {
                        // 将 ConcurrentLinkedQueue 转换为 ArrayList
                        List<File> hasEventsList = new ArrayList<>(hasEvents);
                        List<File> noEventsList = new ArrayList<>(noEvents);

                        // 将有玩家事件和无玩家事件的列表写入文件
                        writeFileAsync("hasEvents.txt", hasEventsList, () -> {
                            writeFileAsync("noEvents.txt", noEventsList, () -> {
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        sender.sendMessage("查询完成。" + hasEvents.size() + " 个区域有玩家事件，" + noEvents.size() + " 个区域无玩家事件。结果已写入文件。");
                                    }
                                }.runTask(plugin);
                            });
                        });

                        // 关闭线程池
                        executor.shutdown();
                        try {
                            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                                executor.shutdownNow();
                            }
                        } catch (InterruptedException e) {
                            executor.shutdownNow();
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("处理文件时发生错误: " + e.getMessage());
                    e.printStackTrace();
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
                    if (eventChecker.getEventsInRegion(mcaRegion)) {
                        player.sendMessage("你所在的区域将会被风化,注意活动!");
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
        sender.sendMessage("此命令需要较长时间加载，请等待...");
        new BukkitRunnable() {
            @Override
            public void run() {
                boolean hasEvents = eventChecker.getEventsInRegion(mcaRegion);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (hasEvents) {
                            sender.sendMessage(mcaRegion + " 该区域有玩家活动。");
                        } else {
                            sender.sendMessage(mcaRegion + " 该区域无玩家活动。");
                        }
                    }
                }.runTask(plugin); // 在主线程中运行
            }
        }.runTaskAsynchronously(plugin); // 在异步线程中运行
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
        for (File mcaFile : hasEvents) {
            drawRegion(mcaFile, true);
        }
        plugin.getLogger().info("绘制完成。" + hasEvents.size() + " 个活跃区域已绘制。");
    }

    private void drawRegionsWithoutEvents() {
        for (File mcaFile : noEvents) {
            drawRegion(mcaFile, false);
        }
        plugin.getLogger().info("绘制完成。" + noEvents.size() + " 个不活跃区域已绘制。");
    }

    private void clearDrawnRegions() {
        dynmapHandler.clearDrawnRegions();
        plugin.getLogger().info("清除完成。");
    }


    private void drawRegion(File mcaFile, boolean hasEvents) {
        Location center = getCenterLocation(plugin, mcaFile.getName());
        dynmapHandler.drawSquareRegion("world", center.getX(), center.getY(), center.getZ(), 512, hasEvents);
    }
}
