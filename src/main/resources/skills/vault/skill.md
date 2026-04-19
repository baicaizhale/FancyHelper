---
name: "Vault 经济插件"
description: "Vault 经济系统插件的使用指南"
triggers:
  - "vault"
  - "经济"
  - "money"
  - "balance"
  - "支付"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "economy"
  - "api"
---

# Vault 经济插件

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 Vault 插件。

检查方法：尝试执行 `/vault version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家无法使用经济系统功能。

## 说明

Vault 本身是一个经济API插件，不直接提供经济命令。它需要配合其他经济插件使用，如 EssentialsX Economy、iConomy、BOSEconomy 等。

## 常用经济命令（通过其他插件提供）

- `/balance` 或 `/money` - 查看余额
- `/pay <玩家> <金额>` - 转账给其他玩家
- `/baltop` - 查看财富排行榜
- `/eco give <玩家> <金额>` - 给予金钱（管理员）
- `/eco take <玩家> <金额>` - 扣除金钱（管理员）
- `/eco set <玩家> <金额>` - 设置金钱（管理员）

## 注意

Vault 是许多插件的经济依赖，提供统一的API接口。
