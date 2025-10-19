package cc.ahaly.weathering.check;

import cc.ahaly.weathering.Weathering;
import cc.ahaly.weathering.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseEventChecker implements EventChecker {

    @Override
    public boolean getEventsInRegion(String mcaRegion) {
        String[] parts = mcaRegion.split("\\.");
        int regionX = Integer.parseInt(parts[1]);
        int regionZ = Integer.parseInt(parts[2]);

        int startX = regionX * 512;
        int startZ = regionZ * 512;
        int endX = startX + 511;
        int endZ = startZ + 511;
        
        long minTime = System.currentTimeMillis() / 1000 - Weathering.WEATHERING_TIME;

        try (Connection connection = DatabaseManager.getConnection()) {
            // 优化版本1：使用EXISTS快速判断（推荐用于大数据量）
            // EXISTS在找到第一条匹配记录时就会立即返回，不会继续扫描
            String sql = 
                "SELECT EXISTS(" +
                "  SELECT 1 FROM co_block " +
                "  WHERE time > ? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? " +
                "  AND action IN (0, 1) " +  // 0: 破坏, 1: 放置
                "  LIMIT " + (Weathering.THRESHOLD + 1) +
                ") AS has_block_events, " +
                "EXISTS(" +
                "  SELECT 1 FROM co_block " +
                "  WHERE time > ? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? " +
                "  AND action = 2 " +  // 2: 交互
                "  LIMIT " + (Weathering.INTERACTION_THRESHOLD + 1) +
                ") AS has_interaction_events";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                // 设置查询超时（5秒，因为使用EXISTS会更快）
                stmt.setQueryTimeout(5);
                
                // 第一个查询（方块事件）
                stmt.setLong(1, minTime);
                stmt.setInt(2, startX);
                stmt.setInt(3, endX);
                stmt.setInt(4, startZ);
                stmt.setInt(5, endZ);
                
                // 第二个查询（交互事件）
                stmt.setLong(6, minTime);
                stmt.setInt(7, startX);
                stmt.setInt(8, endX);
                stmt.setInt(9, startZ);
                stmt.setInt(10, endZ);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        boolean hasBlockEvents = rs.getBoolean("has_block_events");
                        boolean hasInteractionEvents = rs.getBoolean("has_interaction_events");
                        boolean hasActivity = hasBlockEvents || hasInteractionEvents;
                        
                        if (Weathering.DEBUG_MODE) {
                            System.out.println("[Weathering] 区域 " + mcaRegion + 
                                " - 方块事件:" + (hasBlockEvents ? "是" : "否") + 
                                ", 交互事件:" + (hasInteractionEvents ? "是" : "否") +
                                " => " + (hasActivity ? "活跃" : "空闲"));
                        }
                        
                        return hasActivity;
                    }
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("[Weathering] 查询区域 " + mcaRegion + " 时出错: " + e.getMessage());
            if (e.getMessage().contains("timeout") || e.getMessage().contains("timed out")) {
                System.err.println("[Weathering] ⚠️ 查询超时！建议：");
                System.err.println("  1. 减小 QUERY_RADIUS (当前:" + Weathering.QUERY_RADIUS + ") 到 128 或 64");
                System.err.println("  2. 增加 WEATHERING_TIME 到 365 天以上");
                System.err.println("  3. 运行: CREATE INDEX idx_co_block_time_x_z_action ON co_block (time, x, z, action)");
                System.err.println("  4. 考虑清理旧数据");
            }
            e.printStackTrace();
            return false;
        }
    }
}
