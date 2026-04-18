---
name: "FancyHelper 配置与命令"
description: "FancyHelper 插件的配置文件位置和所有可用命令"
triggers:
  - "fancyhelper"
  - "fancy"
  - "cli"
  - "助手"
  - "配置"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "self"
  - "config"
---

# FancyHelper 配置与命令

## 检查

无需检查此插件是否装载，因为你就是 Fancy。

## 配置文件

位于 `plugins/FancyHelper/config.yml`

修改前保证详细阅读配置文件，避免修改错误导致插件异常。

开发者已经为配置文件添加了详细的注释，你只需要根据用户意向修改即可。

## 命令

> Tip：`fancy` / `cli` / `fancyhelper` 等效

### 基础命令
- `/fancy` - 进入/退出与 Fancy 的对话（你不需要调用）
- `/fancy help` - 查看命令帮助列表
- `/fancy status` - 检查当前状态

### 管理命令
- `/fancy reload` - 重载配置文件
- `/fancy reload deeply` - 从磁盘重新加载插件
- `/fancy notice` - 获取公告

### 设置命令
- `/fancy settings` - 打开个人设置界面
- `/fancy memory` - 管理记忆面板（一般不需要调用）

### 模式切换
- `/fancy normal` - 让 Fancy 进入 normal 模式，该模式调用命令需要确认
- `/fancy yolo` - 让 Fancy 进入 YOLO 模式，调用命令无需确认

### 更新命令
- `/fancy update` - 检查并安装更新
- `/fancy checkupdate` - 检查是否有更新
- `/fancy upgrade` - 安装更新

### Skill 命令
- `/fancy skill list` - 列出所有 Skill
- `/fancy skill info <id>` - 查看 Skill 详情
- `/fancy skill load <id>` - 加载 Skill 到当前对话
- `/fancy skill reload` - 重新加载所有 Skill（需要权限）
