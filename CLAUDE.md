# FancyHelper 开发规范

## 颜色消息格式

所有发送给玩家的消息，必须使用 `ColorUtil.translateCustomColors()` 包裹，统一格式：

```java
ColorUtil.translateCustomColors("§zFancyHelper §7> §f消息内容")
```

- `§z` → `#30AEE5`（天蓝色），用于 "FancyHelper" 字样
- `§7` → 灰色，用于 `> ` 分隔符
- `§f` → 白色，用于消息正文
- `§a` → 绿色，成功/确认
- `§c` → 红色，错误/失败
- `§e` → 黄色，强调/数字

示例：
```java
// 正确
player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper §7> §a操作成功"));

// 错误 — 不使用原始 § 代码
player.sendMessage("§a操作成功");
```
