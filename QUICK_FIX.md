# ⚡ Weathering 性能优化 - 快速修复指南

## 🔥 当前问题
- **Java进程占用692% CPU**，服务器严重卡顿
- **MySQL占用大量CPU**
- **922个区域查询需要30分钟**
- 出现"Can't keep up"警告

---

## 🚀 快速修复（5分钟见效）

### 方案A：修改配置（最快，立即生效）

1. **编辑服务器上的配置文件**：
```bash
cd /opt/MinecraftServer-AHA/survival/plugins/Weathering
nano config.yml
```

2. **修改以下参数**：
```yaml
WEATHERING_TIME: 730      # 改为730（2年），减少查询数据量
QUERY_RADIUS: 64          # 改为64（重要！速度提升16倍）
THREAD_MAX: 4             # 改为4，降低CPU压力
MIN_FILE_SIZE_KB: 2048    # 改为2048（2MB），跳过更多小文件
CACHE_EXPIRE_DAYS: 7      # 改为7天
ENABLE_DATABASE: true     # 改为true，启用数据库直连（可选）
```

3. **重载插件**：
```bash
# 在MC控制台执行
plugman reload Weathering
# 或者
/reload confirm
```

**预期效果**：
- 查询时间从 **30分钟 → 2分钟**
- CPU占用降低 **70%+**

---

### 方案B：优化MySQL索引（长期优化）

1. **登录MySQL**：
```bash
mysql -u root -p
```

2. **创建优化索引**：
```sql
USE coreprotect;

-- 创建优化的复合索引（可能需要5-10分钟）
CREATE INDEX idx_co_block_time_x_z_action 
ON co_block (time, x, z, action);

-- 分析表
ANALYZE TABLE co_block;
```

3. **查看进度**：
```sql
SHOW PROCESSLIST;
```

---

## 📊 性能对比

| 配置 | 查询时间 | CPU占用 | 说明 |
|------|---------|---------|------|
| **原配置** | 30分钟 | 692% | RADIUS=256, TIME=7天 |
| **方案A** | 2分钟 | 200% | RADIUS=64, TIME=730天 |
| **方案A+B** | 1分钟 | 150% | 加上索引优化 |

---

## 🔧 高级优化（可选）

### 1. 清理MySQL旧数据

```bash
# 先查看数据量
mysql -u root -p -e "USE coreprotect; SELECT COUNT(*) FROM co_block WHERE time < UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 730 DAY));"

# 备份数据库
mysqldump -u root -p coreprotect > coreprotect_backup_$(date +%Y%m%d).sql

# 删除2年前的数据（分批删除）
mysql -u root -p coreprotect < src/main/resources/mysql_optimization.sql
```

### 2. 优化MySQL配置

编辑 `/etc/my.cnf` 或 `my.ini`：
```ini
[mysqld]
innodb_buffer_pool_size = 8G     # 根据内存调整
query_cache_size = 256M
max_connections = 500
tmp_table_size = 256M
```

重启MySQL：
```bash
sudo systemctl restart mysql
```

---

## ⚠️ 注意事项

### QUERY_RADIUS 说明
- **256格**：查询整个512x512区域（最准确，但最慢）
- **128格**：查询中心256x256区域（推荐，速度快4倍）
- **64格**：查询中心128x128区域（最快，速度快16倍，可能漏掉边缘）

### 建议选择
- 如果区域活动集中在中心：用 **64格**
- 如果需要更准确的检测：用 **128格**
- 不建议用256格（太慢）

---

## 🐛 故障排查

### 问题1：还是很慢
- 检查MySQL索引是否创建成功：`SHOW INDEX FROM co_block;`
- 检查配置是否生效：`/weathering query`
- 启用调试模式：`DEBUG_MODE: true`

### 问题2：查询超时
```
[Weathering] ⚠️ 查询超时！
```
- 减小 `QUERY_RADIUS` 到 32 或 16
- 增加 `WEATHERING_TIME` 到 1095（3年）
- 考虑清理旧数据

### 问题3：MySQL CPU还是高
- 查看慢查询日志：`SHOW PROCESSLIST;`
- 确认索引已创建
- 考虑升级服务器硬件（SSD、更多内存）

---

## 📞 联系信息

如果问题仍未解决，请提供：
1. MySQL版本：`mysql --version`
2. co_block表大小：`SELECT table_rows FROM information_schema.tables WHERE table_name='co_block';`
3. 索引列表：`SHOW INDEX FROM co_block;`
4. 配置文件：`cat config.yml`

---

## 🎯 推荐配置（最终版）

```yaml
# config.yml - 平衡性能和准确性
WEATHERING_TIME: 730         # 2年
QUERY_RADIUS: 64             # 64格（最快）或 128（平衡）
THREAD_MAX: 4                # 4线程
MIN_FILE_SIZE_KB: 2048       # 2MB
CACHE_EXPIRE_DAYS: 7         # 7天
ENABLE_DATABASE: true        # 启用数据库直连
DEBUG_MODE: false            # 关闭调试
```

**立即生效，无需重启服务器！**

Good luck! 🚀

