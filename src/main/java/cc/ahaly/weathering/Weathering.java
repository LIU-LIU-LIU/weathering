package cc.ahaly.weathering;

import cc.ahaly.weathering.check.CoreProtectHandler;
import cc.ahaly.weathering.check.DatabaseEventChecker;
import cc.ahaly.weathering.check.EventChecker;
import cc.ahaly.weathering.util.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import net.coreprotect.CoreProtectAPI;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Weathering extends JavaPlugin {

    public static int WEATHERING_TIME;
    private String MCA_DIR;
    public static int THRESHOLD;
    public static int INTERACTION_THRESHOLD;
    public static int THREAD_MAX;
    private DynmapHandler dynmapHandler;
    private boolean isDynmapEnabled = false;
    private EventChecker eventChecker;
    public static List<File> hasEvents = new ArrayList<>();
    public static List<File> noEvents = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onEnable() {
        // 保存默认配置文件到插件目录
        saveDefaultConfig();

        // 读取配置文件中的风化时间（单位：天），并转换为秒数
        FileConfiguration config = getConfig();
        // 读取和设置基础配置
        WEATHERING_TIME = config.getInt("WEATHERING_TIME", 7) * 86400; // 转换为秒
        MCA_DIR = config.getString("MCA_DIR", getDataFolder() + "/world/region");
        THRESHOLD = config.getInt("THRESHOLD", 512);
        INTERACTION_THRESHOLD = config.getInt("INTERACTION_THRESHOLD", 128);
        THREAD_MAX = config.getInt("THREAD_MAX", 8);



        boolean ENABLE_DATABASE = config.getBoolean("ENABLE_DATABASE", false);

        // 根据数据来源初始化不同的处理器
        if (!ENABLE_DATABASE) {
            CoreProtectHandler coreProtectHandler = new CoreProtectHandler(this, null);
            CoreProtectAPI coreProtectAPI = coreProtectHandler.getCoreProtect();
            if (coreProtectAPI != null) {
                getLogger().info("CoreProtect API found and enabled.");
                eventChecker = new CoreProtectHandler(this, coreProtectAPI);
            } else {
                getLogger().severe("CoreProtect API not found, disabling plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } else {
            String host = config.getString("DATABASE.host", "localhost");
            int port = config.getInt("DATABASE.port", 3306);
            String database = config.getString("DATABASE.database", "coreprotect");
            String username = config.getString("DATABASE.username", "root");
            String password = config.getString("DATABASE.password", "root");

            try {
                DatabaseManager.setupDataSource(host, port, database, username, password);
                getLogger().info("Database connected successfully.");
                eventChecker = new DatabaseEventChecker();
            } catch (Exception e) {
                getLogger().severe("Failed to initialize database: " + e.getMessage() + " disabling plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

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

        // 读取 hasEvents 和 noEvents 文件
        hasEvents = readFile(new File(getDataFolder(), "hasEvents.txt"));
        noEvents = readFile(new File(getDataFolder(), "noEvents.txt"));

        // 异步初始化 MCA 文件列表
        getMCAFilesAsync(MCA_DIR)
                .thenAcceptAsync(mcaFiles -> {
                    // 初始化命令处理程序
                    CommandHandler commandHandler = new CommandHandler(this, dynmapHandler, eventChecker, mcaFiles, hasEvents, noEvents);

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
        if (Objects.equals(getConfig().getString("DATA_SOURCE"), "CoreProtect-DataBase")) {
            DatabaseManager.closeDataSource();
            getLogger().info("Database connection closed.");
        }
    }

    private List<File> readFile(File file) {
        List<File> fileList = new ArrayList<>();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    File f = new File(MCA_DIR, line.trim()); // 添加 MCA_DIR 作为前缀
                    if (f.exists()) {
                        fileList.add(f);
                    } else {
                        getLogger().warning("文件 " + f.getAbsolutePath() + " 不存在。");
                    }
                }
            } catch (IOException e) {
                getLogger().severe("读取文件 " + file.getName() + " 时出错: " + e.getMessage());
            }
        } else {
            getLogger().info("文件 " + file.getName() + " 不存在，将保持列表为空。");
        }
        getLogger().info("从 " + file.getName() + " 读取了 " + fileList.size() + " 个文件。");
        return fileList;
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
