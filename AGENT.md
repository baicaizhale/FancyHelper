---
alwaysApply: true
description: Build commands, code style, and conventions for FancyHelper
---

# FancyHelper - Agent Guidelines

## language

完全使用中文，在任何场景

## Project Overview

- **Type**: Minecraft Bukkit/Spigot Plugin
- **JDK**: Microsoft JDK 17+
- **Build Tool**: Maven
- **Purpose**: Generate Minecraft commands from natural language using AI

## Build Commands

编译项目请使用：

```bash
mvn clean package
```


## Code Style Guidelines

### Types

- Use `var` only when type is obvious from context
- Prefer `String` methods over `StringBuilder` for simple concatenation
- Use `Optional` for nullable return values where appropriate
- Enum for fixed sets of values (e.g., `TodoItem.Status`)

### Comments

- Chinese comments are preferred

## Critical Rules

### Color Theme

- Primary: White + Light Gray + Sky Blue (#30AEE5)
- Warning: Yellow
- Error: Red
- Custom colors:
  - `§x` / `&x` → `#11A8CD` (青色偏蓝)
  - `§z` / `&z` → `#30AEE5` (明亮的天蓝色)
  - 颜色转换实现见：`src\main\java\org\YanPl\util\ColorUtil.java`

### 消息格式规范与 ColorUtil 正确调用方式

所有发送给玩家/控制台的消息（包括提示、报错、公告、ActionBar、Title 等）**必须**通过 `ColorUtil.translateCustomColors(...)` 进行统一转换，以保证 `§z`、`&z` 等品牌专属色和标准颜色代码能被正确解析。

统一前缀格式：
```text
§zFancyHelper§b§r §7> §f
```
整条消息**默认使用白色（§f）**，不允许整体使用其他颜色，仅允许在部分高亮处使用其他颜色（如 `§a` 成功、`§c` 错误）。

#### 错误调用方式（必须避免）
1. **直接使用 `ChatColor` 常量拼接**（绕过 ColorUtil）：
   ```java
   // ✗ 错误：直接使用 ChatColor.RED 等原生常量
   player.sendMessage(ChatColor.RED + "操作失败");
   ```
2. **直接发送原始字符串**（绕过 ColorUtil）：
   ```java
   // ✗ 错误：发送包含 § 或 & 的字符串但不加 ColorUtil
   player.sendMessage("§8▌ §e✦ §fFancyHelper");
   ```
3. **Bungee/Spigot 组件与 ActionBar/Title 未转换**：
   ```java
   // ✗ 错误：在构造 HoverEvent/TextComponent 时未经过 ColorUtil
   TextComponent btn = new TextComponent(ChatColor.GRAY + "点击");
   // ✗ 错误：ActionBar 和 Title 传入原始字符串
   player.sendTitle("", rawSubtitle, 0, 20, 0);
   ```

#### ✅ 正确调用范式
1. **纯文本发送（sendMessage）**：
   ```java
   // ✓ 正确：必须包裹一层 ColorUtil.translateCustomColors
   player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §c操作失败"));
   ```
2. **交互式 Component（Click/Hover）**：
   ```java
   // ✓ 正确：使用 TextComponent.fromLegacyText 结合 ColorUtil（注意：直接 new TextComponent(String) 会导致 hex 颜色失效）
   TextComponent msg = new TextComponent(TextComponent.fromLegacyText(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f交互文本")));
   ```
3. **Title 与 ActionBar**：
   ```java
   // ✓ 正确：传递给 Title 和 ActionBar 前先使用 ColorUtil 转换
   player.sendTitle("", ColorUtil.translateCustomColors(rawSubtitle), 0, 20, 0);
   ```
4. **自定义 Helper 方法封装**：
   在使用类似 `msg(String)` 等快捷消息辅助方法时，需确保该方法最终返回值经过了 `ColorUtil.translateCustomColors(...)` 统一处理。

## After Making Changes

1. Run `mvn clean package` to verify build and tests
3. Check for any new warnings in output
4. Ensure no changes to AI model configuration
