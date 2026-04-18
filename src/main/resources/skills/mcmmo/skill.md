---
name: "mcMMO 技能系统"
description: "mcMMO 技能系统插件的完整使用指南，包含技能查看、组队、管理员命令等"
triggers:
  - "mcmmo"
  - "技能"
  - "skill"
  - "等级"
  - "rpg"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "rpg"
  - "skill"
---

# mcMMO 插件

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 mcMMO 插件。

检查方法：尝试执行 `/mcmmo version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家无法使用技能系统和等级功能。

## 常用命令

### 基础命令
- `/mcmmo help` - 查看 mcMMO 帮助菜单
- `/mcstats` - 查看自己的技能统计和等级
- `/mctop [技能名]` - 查看技能排行榜
- `/inspect <玩家>` - 查看其他玩家的 mcMMO 统计
- `/mcability` - 切换右键触发技能的开启/关闭

### 组队系统
- `/party` - 组队系统相关命令
- `/party create <名称>` - 创建队伍
- `/party join <队伍>` - 加入队伍
- `/party leave` - 离开队伍
- `/party chat` - 队伍聊天

## 管理员命令

- `/mmoedit <玩家> <技能> <等级>` - 修改玩家技能等级
- `/addxp <玩家> <技能> <数值>` - 给玩家增加技能经验
- `/skillreset <玩家> <技能>` - 重置玩家技能等级

## 主要技能

- **挖掘** (Excavation)
- **采矿** (Mining)
- **伐木** (Woodcutting)
- **修复** (Repair)
- **杂技** (Acrobatics)
- **剑术** (Swords)
- **弓箭** (Archery)
- **驯兽** (Taming)
