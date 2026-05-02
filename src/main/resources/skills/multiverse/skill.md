---
name: "multiverse"
description: "Multiverse 多世界管理插件的完整使用指南"
triggers:
  - "multiverse"
  - "mv"
  - "世界"
  - "多世界"
  - "传送世界"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "world"
  - "admin"
---

# Multiverse 使用方法

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 Multiverse 插件。

检查方法：尝试执行 `/mv version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家无法使用多世界管理功能。

## 常用命令

- `/mv list` - 列出所有已加载的世界
- `/mv create <name> <environment>` - 创建新世界
- `/mv import <name> <environment>` - 导入已有世界
- `/mv delete <name>` - 删除世界
- `/mv unload <name>` - 卸载世界
- `/mv load <name>` - 加载世界
- `/mv regen <name>` - 重新生成世界
- `/mv tp <name>` - 传送到指定世界
- `/mv setspawn` - 设置当前世界的出生点
- `/mv modify set <property> <value>` - 修改世界属性
- `/mv confirm` - 确认危险操作（删除世界等）

## 环境类型

- `normal` - 主世界
- `nether` - 下界
- `the_end` - 末地

## 注意

Multiverse 支持多世界管理，包括创建、删除、传送、配置等。
