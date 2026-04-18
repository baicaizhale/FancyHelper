---
name: "CoreProtect 日志查询"
description: "CoreProtect 日志查询与回档插件的完整使用指南，包含查询、回档、恢复等"
triggers:
  - "coreprotect"
  - "co"
  - "日志"
  - "查询"
  - "rollback"
  - "回档"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "logging"
  - "admin"
---

# CoreProtect 插件

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 CoreProtect 插件。

检查方法：尝试执行 `/co version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家无法使用日志查询和回档功能。

## 基础命令

- `/co help` - 显示帮助信息
- `/co version` - 查看插件版本
- `/co reload` - 重新加载配置文件

## 查询模式

- `/co inspect` (或 `/co i`) - 开启/关闭查询模式
  - 开启后，点击或破坏方块可查看该位置的修改记录
  - 再次执行该命令可关闭查询模式
  - 查询模式下左键方块 = 查看该位置的日志
  - 查询模式下右键方块 = 查看放置在该位置的方块日志

## 回档与恢复

- `/co rollback <参数>` - 回档（撤销行为，将改动恢复到之前的状态）
- `/co restore <参数>` - 恢复（重做行为，将回档的操作重新应用）

## 日志查询

- `/co lookup <参数>` - 详细查询日志记录
- `/co near <半径>` - 查询附近的方块变化（默认5格）
- `/co l <参数>` - `/co lookup` 的简写

## 翻页命令

- `/co page <页码>` - 查看查询结果的指定页
- `/co next` - 查看下一页结果（简写）
- `/co prev` - 查看上一页结果

## 参数说明

### 用户参数 `u:`
- `u:<用户名>` - 指定玩家名称
- `u:#*` - 所有玩家
- 示例：`u:Steve`, `u:Notch`, `u:#*`

### 时间参数 `t:`
- `t:<时间>` - 指定时间范围
- 格式：`s`=秒, `m`=分钟, `h`=小时, `d`=天, `w`=周, `m`=月, `y`=年
- 示例：`t:10s`, `t:5m`, `t:2h`, `t:1d`, `t:1w`

### 半径参数 `r:`
- `r:<数字>` - 以玩家当前位置为中心的半径
- `r:#global` - 全局范围（整个服务器）
- `r:#world` - 当前世界范围
- `r:#worldedit` - WorldEdit 选区范围（需安装WE）

### 动作参数 `a:`
- `a:+block` - 方块放置
- `a:-block` - 方块破坏
- `a:+container` - 物品放入容器
- `a:-container` - 物品从容器取出
- `a:kill` - 生物击杀
- `a:session` - 玩家登录/登出
- `a:chat` - 聊天消息
- `a:command` - 执行的命令

### 方块参数 `b:`
- `b:<方块ID或名称>` - 筛选特定方块
- 示例：`b:stone`, `b:diamond_ore`, `b:chest`

### 排除参数 `#`
- 在参数前加 `#` 表示排除
- 示例：`u:#Steve` - 排除 Steve 的操作

## 常用命令示例

### 查询示例
```
/co l u:Steve t:1d r:20 - 查询 Steve 在20格半径内1天的操作
/co l t:1h r:#global a:-block - 查询1小时内全服所有方块破坏记录
/co l u:#* t:30m r:50 a:+container - 查询30分钟内50格半径所有玩家的容器放入记录
```

### 回档示例
```
/co rollback u:Steve t:1h r:20 - 回档 Steve 在20格半径内1小时的所有操作
/co rollback u:Griefer t:1d r:#global - 回档 Griefer 1天内全服的所有操作
/co rollback t:10m r:100 a:-block - 回档10分钟内100格半径所有方块破坏
```

### 恢复示例
```
/co restore u:Steve t:1h r:20 - 恢复 Steve 在20格半径内1小时的操作
/co restore t:30m r:50 a:+block - 恢复30分钟内50格半径放置的方块
```

## 管理员命令

- `/co purge t:<时间>` - 清除指定时间之前的日志数据
  - 示例：`/co purge t:30d` - 清除30天前的日志
  - **注意**：此操作不可逆，请谨慎使用
- `/co status` - 查看数据库状态和性能信息
