package cc.ahaly.weathering.check;

import cc.ahaly.weathering.Weathering;
import cc.ahaly.weathering.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        try (Connection connection = DatabaseManager.getConnection()) {
            String sql = "SELECT action, COUNT(*) AS count FROM coreprotect.co_block " +
                    "WHERE x BETWEEN ? AND ? AND z BETWEEN ? AND ? " +
                    "AND time > ? " +
                    "GROUP BY action";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, startX);
                stmt.setInt(2, endX);
                stmt.setInt(3, startZ);
                stmt.setInt(4, endZ);
                stmt.setLong(5, System.currentTimeMillis() / 1000 - Weathering.WEATHERING_TIME);

                try (ResultSet rs = stmt.executeQuery()) {
                    int type1And2Count = 0;
                    int type6Count = 0;

                    while (rs.next()) {
                        int action = rs.getInt("action");
                        // // 0: 破坏方块, 1: 放置方块, 2: 交互, 3: 击杀
                        if (action == 0 || action == 1) {
                            type1And2Count ++;
                        } else if (action == 2) {
                            type6Count ++;
                        }
                    }

                    return type1And2Count > 256 || type6Count > 64;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
