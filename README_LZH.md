# FancyHelper

<img src="pic/image.webp" alt="FancyHelper Logo" width="50%">

> 以平易之言，御方块之境。

[![中文](https://img.shields.io/badge/-中文(简体)-3178C6?style=flat)](README.md)
[![English](https://img.shields.io/badge/-English(US)-31C654?style=flat)](README_EN.md)

[![CI](https://github.com/baicaizhale/FancyHelper/actions/workflows/CI-Build-Release.yml/badge.svg)](https://github.com/baicaizhale/FancyHelper/actions)
[![License](https://img.shields.io/github/license/baicaizhale/FancyHelper?color=blue)](LICENSE)
[![Stars](https://img.shields.io/github/stars/baicaizhale/FancyHelper?color=yellow&logo=github)](https://github.com/baicaizhale/FancyHelper/stargazers)
[![Issues](https://img.shields.io/github/issues/baicaizhale/FancyHelper?color=red)](https://github.com/baicaizhale/FancyHelper/issues)
[![Download](https://img.shields.io/badge/download-builds-orange?logo=github)](https://fancy.baicaizhale.top/)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/baicaizhale/Fancyhelper)

---

凡欲更易权限、修撰配置，必翻阅维基，苦索指令，常感头痛不已。

"幻思良辅"由此而生。既置此策，君于境中直抒所求即可。如言"把 baicaizhale 设成管理员"，灵枢自当草拟指令，待君亲阅，允准后旋即施行。无需苦记指令，无需遍考典籍。

## 功能

- **对谈理政** — 输入 `/cli` 启对谈之门，如晤同僚，片言只语即可理政。
- **灵枢拟旨** — 默认走 FancyConsole（免钥中转），亦承 OpenAI、DeepSeek、CloudFlare Workers AI 等 OpenAI 兼容端点（自带密钥）。
- **四境理政** — **常境**：凡令必待君亲允；**放手境**：立约之后，寻常细事自当躬行，然 `op`、`ban`、`stop` 等要务仍必请旨；**明智境**：灵枢自度风险，卑险者径行；**图谋境**：先谋划后施行，宜乎繁难之事。
- **慎行确认** — 常境之下，凡所拟指令，必待君亲览确认（`y`/`n`）方可行之，以防纷乱。
- **流光动态** — 动作栏实时显现灵枢之态（思索中 / 施行中 / 候命中），流式逐字而现。
- **博学维基** — 内置 LuckPerms、EssentialsX、WorldEdit 等主流插件之典籍（Skills）。若典籍无载，亦可全网搜寻（Tavily / Metaso）。
- **博艺之籍** — 以 markdown 文档为灵枢注入某插件之用典，更有线上市集，一键装置更新。
- **反馈回环** — 指令既行，成败反馈灵枢。若有谬误，灵枢自能补正。
- **文牍之器** — 使灵枢读写服务器配置文件（`#read` / `#edit` / `#write`），初用须验明正身。
- **MCP 外援** — 连接外部 MCP 工具服务器，灵枢可调用任意外部工具，扩展无穷。
- **待办清册** — 灵枢以 `#todo` 维护事务之册，书中实时可览其进度。
- **偏好之忆** — 灵枢默记每位玩家之长期偏好（`#remember` / `#forget`）。
- **续谈旧事** — 对谈之录持久存焉，`/cli resume` 可随时续前缘。
- **自更自动** — 察觉新版，自当下载装置而热启，无需重启服务器。
- **多语并行** — 界面兼通简体中文 / English / 文言文，`config.yml` 一键可易。
- **防死循环** — 若灵枢陷入重操作或狂调用，自当拦截。

## 兼容性

| 服务端 | 版本 | Java |
|--------|------|------|
| Spigot | 1.18+ | 17+ |
| Paper（荐） | 1.18+ | 17+ |

> 建议使用 Paper 及其下游分支（Purpur、Pufferfish 等）。Spigot 亦可运行，然启时必有警语，因部分高阶功能赖 Paper 之 API。

**依赖：**
- [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) 5.4.0+ — **必需**，用以捕捉指令输出及拦截系统讯息，无之则插件自止。
  君可使用 `/fancy lib install protocollib` 指令自动下载装置（需 OP 权限）。

## 演示

<video src="./pic/demo.mp4" controls width="100%" muted></video>

<details>
<summary>点击展开/收起插件预览图</summary>

<img src="./pic/1.webp" alt="FancyHelper预览图1" width="200"/>
<img src="./pic/2.webp" alt="FancyHelper预览图2" width="200"/>
<img src="./pic/3.webp" alt="FancyHelper预览图3" width="200"/>
<img src="./pic/4.webp" alt="FancyHelper预览图4" width="200"/>
<img src="./pic/5.webp" alt="FancyHelper预览图5" width="200"/>
<img src="./pic/6.webp" alt="FancyHelper预览图6" width="200"/>

</details>

## 快速开始

### 安装

1. 下载 `FancyHelper.jar`，置于服务器 `plugins` 文件夹。
2. 同时装置必需之外援 ProtocolLib（注意对应你服务器的版本）。
3. 重启服务器，配置文案自当生成。

### 配置 AI

插件默认走 **FancyConsole**（免钥之 AI 中转服务），开箱即用，然须注册绑定一次：

1. 境中输入 `/cli`，点击消息中之链接前往注册页，或访问 `https://console.fancy.baicaizhale.top/register?server=<君之服务器ID>`。
2. 注册既成，得 API Key，境中输入 `/cli bind <API Key>`。
3. 绑定成功，即可对谈。

**欲用己钥（BYOK）：** 将 `config.yml` 之 `provider.ai` 改为 `openai` 或 `cloudflare`，填妥相应之 `api_key` / `cf_key`。

**CloudFlare Workers AI：**
教程详见 [![blog](https://img.shields.io/badge/兼容端点配置指南-Blog-blue)](https://blog.baicaizhale.top/post/create-cf-key-for-fhai)

**OpenAI 兼容 API（DeepSeek、OpenAI 等）：**
教程详见 [![blog](https://img.shields.io/badge/创建Cloudflare的AI访问密钥-Blog-blue)](https://blog.baicaizhale.top/post/openai-compatible-providers)

**MCP 工具服务器：**
教程详见 [![blog](https://img.shields.io/badge/MCP%20配置指南-Blog-blue)](https://blog.baicaizhale.top/post/mcp-config)

### 开用

- 境中输入 `/cli` 或 `/fancy` 开启灵枢对谈模式。
- 直抒所求，如"在当前位置生成一个 10x10 的石头平台"。
- 灵枢拟旨，君视之无误则准其施行。

**常用交互：**

| 输入 | 作用 |
|------|------|
| `exit` | 退出 CLI 模式 |
| `stop` | 打断灵枢或取消当前操作 |
| `y` / `n` | 准奏 / 驳回 |
| `agree` | 签契或开启放手模式 |
| `/cli yolo` | 切至放手境（自动施行） |
| `/cli smart` | 切至明智境（灵枢度险） |
| `/cli plan` | 进入图谋境 |
| `/cli retry` | 重试上回之响应 |
| `/cli exempt_anti_loop` | 暂关死循环检测 |
| `/cli resume` | 续上回之会话 |
| `/cli skill list` | 览已装之 Skill |
| `/cli todo` | 启待办清册之书 |
| `!消息` | 以 `!` 为始直发闲谈，不劳灵枢 |

## 指令与权限

| 指令 | 描述 | 默认权限 |
| :--- | :--- | :--- |
| `/fancyhelper` | 插件主指令（别名：`/cli`, `/fancy`） | `fancyhelper.cli` |
| `/fancyhelper bind <key>` | 绑定 FancyConsole API Key | OP |
| `/fancyhelper reload [target]` | 重载插件配置（可指定 `config` / `workspace` / `playerdata` / `skill` / `mcp` / `deeply`） | `fancyhelper.reload` |
| `/fancyhelper status` | 查看插件状态 | `fancyhelper.cli` |
| `/fancyhelper yolo` / `smart` / `normal` / `plan` | 切换对话模式 | `fancyhelper.cli` |
| `/fancyhelper settings` | 打开个人设置（流式输出、显示位置、声音等） | `fancyhelper.cli` |
| `/fancyhelper tools` | 管理文件工具权限（read / write） | `fancyhelper.cli` |
| `/fancyhelper memory` | 管理灵枢偏好记忆 | `fancyhelper.cli` |
| `/fancyhelper resume` | 恢复历史会话 | `fancyhelper.cli` |
| `/fancyhelper todo` | 打开待办清册 | `fancyhelper.cli` |
| `/fancyhelper skill <list\|info\|load>` | Skill 之目 / 详 / 载 | `fancyhelper.skill.use` |
| `/fancyhelper skill <reload\|install\|upgrade>` | 管理 Skill（重载 / 装置 / 更新） | `fancyhelper.skill.admin` |
| `/fancyhelper mcp tools` | 览 MCP 外部工具 | `fancyhelper.cli` |
| `/fancyhelper checkupdate` | 查插件更新 | `fancyhelper.cli` |
| `/fancyhelper upgrade` | 下载并装置新版 | `fancyhelper.reload` |
| `/fancyhelper notice` | 查看插件公告 | `fancyhelper.notice` |
| `/fancyhelper lib install protocollib` | 下载并装置 ProtocolLib 依赖 | OP |

| 权限 | 描述 | 默认 |
| :--- | :--- | :--- |
| `fancyhelper.cli` | 允许使用 CLI 模式 | OP |
| `fancyhelper.reload` | 允许重载配置 | OP |
| `fancyhelper.notice` | 允许查看插件公告 | OP |
| `fancyhelper.skill.use` | 允许使用技能命令 | OP |
| `fancyhelper.skill.admin` | 允许管理技能 | OP |

## 常见问题

**日志里刷 `[WARN]: Failed to update secure chat state for <player>: 'Chat disabled due to missing profile public key. Please try reconnecting.` 警告？**

此乃 Minecraft `enforce-secure-profile` 安全聊天验证所致，非本插件之过。
FancyHelper 会自动尝试将 `server.properties` 中此项改为 `false`，改后重启即可。若自更失败，请手动改之。

**报错说找不到 ProtocolLib / 插件加载失败？**

ProtocolLib 今为 FancyHelper 之**必需依赖**。请自 [SpigotMC](https://www.spigotmc.org/resources/protocollib.1997/) 下载对应君之服务端版本者，置于 `plugins` 文件夹后重启。亦可以 OP 权限执行 `/fancy lib install protocollib` 自动装置。

## 贡献

- [呈报谬误](https://github.com/baicaizhale/FancyHelper/issues/new?template=错误报告.md&labels=bug)
- [请增功能](https://github.com/baicaizhale/FancyHelper/issues/new?template=功能请求.md&labels=enhancement)
- [垂询](https://github.com/baicaizhale/FancyHelper/issues/new?template=询问问题.md&labels=question)
- PR 亦所欢迎。依下方构建之法编译后提交即可。
- 详见 [AGENT.md](AGENT.md) 以知项目结构及开发指引。

## 支持

- [GitHub Issues](https://github.com/baicaizhale/FancyHelper/issues)

## 构建

```bash
git clone https://github.com/baicaizhale/FancyHelper.git
cd FancyHelper
mvn clean package
```

需要 Java 17 + Maven。

## 赞助我们

为铸 FancyHelper，吾等焚膏继晷，兀兀穷年，耗尽心血。若此物有幸助君一臂之力，不知可否邀君共饮一杯薄酒，或赐一盏清茶之资？

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

**© 2026 baicaizhale。遵 GNU GPL v3.0 通用公钥许可证发布。**
