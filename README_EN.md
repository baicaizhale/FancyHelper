# FancyHelper

<img src="pic/image.webp" alt="FancyHelper Logo" width="50%">

> Manage your Minecraft server with natural language.

[![文言](https://img.shields.io/badge/-文言(华夏)-D54B4B?style=flat)](README_LZH.md)
[![中文](https://img.shields.io/badge/-中文(简体)-3178C6?style=flat)](README.md)

[![CI](https://github.com/baicaizhale/FancyHelper/actions/workflows/CI-Build-Release.yml/badge.svg)](https://github.com/baicaizhale/FancyHelper/actions)
[![License](https://img.shields.io/github/license/baicaizhale/FancyHelper?color=blue)](LICENSE)
[![Stars](https://img.shields.io/github/stars/baicaizhale/FancyHelper?color=yellow&logo=github)](https://github.com/baicaizhale/FancyHelper/stargazers)
[![Issues](https://img.shields.io/github/issues/baicaizhale/FancyHelper?color=red)](https://github.com/baicaizhale/FancyHelper/issues)
[![Download](https://img.shields.io/badge/download-builds-orange?logo=github)](https://fancy.baicaizhale.top/)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/baicaizhale/Fancyhelper)

---

Tired of digging through Wikis and memorizing complex commands just to change a permission or a config file?

FancyHelper is here to solve this problem. Once installed, you can talk directly to an AI in-game. For example, say "Set baicaizhale as admin," and it will generate the corresponding command, ask for your confirmation, and execute it. No more command memorization, no more manual searching.

## Features

- **Chat-based Management** — Type `/cli` to enter conversation mode. Manage your server as if you're chatting with a human co-admin.
- **AI Command Generation** — Integrated with CloudFlare Workers AI (default: `gpt-oss-120b`). You state the intent; it writes the command.
- **Multi-Model Support** — Supports CloudFlare, OpenAI, DeepSeek, Azure OpenAI, local Ollama models, and more.
- **Pre-execution Confirmation** — AI-generated commands require manual confirmation (`y`/`n`) by default to prevent accidents.
- **YOLO Mode** — Tired of confirming? After agreeing to the terms, most commands execute automatically, though high-risk operations like `op`, `ban`, and `stop` still require approval.
- **Real-time Status Bar** — The Action Bar displays what the AI is currently doing (Thinking / Executing / Waiting for confirmation).
- **Built-in Wiki Search** — Comes with documentation presets for LuckPerms, EssentialsX, WorldEdit and other major plugins. Falls back to web search if nothing is found.
- **Feedback Loop** — The output of executed commands is fed back to the AI. If something fails, the AI can correct itself.
- **Automatic Config Updates** — No need to manually edit configs during upgrades; it handles them automatically.
- **Anti-Loop Protection** — Automatically intercepts the AI if it starts repeating operations or making excessive calls.

## Compatibility

| Server | Version | Java |
|--------|------|------|
| Spigot | 1.18+ | 17+ |
| Paper | 1.18+ | 17+ |

**Dependencies:**
- [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) 5.4.0+ — Used to capture command output and intercept system messages. The plugin works without it, but some features will be incomplete.
  You can use `/fancy lib install protocollib` to download and install it automatically (OP permission required).

## Demo

<video src="./pic/demo.mp4" controls width="100%" muted></video>

<details>
<summary>Click to Expand/Collapse Preview Images</summary>

<img src="./pic/1.webp" alt="FancyHelper Preview 1" width="200"/>
<img src="./pic/2.webp" alt="FancyHelper Preview 2" width="200"/>
<img src="./pic/3.webp" alt="FancyHelper Preview 3" width="200"/>
<img src="./pic/4.webp" alt="FancyHelper Preview 4" width="200"/>
<img src="./pic/5.webp" alt="FancyHelper Preview 5" width="200"/>
<img src="./pic/6.webp" alt="FancyHelper Preview 6" width="200"/>

</details>

## Quick Start

### Installation

1. Download `FancyHelper.jar` and place it in the server's `plugins` folder.
2. Install the dependency ProtocolLib (make sure to match the version to your server).
3. Restart the server; configuration files will be generated automatically.

### Configure AI (Optional, defaults to CloudFlare)

**CloudFlare Workers AI:**
Tutorial: [![blog](https://img.shields.io/badge/CF%20Key%20Setup%20Guide-Blog-blue)](https://blog.baicaizhale.top/post/create-cf-key-for-fhai)

**OpenAI Compatible API:**
Tutorial: [![blog](https://img.shields.io/badge/OpenAI%20Compatible%20Providers-Blog-blue)](https://blog.baicaizhale.top/post/openai-compatible-providers)

### Usage

- Enter `/cli` or `/fancy` in-game to start AI chat mode.
- Simply type your request, e.g., "Generate a 10x10 stone platform at my current location."
- The AI generates the command; confirm it to execute.

**Common Interactions:**

| Input | Effect |
|------|------|
| `exit` | Exit CLI mode |
| `stop` | Interrupt the AI or cancel current operation |
| `y` / `n` | Confirm / Cancel execution |
| `agree` | Agree to terms or enable YOLO mode |
| `/cli retry` | Retry the previous AI response |
| `/cli exempt_anti_loop` | Temporarily disable anti-loop detection |
| `!message` | Start with `!` to send normal chat messages, bypassing AI |

## Commands & Permissions

| Command | Description | Default Permission |
| :--- | :--- | :--- |
| `/fancyhelper` | Main command (Aliases: `/cli`, `/fancy`) | `fancyhelper.cli` |
| `/fancyhelper reload` | Reload plugin configuration | `fancyhelper.reload` |
| `/fancyhelper lib install protocollib` | Download and install ProtocolLib dependency | OP |

| Permission | Description | Default |
| :--- | :--- | :--- |
| `fancyhelper.cli` | Allows usage of CLI mode | OP |
| `fancyhelper.reload` | Allows reloading configuration | OP |
| `fancyhelper.notice` | Allows viewing plugin announcements | OP |
| `fancyhelper.skill.use` | Allows using skill commands | OP |
| `fancyhelper.skill.admin` | Allows managing skills | OP |

## FAQ

**Seeing `[WARN]: Failed to update secure chat state for <player>: 'Chat disabled due to missing profile public key. Please try reconnecting.` in logs?**

This is caused by Minecraft's `enforce-secure-profile` security setting, not the plugin itself.
FancyHelper will automatically attempt to set this to `false` in `server.properties`. Restart the server after the change. If it fails, edit manually and restart.

## Contributing

- [Report a Bug](https://github.com/baicaizhale/FancyHelper/issues/new?template=错误报告.md&labels=bug)
- [Request a Feature](https://github.com/baicaizhale/FancyHelper/issues/new?template=功能请求.md&labels=enhancement)
- [Ask a Question](https://github.com/baicaizhale/FancyHelper/issues/new?template=询问问题.md&labels=question)
- Pull requests are welcome! Build from source using the instructions below and submit a PR.
- Check [AGENT.md](AGENT.md) for project structure and development guide.

## Support

- [GitHub Issues](https://github.com/baicaizhale/FancyHelper/issues)

## Build

```bash
git clone https://github.com/baicaizhale/FancyHelper.git
cd FancyHelper
mvn clean package
```

Requires Java 17 + Maven.

## Sponsor Us

To create FancyHelper, we have burned the midnight oil and poured our hearts into it. If this tool helps you, would you consider inviting us for a drink or a cup of tea?

**baicaizhale**![baicaizhale](./pic/Sponsor-baicaizhale.webp)

**zip8919**![zip8919](./pic/Sponsor-zip8919.webp)

## Star History

<a href="https://www.star-history.com/?repos=baicaizhale%2FFancyHelper&type=date&logscale=&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=baicaizhale/FancyHelper&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=baicaizhale/FancyHelper&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=baicaizhale/FancyHelper&type=date&legend=top-left" />
 </picture>
</a>

---

**© 2026 baicaizhale. Licensed under the GNU General Public License v3.0.**
