#!/bin/bash

# 定义文件路径
region_dir="/opt/MinecraftServer-AHA/survival/world/region"
file_list="noEvents.txt"

# 检查文件是否存在
if [ ! -f "$file_list" ]; then
    echo "文件 $file_list 不存在。"
    exit 1
fi

# 读取文件名并删除相应的文件
while IFS= read -r file_name; do
    file_path="$region_dir/$file_name"
    if [ -f "$file_path" ]; then
        /bin/rm "$file_path"
        echo "已删除文件: $file_path"
    else
        echo "文件 $file_path 不存在，跳过。"
    fi
done < "$file_list"

echo "所有操作已完成。"