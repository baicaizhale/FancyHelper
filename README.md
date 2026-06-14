# FancyHelper

<img src="pic/image.webp" alt="FancyHelper Logo" width="50%">

> 用说人话的方式管理你的 Minecraft 服务器。

[![文言](https://img.shields.io/badge/-文言(华夏)-D54B4B?style=flat)](README_LZH.md)
[![English](https://img.shields.io/badge/-English(US)-31C654?style=flat)](README_EN.md)

[![CI](https://github.com/baicaizhale/FancyHelper/actions/workflows/CI-Build-Release.yml/badge.svg)](https://github.com/baicaizhale/FancyHelper/actions)
[![License](https://img.shields.io/github/license/baicaizhale/FancyHelper?color=blue)](LICENSE)
[![Stars](https://img.shields.io/github/stars/baicaizhale/FancyHelper?color=yellow&logo=github)](https://github.com/baicaizhale/FancyHelper/stargazers)
[![Issues](https://img.shields.io/github/issues/baicaizhale/FancyHelper?color=red)](https://github.com/baicaizhale/FancyHelper/issues)
[![Download](https://img.shields.io/badge/download-builds-orange?logo=github)](https://fancy.baicaizhale.top/)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/baicaizhale/Fancyhelper)

---

每次想给玩家调个权限、改个配置都要翻半天 Wiki？背指令背到头疼？

FancyHelper 就是来解决这个问题的。装好之后，你在游戏里直接跟 AI 说需求，比如"把 baicaizhale 设成管理员"，它会自己生成对应的指令，问你确认，然后执行。不用背指令，不用翻文档。

## 功能

- **聊天式管理** — 输入 `/cli` 进入对话模式，像跟真人管理员聊天一样管服务器
- **AI 自动生成指令** — 接入 CloudFlare Workers AI（默认 `gpt-oss-120b`），你说需求它出指令
- **多 AI 可选** — 除了 CloudFlare，也支持 OpenAI、DeepSeek、Azure OpenAI、Ollama 本地模型等
- **执行前确认** — AI 生成的指令默认需要手动确认（`y`/`n`）才会执行，不会乱来
- **YOLO 模式** — 嫌确认麻烦？同意协议后大部分指令自动执行，但 `op`、`ban`、`stop` 这种高危操作仍会问你
- **实时状态条** — 动作栏显示 AI 当前在干嘛（思考中 / 执行中 / 等你确认）
- **内置 Wiki 搜索** — 自带 LuckPerms、EssentialsX、WorldEdit 等主流插件的文档预设，搜不到还能自动全网搜
- **执行反馈闭环** — 指令跑完的结果会告诉 AI，错了它能自己改
- **配置文件自动更新** — 升级插件不用手动改配置，它会自己处理
- **防死循环** — AI 如果开始重复操作或疯狂调用，会自动拦截

## 兼容性

| 服务端 | 版本 | Java |
|--------|------|------|
| Spigot | 1.18+ | 17+ |
| Paper | 1.18+ | 17+ |

**依赖：**
- [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) 5.4.0+ — 用于捕获命令输出和拦截系统消息，不装也能跑但功能不完整。
  你可以使用 `/fancy lib install protocollib` 命令自动下载安装（需要 OP 权限）。

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

1. 下载 `FancyHelper.jar`，丢进服务器的 `plugins` 文件夹
2. 同时装上依赖 ProtocolLib（注意对应你服务器的版本）
3. 重启服务器

### 配置 AI（可选，默认用 CloudFlare）

**CloudFlare Workers AI：**
教程详见 [![blog](https://img.shields.io/badge/兼容端点配置指南-Blog-blue)](https://blog.baicaizhale.top/post/create-cf-key-for-fhai)

**OpenAI 兼容 API：**
教程详见 [![blog](https://img.shields.io/badge/创建Cloudflare的AI访问密钥-Blog-blue)](https://blog.baicaizhale.top/post/openai-compatible-providers)


### 开用

- 游戏里输 `/cli` 或 `/fancy` 进入 AI 对话模式
- 直接打字说需求，比如"在当前位置生成一个 10x10 的石头平台"
- AI 会生成指令，你看没问题就确认执行

**常用交互：**

| 输入 | 作用 |
|------|------|
| `exit` | 退出 CLI 模式 |
| `stop` | 打断 AI 或取消当前操作 |
| `y` / `n` | 确认 / 取消执行 |
| `agree` | 同意用户协议，或开启 YOLO 模式 |
| `/cli retry` | 重试上一次的 AI 响应 |
| `/cli exempt_anti_loop` | 临时关闭防死循环检测 |
| `!消息` | 以 `!` 开头直接发聊天消息，不走 AI |

## 指令与权限

| 指令 | 描述 | 默认权限 |
| :--- | :--- | :--- |
| `/fancyhelper` | 插件主指令（别名：`/cli`, `/fancy`） | `fancyhelper.cli` |
| `/fancyhelper reload` | 重载插件配置 | `fancyhelper.reload` |
| `/fancyhelper lib install protocollib` | 下载并安装 ProtocolLib 依赖 | OP |

| 权限 | 描述 | 默认 |
| :--- | :--- | :--- |
| `fancyhelper.cli` | 允许使用 CLI 模式 | OP |
| `fancyhelper.reload` | 允许重载配置 | OP |
| `fancyhelper.notice` | 允许查看插件公告 | OP |
| `fancyhelper.skill.use` | 允许使用技能命令 | OP |
| `fancyhelper.skill.admin` | 允许管理技能 | OP |

## 常见问题

**日志里刷 `[WARN]: Failed to update secure chat state for <player>: 'Chat disabled due to missing profile public key. Please try reconnecting.` 警告？**

这是 Minecraft 的 `enforce-secure-profile` 安全聊天验证导致的，不是本插件的锅。
FancyHelper 会自动尝试把 `server.properties` 里的这个选项改成 `false`，改完重启就好。如果自动改失败了，手动改一下再重启。

## 贡献

- [报告 Bug](https://github.com/baicaizhale/FancyHelper/issues/new?template=错误报告.md&labels=bug)
- [请求功能](https://github.com/baicaizhale/FancyHelper/issues/new?template=功能请求.md&labels=enhancement)
- [提问](https://github.com/baicaizhale/FancyHelper/issues/new?template=询问问题.md&labels=question)
- PR 欢迎！从下方构建说明编译后提交即可。
- 查看 [AGENT.md](AGENT.md) 了解项目结构和开发指引。

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

**© 2026 baicaizhale. Licensed under GNU General Public License v3.0.**
