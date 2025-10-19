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
            dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&cachePrepStmts=true&useServerPrepStmts=true");
            dataSource.setUsername(username);
            dataSource.setPassword(password);

            // 优化的连接池参数
            dataSource.setInitialSize(10);  // 增加初始连接数
            dataSource.setMinIdle(10);      // 增加最小空闲连接
            dataSource.setMaxActive(50);    // 增加最大活动连接
            dataSource.setMaxWait(30000);   // 减少等待时间到30秒
            dataSource.setPoolPreparedStatements(true);
            dataSource.setMaxPoolPreparedStatementPerConnectionSize(50);
            
            // 性能优化参数
            dataSource.setTestWhileIdle(true);
            dataSource.setTimeBetweenEvictionRunsMillis(60000);
            dataSource.setMinEvictableIdleTimeMillis(300000);
            dataSource.setValidationQuery("SELECT 1");
            dataSource.setTestOnBorrow(false);
            dataSource.setTestOnReturn(false);

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

            // 检查是否存在旧索引 idx_co_block_x_z_time
            boolean oldIndexExists = false;
            boolean newIndexExists = false;
            try (ResultSet rs = metaData.getIndexInfo(null, null, "co_block", false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if ("idx_co_block_x_z_time".equals(indexName)) {
                        oldIndexExists = true;
                    }
                    if ("idx_co_block_time_x_z_action".equals(indexName)) {
                        newIndexExists = true;
                    }
                }
            }

            try (Statement stmt = connection.createStatement()) {
                // 删除旧索引（如果存在）
                if (oldIndexExists && !newIndexExists) {
                    System.out.println("[Weathering] 正在删除旧索引 idx_co_block_x_z_time...");
                    stmt.executeUpdate("DROP INDEX idx_co_block_x_z_time ON co_block");
                }

                // 创建优化的复合索引（time放在最前面，因为它过滤性最强）
                if (!newIndexExists) {
                    System.out.println("[Weathering] 正在创建优化索引 idx_co_block_time_x_z_action，这可能需要几分钟...");
                    stmt.executeUpdate("CREATE INDEX idx_co_block_time_x_z_action ON co_block (time, x, z, action)");
                    System.out.println("[Weathering] 索引创建完成！查询性能将大幅提升。");
                } else {
                    System.out.println("[Weathering] 优化索引已存在，无需创建。");
                }
            }
        } catch (SQLException e) {
            System.err.println("[Weathering] 索引操作失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
