---
name: "Multiverse-Core 多世界"
description: "Multiverse-Core 多世界管理插件的完整使用指南，包含世界创建、传送、属性设置等"
triggers:
  - "multiverse"
  - "mv"
  - "多世界"
  - "world"
  - "世界"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "world"
  - "admin"
---

# Multiverse-Core (多世界) 插件

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 Multiverse-Core 插件。

检查方法：尝试执行 `/mv version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家无法使用多世界管理功能。

## 常用命令

### 世界列表与传送
- `/mv list` - 列出当前服务器的所有世界
- `/mv tp [player] <world_name>` - 传送到某个世界

### 世界创建与导入
- `/mv create <world_name> <env> [-t type] [-g generator]` - 创建新世界
  - `env`: normal, nether, end
- `/mv import <world_name> <env>` - 导入已存在的地图文件夹

### 世界管理
- `/mv setspawn` - 设置当前世界的出生点
- `/mv modify set <property> <value> [world]` - 修改世界属性（如 pvp, animals, monsters）
- `/mv gamerule <rule> <value> [world]` - 设置世界游戏规则
- `/mv remove <world_name>` - 从配置中移除世界（不删除文件）
- `/mv delete <world_name>` - 彻底删除世界及地图文件（需确认）
- `/mv reload` - 重新加载插件配置

## 环境类型

- `normal` - 主世界
- `nether` - 下界
- `end` - 末地

## 世界类型

- `FLAT` - 超平坦
- `LARGE_BIOMES` - 巨型生物群系
