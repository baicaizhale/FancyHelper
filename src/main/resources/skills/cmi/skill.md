---
name: "cmi"
description: "CMI 全能基础插件使用指南，涵盖传送、经济、聊天、领地、礼包等功能，请先确保服务器安装的是此插件而不是essentalsx"
triggers:
  - "cmi"
  - "传送"
  - "home"
  - "warp"
  - "spawn"
  - "tpa"
  - "经济"
  - "money"
  - "balance"
  - "kit"
  - "礼包"
  - "vanish"
  - "隐身"
  - "god"
  - "fly"
  - "jail"
  - "监狱"
  - "ban"
  - "tempban"
  - "mute"
  - "禁言"
  - "hologram"
  - "全息"
  - "portal"
  - "传送门"
  - "rankup"
  - "升级"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "teleport"
  - "economy"
  - "chat"
  - "admin"
---

# CMI 全能插件使用指南

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 CMI 插件及 CMILib 依赖。

检查方法：尝试执行 `/cmi version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家使用原版命令或其他已安装插件替代。

**注意**：CMI 和 EssentialsX 功能高度重叠，两个同时安装会导致冲突。如果服务器已安装 CMI，通常不需要 EssentialsX。

## 权限规则

CMI 的权限节点遵循固定格式：
- 命令权限：`cmi.command.<命令名>`（如 `cmi.command.heal`）
- 作用于他人：`cmi.command.<命令名>.others`
- 跳过冷却：`cmi.command.<命令名>.cooldownbypass`
- 跳过预热：`cmi.command.<命令名>.warmupbypass`
- 跳过费用：`cmi.costbypass.<命令名>`

**默认玩家没有任何 CMI 命令权限**，需要管理员通过 LuckPerms 等权限插件授予。

## 常用命令

### 传送
- `/home [名称]` - 传送到家
- `/sethome [名称]` - 设置家
- `/homes` - 列出所有家
- `/warp <名称>` - 传送到地标
- `/setwarp <名称>` - 创建地标
- `/tpa <玩家>` - 请求传送到玩家
- `/tpahere <玩家>` - 请求玩家传送到你
- `/tpaccept` / `/tpdeny` - 接受/拒绝传送请求
- `/back` - 返回上一个位置
- `/dback` - 返回死亡地点
- `/spawn` - 传送到出生点
- `/tppos <x> <y> <z>` - 传送到坐标
- `/top` - 传送到最高点
- `/jump` - 跳到准星目标方块
- `/rt` - 随机传送
- `/world <世界名>` - 跨世界传送

### 玩家管理
- `/heal [玩家/all]` - 治疗（支持百分比如 `10%`）
- `/feed [玩家]` - 恢复饱食度
- `/fly [玩家]` - 切换飞行模式
- `/flyspeed [0-10]` - 设置飞行速度
- `/walkspeed [0-10]` - 设置行走速度
- `/god [玩家]` - 切换无敌模式
- `/gm <模式>` - 切换游戏模式 (survival/creative/adventure/spectator)
- `/vanish` - 切换隐身模式
- `/glow [颜色]` - 设置发光效果
- `/tfly [玩家] [时间]` - 临时飞行
- `/tgod [玩家] [时间]` - 临时无敌
- `/exp <数量>` - 管理经验值
- `/effect <效果> [时间] [等级]` - 施加药水效果
- `/burn <玩家> <时间>` - 点燃玩家
- `/ext [玩家]` - 熄灭玩家身上的火
- `/launch <玩家> [力度]` - 弹射玩家

### 背包与物品
- `/inv [玩家]` - 查看玩家背包
- `/ender [玩家]` - 打开末影箱
- `/give <玩家> <物品> [数量]` - 给予物品
- `/item <物品> [数量]` - 给自己物品
- `/more` - 补满手中物品堆叠
- `/hat` - 将手中物品戴在头上
- `/itemname <名称>` - 重命名物品
- `/itemlore` - 编辑物品描述
- `/enchant <附魔> [等级]` - 附魔物品
- `/repair [hand/armor/all]` - 修复物品耐久
- `/condense` - 将物品压缩成方块
- `/anvil` - 打开虚拟铁砧
- `/workbench` - 打开工作台
- `/grindstone` - 打开砂轮
- `/sell [hand/all/blocks]` - 出售物品
- `/worth` - 查看物品价值
- `/dispose` - 打开垃圾桶界面

### 聊天与通讯
- `/msg <玩家> <消息>` - 发送私聊
- `/reply <消息>` - 回复最后私聊
- `/broadcast <消息>` - 发送全服公告
- `/mail send <玩家> <消息>` - 发送邮件
- `/mail read` - 阅读邮件
- `/mail clear` - 清空邮件
- `/ignore <玩家>` - 屏蔽玩家
- `/chatcolor` - 选择聊天颜色
- `/me <动作>` - 发送动作消息
- `/nick <昵称>` - 修改显示名称
- `/realname <昵称>` - 查询昵称对应的真实名称
- `/clearchat` - 清除聊天屏幕

### 经济
- `/money` / `/balance` - 查看余额
- `/money give <玩家> <金额>` - 给予金钱
- `/money take <玩家> <金额>` - 扣除金钱
- `/money set <玩家> <金额>` - 设定金钱
- `/pay <玩家> <金额>` - 转账给玩家
- `/baltop` - 查看财富排行榜
- `/cheque <金额>` - 将钱转为支票物品
- `/worth <物品>` - 查看物品出售价格
- `/setworth` - 设置物品价值

### 管理处罚
- `/ban <玩家> [原因]` - 封禁玩家
- `/tempban <玩家> <时间> [原因]` - 临时封禁
- `/ipban <玩家>` - IP封禁
- `/unban <玩家>` - 解封
- `/kick <玩家> [原因]` - 踢出玩家
- `/mute <玩家> [时间]` - 禁言玩家
- `/mutechat` - 全服禁言
- `/jail <玩家> [时间] [监狱]` - 关入监狱
- `/unjail <玩家>` - 释放出狱
- `/warn <玩家> [原因]` - 警告玩家
- `/warnings [玩家]` - 查看警告记录
- `/kill <玩家>` - 杀死玩家
- `/cuff <玩家>` - 束缚玩家（禁止操作）
- `/sudo <玩家> <命令>` - 强制玩家执行命令

### 礼包 (Kit)
- `/kit [名称]` - 领取礼包
- `/kiteditor` - 编辑礼包（GUI界面）
- `/kitcdreset <玩家> <礼包>` - 重置礼包冷却

### 全息投影 (Hologram)
- `/hologram create <名称>` - 创建全息文字
- `/hologram addline <名称> <内容>` - 添加一行
- `/hologram removeline <名称> <行号>` - 删除一行
- `/hologram movehere <名称>` - 移动到当前位置
- `/hologram remove <名称>` - 删除全息文字

### 传送门 (Portal)
- `/portals` - 打开传送门管理界面
- `/portal <名称>` - 使用指定传送门

### 排行升级 (Rankup)
- `/rankup` - 升级到下一级
- `/ranks` - 查看可用等级列表
- `/rankinfo` - 查看当前等级信息

### 工具与信息
- `/info [玩家]` - 查看玩家详细信息
- `/list` - 查看在线玩家列表
- `/seen <玩家>` - 查看玩家最后在线时间
- `/ping [玩家]` - 查看延迟
- `/stats [玩家]` - 查看玩家统计
- `/pos` - 显示当前位置坐标
- `/near [距离]` - 查看附近玩家
- `/playtime [玩家]` - 查看游玩时间
- `/skin <玩家名>` - 更改皮肤
- `/sit` - 坐下
- `/ride` - 骑乘目标实体
- `/armorstand` - 打开盔甲架编辑器
- `/search <条件>` - 搜索玩家（可按物品/飞行/模式等条件）
- `/scan` - 扫描容器中的物品
- `/spawner <类型>` - 修改刷怪笼类型
- `/counter <时间>` - 开始倒计时

### 配置与管理
- `/cmi reload` - 重载配置
- `/cmi checkcommand <关键词>` - 搜索命令
- `/cmi checkperm <关键词>` - 搜索权限
- `/cmi aliaseditor` - 编辑命令别名
- `/cmi importfrom essentials <类型>` - 从Essentials导入数据(支持类型：home/money/mail/warp/nick/logoutlocation)
- `/cmi saveall` - 保存所有玩家数据
- `/cmi version` - 查看插件版本
- `-s` 标志 - 在任何命令后加 `-s` 可静默执行（不显示反馈消息）

## 数据库

- 默认使用 SQLite，存储于 `plugins/CMI/cmi.sqlite.db`
- 可在 `databaseInfo.yml` 中配置 MySQL 连接
- 使用 `/cmi migratedatabase` 在不同数据库间迁移

## 注意

1. CMI 的经济和聊天模块默认关闭，需要在 `plugins/CMI/config.yml` 中将 `Economy.Enabled` 和 `Chat.Enabled` 设为 `true`
2. CMI 依赖 CMILib 库插件，缺少时无法加载
3. CMI 与 EssentialsX 功能重叠，二选一安装即可，同时安装会导致命令冲突
4. 大部分命令可通过 `/cmi aliaseditor` 创建短别名（如 `/heal` 代替 `/cmi heal`）
5. CMI 拥有超过 300 条命令，以上仅列出最常用部分。如需查询更多命令，可用 `/cmi checkcommand <关键词>` 搜索
6. 玩家默认没有任何命令权限，管理员需通过权限插件（如 LuckPerms）授予具体权限节点
7. 如需完整的命令和权限列表，请访问官方文档：https://www.zrips.net/cmi/
