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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
        sender.sendMessage("========== 开始扫描区域 ==========");
        plugin.getLogger().info("========== 开始扫描区域 ==========");
        
        // 显示MCA文件总数
        int totalFiles = mcaFiles.size();
        sender.sendMessage("§e世界区域文件总数: §a" + totalFiles + " §e个MCA文件");
        plugin.getLogger().info("世界区域文件总数: " + totalFiles + " 个MCA文件");

        // 读取缓存文件
        File cacheFile = new File(plugin.getDataFolder(), "region_cache.txt");
        Map<String, CacheEntry> cacheMap = new ConcurrentHashMap<>();
        long currentTime = System.currentTimeMillis();
        long cacheExpireMillis = Weathering.CACHE_EXPIRE_DAYS * 86400000L;

        // 加载缓存
        if (cacheFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(cacheFile.toPath());
                for (String line : lines) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 3) {
                        String fileName = parts[0];
                        long timestamp = Long.parseLong(parts[1]);
                        boolean hasEvents = Boolean.parseBoolean(parts[2]);
                        cacheMap.put(fileName, new CacheEntry(timestamp, hasEvents));
                    }
                }
                sender.sendMessage("§e已加载缓存记录: §a" + cacheMap.size() + " §e条");
                plugin.getLogger().info("已加载缓存记录: " + cacheMap.size() + " 条");
            } catch (IOException e) {
                plugin.getLogger().severe("读取缓存文件失败: " + e.getMessage());
            }
        } else {
            sender.sendMessage("§e未找到缓存文件，将全量扫描");
            plugin.getLogger().info("未找到缓存文件，将全量扫描");
        }

        // 过滤需要检查的文件（不在缓存中或缓存过期）
        List<File> filesToCheck = new ArrayList<>();
        ConcurrentLinkedQueue<File> hasEventsQueue = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<File> noEventsQueue = new ConcurrentLinkedQueue<>();
        int skippedBySize = 0;

        for (File mcaFile : mcaFiles) {
            // 检查文件大小，小于阈值的直接跳过
            if (Weathering.MIN_FILE_SIZE_BYTES > 0 && mcaFile.length() < Weathering.MIN_FILE_SIZE_BYTES) {
                noEventsQueue.add(mcaFile);
                skippedBySize++;
                continue;
            }
            
            CacheEntry cache = cacheMap.get(mcaFile.getName());
            if (cache == null || (currentTime - cache.timestamp) > cacheExpireMillis) {
                filesToCheck.add(mcaFile);
            } else {
                // 使用缓存的结果
                if (cache.hasEvents) {
                    hasEventsQueue.add(mcaFile);
                } else {
                    noEventsQueue.add(mcaFile);
                }
            }
        }

        int cachedFiles = totalFiles - filesToCheck.size() - skippedBySize;
        sender.sendMessage("§e├─ 直接使用缓存: §a" + cachedFiles + " §e个区域");
        if (skippedBySize > 0) {
            sender.sendMessage("§e├─ 文件过小跳过: §7" + skippedBySize + " §e个区域 §7(小于 " + (Weathering.MIN_FILE_SIZE_BYTES / 1024) + "KB)");
            plugin.getLogger().info("├─ 文件过小跳过: " + skippedBySize + " 个区域 (小于 " + (Weathering.MIN_FILE_SIZE_BYTES / 1024) + "KB)");
        }
        sender.sendMessage("§e├─ 需要重新查询: §c" + filesToCheck.size() + " §e个区域");
        sender.sendMessage("§e├─ 查询配置: §7半径=" + Weathering.QUERY_RADIUS + "格, 时间=" + (Weathering.WEATHERING_TIME / 86400) + "天, 线程=" + Weathering.THREAD_MAX);
        sender.sendMessage("§e└─ 预计耗时: §c约 " + (filesToCheck.size() * 2 / 60) + " 分钟 §7(按每区域2秒估算)");
        
        plugin.getLogger().info("├─ 直接使用缓存: " + cachedFiles + " 个区域");
        plugin.getLogger().info("├─ 需要重新查询: " + filesToCheck.size() + " 个区域");
        plugin.getLogger().info("├─ 查询配置: 半径=" + Weathering.QUERY_RADIUS + "格, 时间=" + (Weathering.WEATHERING_TIME / 86400) + "天, 线程=" + Weathering.THREAD_MAX);
        plugin.getLogger().info("└─ 开始查询...");
        
        if (filesToCheck.isEmpty()) {
            sender.sendMessage("§a全部使用缓存，无需查询！");
            plugin.getLogger().info("全部使用缓存，无需查询！");
            // 全部使用缓存，直接更新列表和文件
            updateResultsAndSave(sender, hasEventsQueue, noEventsQueue);
            return;
        }

        sender.sendMessage("§e正在查询中，请耐心等待...");
        long scanStartTime = System.currentTimeMillis();
        
        final int initialProcessed = cachedFiles + skippedBySize;  // 已经处理的数量（缓存+跳过）

        int batchSize = 10;  // 减小批次大小，提高实时持久化频率
        AtomicInteger processedFiles = new AtomicInteger(initialProcessed);
        AtomicBoolean isShuttingDown = new AtomicBoolean(false);

        int poolSize = Math.min(Weathering.THREAD_MAX, filesToCheck.size() / batchSize + 1);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        // 用于实时写入缓存
        Object cacheLock = new Object();

        for (int i = 0; i < filesToCheck.size(); i += batchSize) {
            int start = i;
            int end = Math.min(i + batchSize, filesToCheck.size());

            executor.submit(() -> {
                try {
                    for (int j = start; j < end; j++) {
                        File mcaFile = filesToCheck.get(j);
                        
                        int currentProgress = processedFiles.get() + 1;
                        
                        // 开始查询日志
                        plugin.getLogger().info("[" + currentProgress + "/" + totalFiles + "] 正在查询 " + mcaFile.getName() + " ...");
                        
                        long startTime = System.currentTimeMillis();
                        boolean hasEventsInRegion = eventChecker.getEventsInRegion(mcaFile.getName());
                        long queryTime = System.currentTimeMillis() - startTime;
                        
                        // 完成查询日志（带进度和耗时）
                        plugin.getLogger().info("[" + currentProgress + "/" + totalFiles + "] " + mcaFile.getName() + " 完成，耗时 " + queryTime + "ms，结果: " + (hasEventsInRegion ? "活跃" : "空闲"));

                        if (hasEventsInRegion) {
                            hasEventsQueue.add(mcaFile);
                        } else {
                            noEventsQueue.add(mcaFile);
                        }

                        // 每个文件查询完立即写入缓存，避免崩溃丢失数据
                        synchronized (cacheLock) {
                            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(plugin.getDataFolder(), "region_cache.txt"), true))) {
                                long timestamp = System.currentTimeMillis();
                                writer.write(mcaFile.getName() + "|" + timestamp + "|" + hasEventsInRegion);
                                writer.newLine();
                                writer.flush();
                            } catch (IOException e) {
                                plugin.getLogger().severe("写入缓存文件失败: " + e.getMessage());
                            }
                        }

                        int processed = processedFiles.incrementAndGet();
                        
                        // 每10个文件报告一次进度给玩家，带统计信息
                        if (processed % 10 == 0) {
                            long elapsed = (System.currentTimeMillis() - scanStartTime) / 1000;
                            int scanned = processed - initialProcessed;  // 实际扫描数量
                            double avgTime = scanned > 0 ? (double) elapsed / scanned : 0;
                            int remaining = totalFiles - processed;
                            int estimatedSeconds = (int) (remaining * avgTime);
                            
                            sender.sendMessage("§e[" + processed + "/" + totalFiles + "] §7已扫描:" + scanned + " §7平均:" + String.format("%.1f", avgTime) + "s/个 §7预计剩余:" + (estimatedSeconds / 60) + "分钟");
                        }
                    }

                    // 检查是否全部完成
                    int finalProcessed = processedFiles.get();
                    if (finalProcessed >= totalFiles && isShuttingDown.compareAndSet(false, true)) {
                        long totalElapsed = (System.currentTimeMillis() - scanStartTime) / 1000;
                        int actualScanned = filesToCheck.size();
                        
                        sender.sendMessage("§a========== 扫描完成 ==========");
                        sender.sendMessage("§e总耗时: §a" + (totalElapsed / 60) + " 分 " + (totalElapsed % 60) + " 秒");
                        sender.sendMessage("§e实际查询: §a" + actualScanned + " §e个区域");
                        if (actualScanned > 0) {
                            sender.sendMessage("§e平均速度: §a" + String.format("%.2f", (double) totalElapsed / actualScanned) + " §e秒/区域");
                        }
                        
                        plugin.getLogger().info("========== 扫描完成 ==========");
                        plugin.getLogger().info("总耗时: " + (totalElapsed / 60) + " 分 " + (totalElapsed % 60) + " 秒");
                        plugin.getLogger().info("实际查询: " + actualScanned + " 个区域");
                        if (actualScanned > 0) {
                            plugin.getLogger().info("平均速度: " + String.format("%.2f", (double) totalElapsed / actualScanned) + " 秒/区域");
                        }
                        
                        updateResultsAndSave(sender, hasEventsQueue, noEventsQueue);

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

    private void updateResultsAndSave(CommandSender sender, ConcurrentLinkedQueue<File> hasEventsQueue, ConcurrentLinkedQueue<File> noEventsQueue) {
        List<File> hasEventsList = new ArrayList<>(hasEventsQueue);
        List<File> noEventsList = new ArrayList<>(noEventsQueue);

        // 显示最终统计
        sender.sendMessage("§e活跃区域: §a" + hasEventsList.size() + " §e个");
        sender.sendMessage("§e空闲区域: §7" + noEventsList.size() + " §e个");
        sender.sendMessage("§e正在保存文件...");
        
        plugin.getLogger().info("最终统计 - 活跃区域: " + hasEventsList.size() + " 个，空闲区域: " + noEventsList.size() + " 个");

        // 重新生成完整的缓存文件（去重并更新）
        executor.submit(() -> {
            File cacheFile = new File(plugin.getDataFolder(), "region_cache.txt");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(cacheFile, false))) {
                long currentTime = System.currentTimeMillis();
                for (File file : hasEventsList) {
                    writer.write(file.getName() + "|" + currentTime + "|true");
                    writer.newLine();
                }
                for (File file : noEventsList) {
                    writer.write(file.getName() + "|" + currentTime + "|false");
                    writer.newLine();
                }
                writer.flush();
                plugin.getLogger().info("缓存文件已更新，共 " + (hasEventsList.size() + noEventsList.size()) + " 条记录。");
            } catch (IOException e) {
                plugin.getLogger().severe("更新缓存文件失败: " + e.getMessage());
            }
        });

        writeFileAsync("hasEvents.txt", hasEventsList, () -> {
            writeFileAsync("noEvents.txt", noEventsList, () -> {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        sender.sendMessage("§a所有文件已保存！");
                        sender.sendMessage("§7- hasEvents.txt: " + hasEventsList.size() + " 个");
                        sender.sendMessage("§7- noEvents.txt: " + noEventsList.size() + " 个");
                        sender.sendMessage("§7- region_cache.txt: " + (hasEventsList.size() + noEventsList.size()) + " 个");
                        
                        plugin.getLogger().info("文件保存完成");
                        plugin.getLogger().info("更新 hasEvents 和 noEvents 列表...");
                        // 更新全局变量
                        hasEvents.clear();
                        hasEvents.addAll(hasEventsList);
                        noEvents.clear();
                        noEvents.addAll(noEventsList);
                        plugin.getLogger().info("列表更新完成： hasEvents=" + hasEvents.size() + ", noEvents=" + noEvents.size());
                    }
                }.runTask(plugin);
            });
        });
    }

    // 缓存条目内部类
    private static class CacheEntry {
        final long timestamp;
        final boolean hasEvents;

        CacheEntry(long timestamp, boolean hasEvents) {
            this.timestamp = timestamp;
            this.hasEvents = hasEvents;
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
                dynmapHandler.clearDrawnRegions();
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

    private void drawRegion(File mcaFile, boolean hasEvents) {
        Location center = getCenterLocation(plugin, mcaFile.getName());
        dynmapHandler.drawSquareRegion("world", center.getX(), center.getY(), center.getZ(), 512, hasEvents);
    }
}
