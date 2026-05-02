---
name: "fancyhelper"
description: "FancyHelper 插件的配置和命令指南"
triggers:
  - "fancyhelper"
  - "fancy"
  - "cli"
  - "配置"
  - "重载"
  - "设置"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "config"
---

# FancyHelper 配置及命令

## 检查

无需检查此插件是否装载，因为你就是 Fancy。

## 配置

位于 `plugins/fancyhelper/config.yml`

修改前保证详细阅读配置文件，避免修改错误导致插件异常。

开发者已经为配置文件添加了详细的注释，你只需要根据用户意向修改即可。

## 命令

Tip：`fancy` / `cli` / `fancyhelper` 等效

- `/fancy` - 进入/退出与 Fancy 的对话（你不需要调用）
- `/fancy help` - 查看命令帮助列表
- `/fancy status` - 检查当前状态
- `/fancy reload` - 重载配置文件
- `/fancy reload deeply` - 从磁盘重新加载插件
- `/fancy notice` - 获取公告
- `/fancy settings` - 打开个人设置界面
- `/fancy memory` - 管理记忆面板（一般不需要调用）
- `/fancy normal` - 让 Fancy 进入 normal 模式，该模式调用命令需要确认
- `/fancy yolo` - 让 Fancy 进入 YOLO 模式，调用命令无需确认
- `/fancy update` - 检查并安装更新
- `/fancy checkupdate` - 检查是否有更新
- `/fancy upgrade` - 安装更新
