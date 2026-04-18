---
name: "Give 物品给予"
description: "Minecraft /give 命令的完整使用指南，包含基础用法、NBT/组件格式等"
triggers:
  - "give"
  - "物品"
  - "item"
  - "给予"
  - "nbt"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "vanilla"
  - "command"
  - "item"
---

# /give 命令使用指南

## 命令检查

`/give` 命令是 Minecraft 原版命令，用于给予玩家物品。

语法：`/give <玩家> <物品>[NBT/组件] [数量]`

## 版本注意

- 如果服务器版本是 **1.20.5 或更高**，请务必使用 **[组件格式]** 而不是 `{NBT格式}`
- 如果不确定版本，请先查看系统提示中的 "当前 Minecraft 版本"
- 详细的 NBT/组件格式请查阅 `nbt-format` Skill

## 常用用法

### 基础给予
```
/give @p apple 64
```

### 给予带名字的物品 (1.20.5+)
```
/give @p apple[custom_name='{"text":"红苹果","color":"red"}'] 1
```

### 给予带名字的物品 (1.20.5以前)
```
/give @p apple{display:{Name:'{"text":"红苹果","color":"red"}'}} 1
```

### 给予附魔物品 (1.20.5+)
```
/give @p diamond_sword[enchantments={levels:{"minecraft:sharpness":5}}] 1
```

### 给予附魔物品 (1.20.5以前)
```
/give @p diamond_sword{Enchantments:[{id:"minecraft:sharpness",lvl:5s}]} 1
```
