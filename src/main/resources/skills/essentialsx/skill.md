---
name: "EssentialsX 助手"
description: "EssentialsX 插件的完整使用指南，包含所有常用命令和配置"
triggers:
  - "essentials"
  - "ess"
  - "经济"
  - "传送"
  - "home"
  - "warp"
  - "spawn"
  - "tpa"
auto_trigger: true
author: "FancyHelper Team"
version: "2.0.0"
categories:
  - "plugin"
  - "economy"
  - "teleport"
  - "essential"
---

# EssentialsX 使用指南

## 快速检查
在使用前，请先确认服务器已安装 EssentialsX：
```
#run: essentials version
```

## 基础命令

### 经济系统
- `/balance` 或 `/money` - 查看余额
- `/pay <玩家> <金额>` - 转账给其他玩家
- `/baltop` - 查看财富排行榜

### 传送系统
- `/home` - 回到主家
- `/home <名称>` - 回到指定家
- `/sethome [名称]` - 设置家（默认主家）
- `/delhome <名称>` - 删除家
- `/warp <名称>` - 传送到地标
- `/setwarp <名称>` - 设置地标（需要权限）
- `/delwarp <名称>` - 删除地标（需要权限）
- `/spawn` - 回到出生点
- `/setspawn` - 设置出生点（需要权限）
- `/back` - 回到上一个位置

### 玩家传送
- `/tpa <玩家>` - 请求传送到某玩家
- `/tpahere <玩家>` - 请求某玩家传送到你
- `/tpaccept` - 接受传送请求
- `/tpdeny` - 拒绝传送请求
- `/tpcancel` - 取消传送请求

### 游戏模式
- `/gamemode <模式>` 或 `/gm <模式>` - 切换游戏模式
- `/gms` - 切换到生存模式
- `/gmc` - 切换到创造模式
- `/gma` - 切换到冒险模式
- `/gmsp` - 切换到旁观模式

### 实用工具
- `/afk` - 标记为离开
- `/msg <玩家> <消息>` 或 `/tell <玩家> <消息>` - 私聊
- `/r <消息>` - 回复最后私聊
- `/ignore <玩家>` - 忽略某玩家
- `/unignore <玩家>` - 取消忽略
- `/me <动作>` - 动作描述
- `/nick <昵称>` - 设置昵称
- `/realname <昵称>` - 查看真实ID
- `/whois <玩家>` - 查看玩家信息
- `/getpos` - 查看当前坐标
- `/compass` - 显示指南针方向
- `/depth` - 显示高度
- `/time` - 查看世界时间

## 管理员命令

### 经济管理
- `/eco give <玩家> <金额>` - 给予金钱
- `/eco take <玩家> <金额>` - 扣除金钱
- `/eco set <玩家> <金额>` - 设置金钱
- `/eco reset <玩家>` - 重置金钱

### 玩家管理
- `/kick <玩家> [原因]` - 踢出玩家
- `/ban <玩家> [原因]` - 封禁玩家
- `/unban <玩家>` - 解封玩家
- `/mute <玩家> [时间]` - 禁言玩家
- `/unmute <玩家>` - 解除禁言
- `/jail <玩家> [时间]` - 监禁玩家
- `/unjail <玩家>` - 解除监禁
- `/invsee <玩家>` - 查看玩家背包
- `/enderchest <玩家>` - 查看玩家末影箱

### 服务器管理
- `/essentials reload` - 重载配置
- `/gc` - 查看服务器性能
- `/lag` - 查看服务器延迟
- `/killall [生物类型]` - 清除生物
- `/fireball` - 发射火球
- `/lightning [玩家]` - 召唤闪电
- `/nuke [玩家]` - 发射核弹

## 权限节点

### 基础权限
- `essentials.home` - 使用家功能
- `essentials.sethome` - 设置家
- `essentials.sethome.multiple` - 设置多个家
- `essentials.warp` - 使用地标
- `essentials.warp.list` - 查看地标列表
- `essentials.spawn` - 使用出生点
- `essentials.tpa` - 使用传送请求
- `essentials.balance` - 查看余额
- `essentials.pay` - 转账

### 管理员权限
- `essentials.*` - 所有权限
- `essentials.eco` - 经济管理
- `essentials.kick` - 踢出玩家
- `essentials.ban` - 封禁玩家
- `essentials.mute` - 禁言玩家
- `essentials.jail` - 监禁玩家

## 配置说明

主要配置文件：`plugins/Essentials/config.yml`

常用配置项：
- `change-displayname` - 是否允许修改显示名称
- `change-playerlist` - 是否允许修改玩家列表名称
- `add-prefix-suffix` - 是否添加前缀后缀
- `nickname-prefix` - 昵称前缀
- `max-nick-length` - 最大昵称长度
- `ignore-colors-in-max-nick-length` - 忽略颜色代码计算长度
- `teleport-cooldown` - 传送冷却时间（秒）
- `teleport-delay` - 传送延迟（秒）
- `warp-permissions` - 是否启用地标权限
- `world-home-permissions` - 是否启用世界家权限
- `sethome-multiple` - 设置多个家的数量限制
- `backup-interval` - 备份间隔（分钟）

## 注意事项

1. **经济功能**需要 Vault 插件支持
2. **聊天格式**需要 EssentialsXChat 模块
3. **出生点控制**需要 EssentialsXSpawn 模块
4. **保护功能**需要 EssentialsXProtect 模块
5. **反建筑**需要 EssentialsXAntiBuild 模块
6. 部分命令需要特定权限才能使用
7. 建议在配置文件中仔细设置权限和限制

## 相关链接

- 官方文档：https://essentialsx.net/wiki/Home.html
- Spigot 页面：https://www.spigotmc.org/resources/essentialsx.9089/
- GitHub：https://github.com/EssentialsX/Essentials
