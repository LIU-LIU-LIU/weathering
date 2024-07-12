package cc.ahaly.weathering;

import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import net.coreprotect.CoreProtectAPI;

import java.io.File;
import java.util.*;

public final class Weathering extends JavaPlugin {

    private DynmapHandler dynmapHandler;
    private boolean isDynmapEnabled = false;
    private final List<File> hasEvents = new ArrayList<>();
    private final List<File> noEvents = new ArrayList<>();

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

        // 初始化 MCA 文件列表
        List<File> mcaFiles = getMCAFiles("/opt/MinecraftServer-AHA/survival/world/region");

        // 初始化命令处理程序
        CommandHandler commandHandler = new CommandHandler(this, coreProtectHandler, dynmapHandler, mcaFiles, hasEvents, noEvents, isDynmapEnabled);

        // 注册命令处理程序
        Objects.requireNonNull(getCommand("weathering")).setExecutor(commandHandler);
    }

    @Override
    public void onDisable() {
        // 插件关闭逻辑
    }

    public List<File> getMCAFiles(String directoryPath) {
        File dir = new File(directoryPath);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".mca"));
        return files != null ? Arrays.asList(files) : Collections.emptyList();
    }
}
