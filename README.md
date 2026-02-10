# FancyHelper 🤖

<img src="./image.jpg" alt="FancyHelper" width="200"/>

FancyHelper 是一款基于 AI 驱动的 Minecraft 服务器管理助手插件。它允许管理员通过简单的自然语言与 AI 对话，自动生成并执行复杂的服务器指令，极大地降低了插件学习成本和管理负担。

## ✨ 核心特性

- **💬 CLI 交互模式**：通过游戏内聊天框进入沉浸式对话，像和真人管理员交流一样管理服务器。
- **🧠 强力 AI 驱动**：接入 CloudFlare Workers AI，默认使用 `gpt-oss-120b` 大模型，理解意图更精准。
- **🛡️ 安全执行机制**：AI 生成的指令需要玩家手动确认后才会执行，确保服务器安全。
- **🔍 智能文档搜索**：内置多种主流插件预设（LuckPerms, EssentialsX, WorldEdit 等），并支持在 Wiki 无结果时自动进行全网搜索。
- **📊 结果反馈闭环**：指令执行后的结果会自动反馈给 AI，使其能根据执行情况进行下一步操作或错误修正。
- **🔄 配置自愈**：支持配置文件版本检测与自动更新，无需担心插件升级导致的配置丢失。

## 📋 要求

- **Java**: 17 或更高版本
- **服务器版本**: Spigot/Paper 1.18 - 1.21
- **网络**: 服务器需能访问 CloudFlare API 接口

## ⚠️ 常见问题

### 安全聊天警告

如果服务器日志中出现以下警告：

```
[WARN]: Failed to update secure chat state for <player>: 'Chat disabled due to missing profile public key. Please try reconnecting.'
```

**原因**：这是 Minecraft 服务器的安全聊天功能（`enforce-secure-profile`）产生的警告，不是 FancyHelper 插件的问题。当服务器启用了安全配置文件验证，但玩家的配置文件缺少公钥时就会出现此警告。

**解决方案**：
FancyHelper 会自动检测此问题并尝试将 `server.properties` 中的 `enforce-secure-profile` 设置为 `false`。如果自动修改成功，只需重启服务器即可。

如果自动修改失败，请手动执行以下步骤：
1. 打开服务器的 `server.properties` 文件
2. 将 `enforce-secure-profile` 设置为 `false`
3. 重启服务器

**注意**：此警告不影响 FancyHelper 的正常功能，可以安全忽略。

## 🚀 快速开始

### 1. 安装插件

1. 下载最新的 `FancyHelper.jar` 并放入服务器的 `plugins` 文件夹。
2. 重启服务器以生成默认配置文件。

### 2. 配置 CloudFlare AI

1. 前往 [CloudFlare 控制台](https://dash.cloudflare.com/) 获取你的key或在 [共享站](https://litemotto.is-a.dev/) 拿一个，可参考LiteMotto获取key的方法。当然你可以用自带的key，但是这个key很有可能会被禁用或者耗尽。
2. 编辑 `plugins/FancyHelper/config.yml`：

   ```yaml
   cloudflare:
     cf_key: 你的_CLOUDFLARE_API_KEY
   ```

3. 在游戏中输入 `/fancyhelper reload` 重载配置。

### 3. 使用方法

- 在游戏中输入 `/cli` 或 `/fancy` 进入 AI 对话模式。
- 直接输入你的需求，例如：“给玩家 YanPl 设置为管理员组” 或 “在当前位置生成一个 10x10 的石头平台”。
- 预览 AI 生成的指令，确认无误后确认执行。

## 🛠️ 指令与权限

| 指令 | 描述 | 默认权限 |
| :--- | :--- | :--- |
| `/fancyhelper` | 插件主指令 (别名: `/cli`, `/fancy`) | `fancyhelper.cli` |
| `/fancyhelper reload` | 重载插件配置 | `fancyhelper.reload` |

## 📚 支持的插件预设

FancyHelper 内置了对以下插件的深度理解（位于 `src/main/resources/preset/`）：

- EssentialsX, LuckPerms, WorldEdit, Vault, Residence, Multiverse, CoreProtect, Citizens, mcMMO, GriefPrevention 等。

## 🛠️ 开发与构建

项目使用 Maven 进行管理。

```bash
git clone https://github.com/baicaizhale/FancyHelper.git
cd FancyHelper
mvn clean package
```

---

 # **© 2026 baicaizhale. 保留所有权利。**
