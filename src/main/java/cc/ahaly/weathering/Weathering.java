package cc.ahaly.weathering;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import net.coreprotect.CoreProtectAPI;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Weathering extends JavaPlugin {

    public static int WEATHERING_TIME;
    public static String MCA_DIR;
    private DynmapHandler dynmapHandler;
    private boolean isDynmapEnabled = false;
    private final List<File> hasEvents = new ArrayList<>();
    private final List<File> noEvents = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onEnable() {
        CoreProtectHandler coreProtectHandler = new CoreProtectHandler(this);
        CoreProtectAPI coreProtectAPI = coreProtectHandler.getCoreProtect();
        if (coreProtectAPI == null) {
            getLogger().severe("CoreProtect not found, disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("CoreProtect found and enabled.");

        // 获取 Dynmap API
        if (getServer().getPluginManager().getPlugin("dynmap") != null) {
            DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
                @Override
                public void apiEnabled(DynmapCommonAPI api) {
                    isDynmapEnabled = true;
                    dynmapHandler = new DynmapHandler();
                    dynmapHandler.handleApiEnabled(api);
                    getLogger().info("Dynmap found and enabled.");
                }
            });
        } else {
            getLogger().info("Dynmap not found, continuing without Dynmap support.");
        }

        // 保存默认配置文件到插件目录
        saveDefaultConfig();

        // 读取配置文件中的风化时间（单位：天），并转换为秒数
        FileConfiguration config = getConfig();
        int weatheringDays = config.getInt("WEATHERING_TIME", 7); // 默认值为7天
        WEATHERING_TIME = weatheringDays * 86400; // 将天数转换为秒数
        String MCA_DIR = config.getString("MCA_DIR", "/opt/MinecraftServer-AHA/survival/world/region");

        // 异步初始化 MCA 文件列表
        getMCAFilesAsync(MCA_DIR)
                .thenAcceptAsync(mcaFiles -> {
                    // 初始化命令处理程序
                    CommandHandler commandHandler = new CommandHandler(this, coreProtectHandler, dynmapHandler, mcaFiles, hasEvents, noEvents, isDynmapEnabled);

                    // 注册命令处理程序
                    Objects.requireNonNull(getCommand("weathering")).setExecutor(commandHandler);
                    // Tab补全
                    Objects.requireNonNull(getCommand("weathering")).setTabCompleter(new WeatheringTabCompleter());

                    getLogger().info("MCA 文件加载完成，插件初始化完毕。");
                }, Bukkit.getScheduler().getMainThreadExecutor(this));
    }

    @Override
    public void onDisable() {
        executor.shutdown();
    }

    private CompletableFuture<List<File>> getMCAFilesAsync(String directoryPath) {
        return CompletableFuture.supplyAsync(() -> getMCAFiles(directoryPath), executor);
    }

    private List<File> getMCAFiles(String directoryPath) {
        File dir = new File(directoryPath);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".mca"));
        return files != null ? Arrays.asList(files) : Collections.emptyList();
    }
}
