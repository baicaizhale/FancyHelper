---
name: "WorldEdit 创世神"
description: "WorldEdit 创世神插件的完整使用指南，包含选区、复制粘贴、笔刷工具等"
triggers:
  - "worldedit"
  - "we"
  - "创世神"
  - "建筑"
  - "edit"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "building"
  - "admin"
---

# WorldEdit (创世神) 插件

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 WorldEdit 插件。

检查方法：尝试执行 `//version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家使用 Minecraft 原版命令（如 `/fill` 和 `/setblock`）来替代。

如果任务不是很复杂，更推荐使用原版命令。

## 常用命令

### 选区工具
- `//wand` - 获取选择工具（默认木斧）
- `//pos1` / `//pos2` - 设置选择区域的两个点

### 基础操作
- `//set <block>` - 将选择区域填充为某种方块
- `//replace <from_block> <to_block>` - 替换区域内的特定方块
- `//copy` - 复制当前选择区域（相对于你的位置）
- `//paste` - 粘贴已复制的区域（相对于你的位置）
- `//undo` - 撤销上一步操作
- `//redo` - 重做被撤销的操作
- `//rotate <degrees>` - 旋转已复制的内容

### 高级功能
- `//schem load/save <name>` - 加载或保存结构文件
- `//br sphere <block> [radius]` - 笔刷工具：生成球体
- `//smooth` - 平滑选择区域的地形
- `//stack <count> [direction]` - 沿某个方向堆叠选区

## 注意事项

WorldEdit 大多数命令以双斜杠 `//` 开头，范围操作前请务必确认选区大小。
