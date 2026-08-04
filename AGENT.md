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

### 消息格式规范与多语言（I18n）

插件支持三语言：`zh-cn`（简体中文，基准表）、`en-us`（美式英文）、`lzh-cn`（文言文），通过 `config.yml` 的 `settings.language` 切换（`/cli reload` 即时生效）。语言表硬编码在 `src\main\java\org\YanPl\util\I18n.java`，**不需要也不应该**放入 `src\main\resources`。

#### 多语言规则（重要）
1. **玩家可见消息**（sendMessage、TextComponent、GUI 物品名/Lore、书本页等）**必须**通过 `I18n.t("key", args...)` 获取，禁止硬编码中文/英文文本。
2. 新增消息时，key 必须在 `I18n.java` 的**三张语言表**（ZH_CN / EN_US / LZH_CN）中都添加；key 缺失时回退中文，中文也缺失时原样返回 key。
3. `I18n.t()` 返回前已自动调用 `ColorUtil.translateCustomColors(...)` 处理颜色码，**不要**再对返回值重复包裹 ColorUtil。
4. **不翻译**的内容：控制台消息（`Bukkit.getConsoleSender()`）、`plugin.getLogger()` 调试日志、AI 提示词/协议串（如 `#error:`、`#run_result`）。这些可直接使用 `ColorUtil.translateCustomColors(...)` 或保持原样。
5. 占位符使用 `{0}`、`{1}` 等格式，调用时按顺序传参。

#### 统一前缀格式
```text
§zFancyHelper§b§r §7> §f
```
整条消息**默认使用白色（§f）**，不允许整体使用其他颜色，仅允许在部分高亮处使用其他颜色（如 `§a` 成功、`§c` 错误）。

#### 错误调用方式（必须避免）
1. **玩家可见消息硬编码文本**：
   ```java
   // ✗ 错误：绕过 I18n，无法随语言切换
   player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §c操作失败"));
   ```
2. **直接使用 `ChatColor` 常量拼接**（绕过 ColorUtil/I18n）：
   ```java
   // ✗ 错误：直接使用 ChatColor.RED 等原生常量
   player.sendMessage(ChatColor.RED + "操作失败");
   ```
3. **直接发送原始字符串**（绕过 ColorUtil）：
   ```java
   // ✗ 错误：发送包含 § 或 & 的字符串但不加 ColorUtil
   player.sendMessage("§8▌ §e✦ §fFancyHelper");
   ```
4. **Bungee/Spigot 组件与 ActionBar/Title 未转换**：
   ```java
   // ✗ 错误：在构造 HoverEvent/TextComponent 时未经过 ColorUtil/I18n
   TextComponent btn = new TextComponent(ChatColor.GRAY + "点击");
   // ✗ 错误：ActionBar 和 Title 传入原始字符串
   player.sendTitle("", rawSubtitle, 0, 20, 0);
   ```

#### ✅ 正确调用范式
1. **纯文本发送（sendMessage）**：
   ```java
   // ✓ 正确：玩家可见消息走 I18n.t（内部已处理 ColorUtil）
   player.sendMessage(I18n.t("cli.xxx.failed"));
   // 带参数：
   player.sendMessage(I18n.t("cli.xxx.count", count));
   ```
2. **交互式 Component（Click/Hover）**：
   ```java
   // ✓ 正确：使用 TextComponent.fromLegacyText 结合 I18n.t（注意：直接 new TextComponent(String) 会导致 hex 颜色失效）
   TextComponent msg = new TextComponent(TextComponent.fromLegacyText(I18n.t("cli.xxx.interactive")));
   ```
3. **Title 与 ActionBar**：
   ```java
   // ✓ 正确：传递给 Title 和 ActionBar 前先经过 I18n.t / ColorUtil 转换
   player.sendTitle("", I18n.t("cli.xxx.title"), 0, 20, 0);
   ```
4. **控制台消息 / 调试日志**（无需翻译，直接用 ColorUtil）：
   ```java
   // ✓ 正确：仅控制台可见，保持中文即可
   Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检测到新版本"));
   plugin.getLogger().info("[CLI] 调试信息");
   ```
5. **自定义 Helper 方法封装**：
   在使用类似 `msg(String)` 等快捷消息辅助方法时，需确保该方法最终返回值经过了 `ColorUtil.translateCustomColors(...)` / `I18n.t(...)` 统一处理。

## After Making Changes

1. Run `mvn clean package` to verify build and tests
3. Check for any new warnings in output
4. Ensure no changes to AI model configuration
