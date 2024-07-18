package cc.ahaly.weathering;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DatabaseEventChecker implements EventChecker {
    @Override
    public CompletableFuture<List<String[]>> getEventsInRegion(String mcaRegion, Location center, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList) {
        return CompletableFuture.supplyAsync(() -> {
            String[] parts = mcaRegion.split("\\.");
            int regionX = Integer.parseInt(parts[1]);
            int regionZ = Integer.parseInt(parts[2]);

            int startX = regionX * 512;
            int startZ = regionZ * 512;
            int endX = startX + 511;
            int endZ = startZ + 511;

            try (Connection connection = DatabaseManager.getConnection()) {
                String sql = buildSqlQuery(startX, endX, startZ, endZ, restrictUsers, excludeUsers, restrictBlocks, excludeBlocks, actionList);
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        List<String[]> results = processResultSet(rs);
                        System.out.println("DatabaseEventChecker results size: " + results.size());
                        return results;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        });
    }

    private String buildSqlQuery(int startX, int endX, int startZ, int endZ, List<String> restrictUsers, List<String> excludeUsers, List<Object> restrictBlocks, List<Object> excludeBlocks, List<Integer> actionList) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT action, COUNT(*) AS count FROM co_block WHERE ");
        sqlBuilder.append("x BETWEEN ").append(startX).append(" AND ").append(endX);
        sqlBuilder.append(" AND z BETWEEN ").append(startZ).append(" AND ").append(endZ);
        sqlBuilder.append(" AND time > ").append(System.currentTimeMillis() / 1000 - Weathering.WEATHERING_TIME);

        if (!restrictUsers.isEmpty()) {
            sqlBuilder.append(" AND user IN (").append(String.join(", ", restrictUsers)).append(")");
        }
        if (!excludeUsers.isEmpty()) {
            sqlBuilder.append(" AND user NOT IN (").append(String.join(", ", excludeUsers)).append(")");
        }
        if (!actionList.isEmpty()) {
            sqlBuilder.append(" AND action IN (").append(String.join(", ", actionList.stream().map(String::valueOf).toArray(String[]::new))).append(")");
        }
        sqlBuilder.append(" GROUP BY action");
        return sqlBuilder.toString();
    }

    private List<String[]> processResultSet(ResultSet rs) throws SQLException {
        List<String[]> results = new ArrayList<>();
        while (rs.next()) {
            String action = rs.getString("action");
            String count = rs.getString("count");
            results.add(new String[]{action, count});
        }
        return results;
    }
}
