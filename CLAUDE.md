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
- `§8` → 深灰色，用于分隔线和 `▌` 左边界

示例：
```java
// 正确
player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper §7> §a操作成功"));

// 错误 — 不使用原始 § 代码
player.sendMessage("§a操作成功");
```

## 框式界面风格

信息面板（Skill Info、进入/退出 CLI 等）采用上下对称的框式布局：

```
§8§m------------------------------------

▌ §zFancyHelper §8§m── §e标题 §b高亮值
▌ §f内容行  §7辅助信息
▌ §7描述文字

§8§m------------------------------------
```

规则：
- **分隔线**：`§8§m` + 36 个 `-`（深灰删除线），上下对称，与空行间隔
- **左边界**：每行内容以 `§8▌ ` 开头（深灰竖条 + 空格）
- **标题区**：`§zFancyHelper §8§m──` + `§e` 标题 + `§b` 高亮值
- **正文**：`§f` 白色，辅助信息 `§7` 灰色，强调 `§e` 黄色
- **构建方式**：涉及 hex 颜色或交互按钮时使用 `TextComponent` + `setColor()`，简单文本使用 `ColorUtil.translateCustomColors("§...")`
