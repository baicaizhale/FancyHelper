---
name: "residence"
description: "Residence 领地管理插件的完整使用指南"
triggers:
  - "residence"
  - "res"
  - "领地"
  - "圈地"
  - "保护领地"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "protection"
  - "land"
---

# Residence 领地插件使用

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 Residence 插件。

检查方法：尝试执行 `/res version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家无法使用领地管理功能。

## 常用命令

### 领地管理
- `/res create <领地名>` - 创建新领地（需要先使用选择工具选择区域）
- `/res delete <领地名>` - 删除领地
- `/res select` - 查看当前选择区域
- `/res area list` - 列出当前领地的所有区域
- `/res list [玩家]` - 列出领地

### 权限管理
- `/res set <权限> [true|false]` - 设置领地权限
- `/res pset <玩家> <权限> [true|false]` - 为特定玩家设置权限
- `/res info` - 查看领地信息
- `/res message set <消息>` - 设置进入领地时的欢迎消息

### 子区域
- `/res subzone create <名称>` - 创建子区域
- `/res subzone delete <名称>` - 删除子区域
- `/res subzone list` - 列出子区域

### 交易和管理
- `/res market` - 查看领地市场信息
- `/res rent` - 租赁相关命令
- `/res tp <领地名>` - 传送到领地
- `/res near` - 查看附近的领地

## 选择工具

默认使用木锄（Wooden Hoe）作为选择工具：
- 左键点击 = 选择第一个点
- 右键点击 = 选择第二个点
- `/res select size` - 查看选择区域大小

## 常用权限

- `build` - 建筑权限
- `destroy` - 破坏权限
- `use` - 使用权限（门、按钮等）
- `container` - 容器权限（箱子、熔炉等）
- `pvp` - PVP权限
- `tnt` - TNT权限
- `fire` - 火焰权限
- `move` - 移动权限
- `teleport` - 传送权限

## 注意

Residence 是最流行的领地保护插件之一，支持领地租赁、子区域、精细权限控制等功能。
