# 风化插件（WeatheringPlugin）
当某个地方年久失修的时候，就会被自然风化。

## 概述
* 数据查询：通过查询[coreprotect](https://dev.bukkit.org/projects/coreprotect)并分析最近被修改过的区块方块数据。
* 渲染：可选通过[dynmap](https://www.spigotmc.org/resources/dynmap%C2%AE.274/)插件渲染出玩家活动的区域。
* 提醒：当玩家进入长期未被修改过的区域时，提醒玩家该区域可能会被自然风化。
* 重置：重置长期未被修改过的区块，使其回到自然生成的状态。

## 安装
1. 下载 `Weathering.jar` 插件文件。
2. 将 `Weathering.jar` 文件放入你的 Minecraft 服务器的 `plugins` 目录中。
3. 确保你已经安装了以下依赖插件：
    - CoreProtect
    - Dynmap (可选)
4. 启动或重启服务器。

## 命令
### 查询命令
- `/weathering query`
    - 查询玩家所在区域内是否有玩家事件。
- `/weathering query mca <x> <z>`
    - 查询指定 MCA 区域内是否有玩家事件。

### 列表命令
- `/weathering list`
    - 列出所有区域的玩家事件情况，并将结果写入 `hasEvents.txt` 和 `noEvents.txt` 文件。

### 绘制命令
- `/weathering draw events`
    - 在 Dynmap 上绘制有玩家事件的区域。
- `/weathering draw weathers`
    - 在 Dynmap 上绘制无玩家事件的区域。
- `/weathering draw clear`
    - 清除 Dynmap 上所有绘制的区域。

### 重置/提醒命令
- `/weathering reset`
    - 重置所有无玩家事件的区域。
- `/weathering remind`
    - 定时提醒所有无玩家事件的区域。