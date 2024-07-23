package cc.ahaly.weathering.util;

import com.alibaba.druid.pool.DruidDataSource;

import java.sql.*;

public class DatabaseManager {
    private static DruidDataSource dataSource;

    // 设置数据源
    public static void setupDataSource(String host, int port, String database, String username, String password) {
        if (dataSource == null) {
            dataSource = new DruidDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
            dataSource.setUsername(username);
            dataSource.setPassword(password);

            // 可根据需要设置其他连接池参数
            dataSource.setInitialSize(5);
            dataSource.setMinIdle(5);
            dataSource.setMaxActive(20);
            dataSource.setMaxWait(60000);
            dataSource.setPoolPreparedStatements(true);
            dataSource.setMaxPoolPreparedStatementPerConnectionSize(20);

            // 初始化连接池后检查并创建索引
            checkAndCreateIndexes();
        }
    }

    // 获取连接
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // 关闭数据源
    public static void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    // 检查并创建索引
    private static void checkAndCreateIndexes() {
        try (Connection connection = getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            // 检查是否存在索引 idx_co_block_x_z_time
            boolean indexExists = false;
            try (ResultSet rs = metaData.getIndexInfo(null, null, "co_block", false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if ("idx_co_block_x_z_time".equals(indexName)) {
                        indexExists = true;
                        break;
                    }
                }
            }

            // 如果索引不存在，则创建索引
            if (!indexExists) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("CREATE INDEX idx_co_block_x_z_time ON co_block (x, z, time)");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
