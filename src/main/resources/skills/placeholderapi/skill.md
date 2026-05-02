---
name: "placeholderapi"
description: "PlaceholderAPI 变量系统的完整使用指南"
triggers:
  - "placeholderapi"
  - "papi"
  - "变量"
  - "占位符"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "placeholder"
  - "api"
---

# PAPI (PlaceholderAPI)

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 PlaceholderAPI 插件。

检查方法：尝试执行 `/papi version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家无法使用变量替换功能。

## 常用命令

- `/papi version` - 查看插件版本
- `/papi list` - 列出所有已注册的变量扩展
- `/papi ecloud list [all]` - 列出云端可用的扩展
- `/papi ecloud download <扩展名>` - 下载扩展
- `/papi ecloud update <扩展名>` - 更新扩展
- `/papi ecloud status <扩展名>` - 查看扩展状态
- `/papi reload` - 重新加载插件配置
- `/papi parse <player> <placeholder>` - 测试解析变量
- `/papi info <扩展名>` - 查看扩展详细信息

## 常用变量

- `%player_name%` - 玩家名称
- `%player_displayname%` - 玩家显示名称
- `%player_health%` - 玩家生命值
- `%player_level%` - 玩家等级
- `%player_exp%` - 玩家经验值
- `%player_world%` - 玩家所在世界
- `%player_x%` / `%player_y%` / `%player_z%` - 玩家坐标
- `%server_online%` - 在线玩家数
- `%server_tps%` - 服务器TPS
- `%server_uptime%` - 服务器运行时间

## 注意

PlaceholderAPI 是许多插件的依赖，用于在聊天、记分板、TAB列表等地方显示动态变量。
