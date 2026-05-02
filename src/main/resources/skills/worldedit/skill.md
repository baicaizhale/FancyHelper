---
name: "worldedit"
description: "WorldEdit 世界编辑插件的使用指南"
triggers:
  - "worldedit"
  - "we"
  - "创世神"
  - "选区"
  - "快速建筑"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "building"
  - "admin"
---

# WorldEdit (通常不用)

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 WorldEdit 插件。

检查方法：尝试执行 `//version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家无法使用快速建筑功能。

## 常用命令

- `//wand` - 获取选区工具（木斧）
- `//pos1` - 设置选区第一个点
- `//pos2` - 设置选区第二个点
- `//set <方块>` - 将选区设置为指定方块
- `//replace [旧方块] <新方块>` - 替换选区内的方块
- `//copy` - 复制选区
- `//paste` - 粘贴复制的内容
- `//cut` - 剪切选区
- `//undo` - 撤销上一步操作
- `//redo` - 重做上一步操作
- `//rotate <角度>` - 旋转复制的内容
- `//flip [方向]` - 翻转复制的内容
- `//walls <方块>` - 生成选区的墙壁
- `//outline <方块>` - 生成选区的轮廓
- `//smooth [迭代次数]` - 平滑地形
- `//regen` - 重新生成选区地形
- `//count <方块>` - 统计选区内方块数量
- `//size` - 查看选区大小

## 选择工具

默认使用木斧（Wooden Axe）作为选择工具：
- 左键点击 = 选择第一个点
- 右键点击 = 选择第二个点

## 注意

WorldEdit 是强大的世界编辑工具，通常用于大型建筑、地形修改等。由于功能强大，请谨慎使用，避免误操作。
