# 风化插件（WeatheringPlugin）
当某个地方年久失修的时候，就会被自然风化。

## 概述
* 数据查询：通过查询[coreprotect](https://dev.bukkit.org/projects/coreprotect)并分析最近被修改过的区块方块数据。
* 渲染：可选通过[dynmap](https://www.spigotmc.org/resources/dynmap%C2%AE.274/)插件渲染出玩家活动的区域。
* 提醒：当玩家进入长期未被修改过的区域时，提醒玩家该区域可能会被自然风化。
* 重置：自动重置长期未被修改过的区块，使其回到自然生成的状态。

## 命令
主命令 /Weathering：根据第一个参数分发到不同的子命令处理函数。

* /Weathering query [player|mca <x> <z>]：处理查询命令。
没有参数时，查询玩家所在区域。告诉他这里是否会被风化，如果不会还有多久会，如果会提醒玩家 此区域会被风化，如果不想风化需要玩家在此活动。

有 mca <x> <z> 参数时，查询指定区域是否会被风化。

* /Weathering list：列出所有MCA文件并查询是否有事件,将结果写到文本中。

* /Weathering draw [events|weathers|clear]：绘制或清除区域。
events：绘制有事件的区域。
weathers：绘制无事件的区域。
clear：清除绘制的区域。