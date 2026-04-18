---
name: "Vault 经济API"
description: "Vault 经济、权限和聊天 API 插件的说明，作为经济/权限插件的桥梁"
triggers:
  - "vault"
  - "经济"
  - "api"
  - "前置"
  - "economy"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "economy"
  - "api"
  - "dependency"
---

# Vault 插件

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 Vault 插件。

检查方法：尝试执行 `/vault version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家无法使用经济和权限 API 功能。

## 说明

Vault 是一个权限、经济和聊天 API 插件。它本身不提供具体功能，而是作为其他经济/权限插件的桥梁。

## 常用命令（通常由具体经济/权限插件提供）

- `/money` - 查看余额
- `/money pay <player> <amount>` - 转账
- `/lp` (LuckPerms) - 常用权限管理

## 依赖关系

Vault 需要配合具体的经济插件（如 EssentialsX）和权限插件（如 LuckPerms）使用才能发挥作用。
