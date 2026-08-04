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
- **AI Command Generation** — Defaults to FancyConsole (keyless AI relay). Also supports OpenAI-compatible endpoints such as OpenAI, DeepSeek, and CloudFlare Workers AI (BYOK).
- **Four Conversation Modes** — **Normal**: every command needs confirmation. **YOLO**: after agreeing to the terms, most commands run automatically, though high-risk ones like `op`, `ban`, and `stop` still ask. **SMART**: the AI judges risk and executes low-risk commands directly. **Plan**: plan first, then execute — great for complex tasks.
- **Pre-execution Confirmation** — In Normal mode, AI-generated commands require manual confirmation (`y`/`n`) to prevent accidents.
- **Real-time Status Bar** — The Action Bar displays what the AI is doing (Thinking / Executing / Waiting for confirmation), with streaming output word by word.
- **Built-in Wiki Search** — Comes with documentation presets (Skills) for LuckPerms, EssentialsX, WorldEdit and other major plugins. Falls back to web search (Tavily / Metaso) if nothing is found.
- **Skill System** — Inject markdown knowledge files into the AI for specific plugins. Online market for one-click install/update.
- **Feedback Loop** — The output of executed commands is fed back to the AI. If something fails, the AI can correct itself.
- **File Tools** — Let the AI read and edit server config files (`#read` / `#edit` / `#write`). First-time use requires verification.
- **MCP Client** — Connect to external MCP tool servers, letting the AI call any external tool.
- **Todo List** — The AI maintains a task list via `#todo`, viewable in-game as a book.
- **Preference Memory** — The AI remembers each player's long-term preferences (`#remember` / `#forget`).
- **Session Resume** — Conversation history is persisted. Use `/cli resume` to pick up where you left off.
- **Auto Updates** — Detects new versions, downloads and hot-reloads automatically — no server restart needed.
- **Multi-language** — Plugin UI supports Simplified Chinese / English / Classical Chinese. Switch in `config.yml`.
- **Anti-Loop Protection** — Automatically intercepts the AI if it starts repeating operations or making excessive calls.

## Compatibility

| Server | Version | Java |
|--------|------|------|
| Spigot | 1.18+ | 17+ |
| Paper (recommended) | 1.18+ | 17+ |

> Paper and its forks (Purpur, Pufferfish, etc.) are recommended. Spigot works, but the plugin warns at startup because some advanced features rely on Paper-specific APIs.

**Dependencies:**
- [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) 5.4.0+ — **Required**, used to capture command output and intercept system messages. The plugin disables itself without it.
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
2. Install the required dependency ProtocolLib (make sure to match the version to your server).
3. Restart the server; configuration files will be generated automatically.

### Configure AI

The plugin defaults to **FancyConsole** (a keyless AI relay service). It works out of the box, but you need to register and bind once:

1. Type `/cli` in-game and click the registration link, or visit `https://console.fancy.baicaizhale.top/register?server=<your-server-id>`.
2. After registering, get your API Key and bind it in-game: `/cli bind <API Key>`.
3. Once bound, you can start chatting.

**Bring Your Own Key (BYOK):** Set `provider.ai` to `openai` or `cloudflare` in `config.yml`, then fill in the corresponding `api_key` / `cf_key`.

**CloudFlare Workers AI:**
Tutorial: [![blog](https://img.shields.io/badge/CF%20Key%20Setup%20Guide-Blog-blue)](https://blog.baicaizhale.top/post/create-cf-key-for-fhai)

**OpenAI Compatible API (DeepSeek, OpenAI, etc.):**
Tutorial: [![blog](https://img.shields.io/badge/OpenAI%20Compatible%20Providers-Blog-blue)](https://blog.baicaizhale.top/post/openai-compatible-providers)

**MCP Tool Servers:**
Tutorial: [![blog](https://img.shields.io/badge/MCP%20Setup%20Guide-Blog-blue)](https://blog.baicaizhale.top/post/mcp-config)

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
| `/cli yolo` | Switch to YOLO mode (auto-execute) |
| `/cli smart` | Switch to SMART mode (AI judges risk) |
| `/cli plan` | Enter Plan mode |
| `/cli retry` | Retry the previous AI response |
| `/cli exempt_anti_loop` | Temporarily disable anti-loop detection |
| `/cli resume` | Resume a previous session |
| `/cli skill list` | List installed Skills |
| `/cli todo` | Open the todo list book |
| `!message` | Start with `!` to send normal chat messages, bypassing AI |

## Commands & Permissions

| Command | Description | Default Permission |
| :--- | :--- | :--- |
| `/fancyhelper` | Main command (Aliases: `/cli`, `/fancy`) | `fancyhelper.cli` |
| `/fancyhelper bind <key>` | Bind a FancyConsole API Key | OP |
| `/fancyhelper reload [target]` | Reload plugin config (`config` / `workspace` / `playerdata` / `skill` / `mcp` / `deeply`) | `fancyhelper.reload` |
| `/fancyhelper status` | Show plugin status | `fancyhelper.cli` |
| `/fancyhelper yolo` / `smart` / `normal` / `plan` | Switch conversation mode | `fancyhelper.cli` |
| `/fancyhelper settings` | Open personal settings (streaming, display position, sound, etc.) | `fancyhelper.cli` |
| `/fancyhelper tools` | Manage file tool permissions (read / write) | `fancyhelper.cli` |
| `/fancyhelper memory` | Manage AI preference memory | `fancyhelper.cli` |
| `/fancyhelper resume` | Resume a previous session | `fancyhelper.cli` |
| `/fancyhelper todo` | Open the todo list | `fancyhelper.cli` |
| `/fancyhelper skill <list\|info\|load>` | Skill list / details / load | `fancyhelper.skill.use` |
| `/fancyhelper skill <reload\|install\|upgrade>` | Manage Skills (reload / install / update) | `fancyhelper.skill.admin` |
| `/fancyhelper mcp tools` | View MCP external tools | `fancyhelper.cli` |
| `/fancyhelper checkupdate` | Check for plugin updates | `fancyhelper.cli` |
| `/fancyhelper upgrade` | Download and install the new version | `fancyhelper.reload` |
| `/fancyhelper notice` | View plugin announcements | `fancyhelper.notice` |
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

**Can't find ProtocolLib / plugin fails to load?**

ProtocolLib is now a **required dependency** of FancyHelper. Download the version matching your server from [SpigotMC](https://www.spigotmc.org/resources/protocollib.1997/), put it in the `plugins` folder, and restart. You can also run `/fancy lib install protocollib` as OP to install it automatically.

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
