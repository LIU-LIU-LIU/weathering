-- ================================================
-- Weathering 插件 MySQL 优化脚本
-- ================================================
-- 用途：优化 CoreProtect 数据库查询性能
-- 执行方式：mysql -u root -p coreprotect < mysql_optimization.sql
-- ================================================

USE coreprotect;

-- 1. 查看当前表大小和行数
SELECT 
    table_name AS "表名",
    ROUND(((data_length + index_length) / 1024 / 1024 / 1024), 2) AS "大小(GB)",
    ROUND((data_length / 1024 / 1024 / 1024), 2) AS "数据大小(GB)",
    ROUND((index_length / 1024 / 1024 / 1024), 2) AS "索引大小(GB)",
    table_rows AS "行数(估计)"
FROM information_schema.TABLES
WHERE table_schema = 'coreprotect' AND table_name = 'co_block';

-- 2. 查看现有索引
SHOW INDEX FROM co_block;

-- 3. 【重要】创建优化的复合索引
-- 注意：如果表很大（超过5GB），这个操作可能需要5-15分钟
-- 建议：在服务器低峰期执行
CREATE INDEX IF NOT EXISTS idx_co_block_time_x_z_action 
ON co_block (time, x, z, action);

-- 4. （可选）删除旧的低效索引
-- 注意：先确认新索引创建成功再删除旧索引
-- DROP INDEX idx_co_block_x_z_time ON co_block;

-- 5. 分析表统计信息（帮助优化器选择最佳索引）
ANALYZE TABLE co_block;

-- 6. 优化表（清理碎片，压缩空间）
-- 注意：这个操作会锁表，建议在服务器低峰期执行
-- OPTIMIZE TABLE co_block;

-- 7. 查看索引使用情况（运行一段时间后查看）
-- SELECT 
--     TABLE_NAME AS "表名",
--     INDEX_NAME AS "索引名",
--     ROWS_READ AS "读取行数",
--     ROWS_SENT AS "返回行数"
-- FROM performance_schema.table_io_waits_summary_by_index_usage
-- WHERE OBJECT_SCHEMA = 'coreprotect' AND OBJECT_NAME = 'co_block';

-- ================================================
-- 高级优化（可选，需要重启MySQL）
-- ================================================

-- 显示当前配置
SHOW VARIABLES LIKE 'innodb_buffer_pool_size';
SHOW VARIABLES LIKE 'query_cache_size';
SHOW VARIABLES LIKE 'max_connections';

-- 建议配置（编辑 /etc/my.cnf 或 my.ini）：
-- [mysqld]
-- innodb_buffer_pool_size = 8G        # 根据服务器内存调整（建议50-70%内存）
-- query_cache_size = 256M             # 查询缓存
-- query_cache_type = 1                # 启用查询缓存
-- max_connections = 500               # 最大连接数
-- tmp_table_size = 256M               # 临时表大小
-- max_heap_table_size = 256M          # 内存表大小
-- innodb_flush_log_at_trx_commit = 2  # 提升写入性能（牺牲一点安全性）
-- innodb_log_file_size = 256M         # 日志文件大小

-- ================================================
-- 数据清理（谨慎操作！）
-- ================================================

-- 查看数据分布（按时间）
SELECT 
    FROM_UNIXTIME(time) AS "日期",
    COUNT(*) AS "记录数"
FROM co_block
GROUP BY DATE(FROM_UNIXTIME(time))
ORDER BY time DESC
LIMIT 30;

-- 查看2年前的数据量
SELECT COUNT(*) AS "2年前的记录数"
FROM co_block
WHERE time < UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 730 DAY));

-- 【谨慎】删除2年前的数据（取消注释前请备份！）
-- IMPORTANT: 执行前请先备份数据库！
-- mysqldump -u root -p coreprotect > coreprotect_backup_$(date +%Y%m%d).sql
-- 
-- DELETE FROM co_block 
-- WHERE time < UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 730 DAY))
-- LIMIT 100000;  -- 分批删除，避免锁表太久
--
-- 删除后优化表：
-- OPTIMIZE TABLE co_block;

-- ================================================
-- 测试查询性能
-- ================================================

-- 测试查询（替换为您实际的区域坐标）
EXPLAIN SELECT EXISTS(
    SELECT 1 FROM co_block 
    WHERE time > UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 730 DAY))
    AND x BETWEEN -2560 AND -2049 
    AND z BETWEEN 1536 AND 2047 
    AND action IN (0, 1)
    LIMIT 129
) AS has_block_events;

-- 查看慢查询日志（如果启用了）
-- SHOW VARIABLES LIKE 'slow_query%';
-- SET GLOBAL slow_query_log = 'ON';
-- SET GLOBAL long_query_time = 2;  -- 记录超过2秒的查询

SELECT '=== 优化脚本执行完成 ===' AS '状态';
SELECT '请检查索引是否创建成功，然后重启 Weathering 插件' AS '提示';

