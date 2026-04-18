---
name: "Residence 领地保护"
description: "Residence 领地插件的完整使用指南，包含创建领地、权限设置、子区域等"
triggers:
  - "residence"
  - "res"
  - "领地"
  - "保护"
  - "claim"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "protection"
  - "land"
---

# Residence (领地) 插件

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 Residence 插件。

检查方法：尝试执行 `/res version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家无法使用领地保护功能。

## 常用命令

### 领地创建
- `/res select [x y z]` - 选择区域（也可以用木锄/木斧左击和右击）
- `/res create <name>` - 创建领地
- `/res remove <name>` - 删除领地
- `/res subzone <name> <subzone_name>` - 在领地内创建子区域

### 领地信息
- `/res info [name]` - 查看领地详细信息
- `/res list [player]` - 列出玩家拥有的领地
- `/res limits` - 查看你的领地创建限制

### 传送功能
- `/res tp <name>` - 传送到领地
- `/res tpset` - 设置领地传送点

### 权限设置
- `/res pset <player> [flag] [t/f/remove]` - 给特定玩家设置权限（如 build, move, use）
- `/res set [flag] [t/f/remove]` - 设置领地全局权限（如 pvp, tnt, monsters）

### 消息设置
- `/res message <enter/leave> <message>` - 设置进入/离开领地的提示语

## 常用 Flag

- `build` - 建筑权限
- `use` - 使用权限
- `move` - 移动权限
- `container` - 容器权限
- `pvp` - 玩家对战
- `ignite` - 点火权限
