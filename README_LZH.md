# FancyHelper

<img src="pic/image.webp" alt="FancyHelper Logo" width="50%">

> 以平易之言，御方块之境。

凡欲更易权限、修撰配置，必翻阅维基，苦索指令，常感头痛不已。

“幻思良辅”由此而生。既置此策，君于境中直抒所求即可。如言"把 baicaizhale 设成管理员"，灵枢自当草拟指令，待君亲阅，允准后旋即施行。无需苦记指令，无需遍考典籍。

---

## 功能

- **对谈理政** — 输入 `/cli` 启对谈之门，如晤同僚，片言只语即可理政。
- **灵枢拟旨** — 默认衔接 CloudFlare Workers AI（默认 `gpt-oss-120b`），君命如火，其拟旨如风。
- **多枢可选** — 除 CloudFlare 外，亦承 OpenAI、DeepSeek、Azure OpenAI、Ollama 本地之法。
- **慎行确认** — 凡所拟指令，必待君亲览确认（`y`/`n`）方可行之，以防纷乱。
- **放手模式** — 若嫌复核琐碎，立约之后，寻常细事自当躬行。然 `op`、`ban`、`stop` 等要务，仍必请旨。
- **流光动态** — 动作栏实时显现灵枢之态（思索中 / 施行中 / 候命中）。
- **博学维基** — 内置 LuckPerms、EssentialsX、WorldEdit 等主流插件之典籍。若典籍无载，亦可全网搜寻。
- **反馈回环** — 指令既行，成败反馈灵枢。若有谬误，灵枢自能补正。
- **配置自新** — 升级插件无需手动改易配置，灵枢自会处理。
- **防死循环** — 若灵枢陷入重操作或狂调用，自当拦截。

## 📷 图片展示

<details>
<summary>点击展开/收起插件预览图</summary>

<img src="./pic/1.webp" alt="FancyHelper预览图1" width="200"/>
<img src="./pic/2.webp" alt="FancyHelper预览图2" width="200"/>
<img src="./pic/3.webp" alt="FancyHelper预览图3" width="200"/>
<img src="./pic/4.webp" alt="FancyHelper预览图4" width="200"/>
<img src="./pic/5.webp" alt="FancyHelper预览图5" width="200"/>
<img src="./pic/6.webp" alt="FancyHelper预览图6" width="200"/>

</details>

## 兼容性

| 服务端 | 版本 | Java |
|--------|------|------|
| Spigot | 1.18+ | 17+ |
| Paper | 1.18+ | 17+ |

**依赖：**
- [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) 5.4.0+（强烈建议装，用以捕捉指令输出）

## 快速开始

### 下载插件
<a href="[https://fancy.baicaizhale.top/](https://fancy.baicaizhale.top/)">
  <img src="[https://img.shields.io/badge/](https://img.shields.io/badge/)🌕_查看构建列表-Enter-orange?style=for-the-badge&labelColor=555" alt="下载构建" style="vertical-align: middle;"/>
</a>

> 💡 建议下载最新版本，包含最新功能与修复。

### 安装

1. 下载最新之 `FancyHelper.jar`，置于服务器 `plugins` 文件夹。
2. 同时装置外援 ProtocolLib（26.1 以上版本请使用 dev 构建）。
3. 重启服务器，配置文案自当生成。

### 配置 AI（可选，默认用 CloudFlare）

**CloudFlare Workers AI（默认）：**
往 CloudFlare 控制台或 Key 共享站取得 API Key，随后更易 `plugins/FancyHelper/config.yml`：

```yaml
cloudflare:
  cf_key: 你的_CLOUDFLARE_API_KEY
  model: "@cf/openai/gpt-oss-120b"
```

更易后于境中输入 `/fancyhelper reload` 重载。

**OpenAI 兼容 API：**
亦承 OpenAI 官方、DeepSeek、Ollama 本地模型、Azure OpenAI 等。于 `config.yml` 之中：

```yaml
openai:
  enabled: true
  api_url: "https://api.openai.com/v1/chat/completions"
  api_key: "your-openai-api-key"
  model: "gpt-4o"
```

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
| `/cli retry` | 重试上回之响应 |
| `/cli exempt_anti_loop` | 暂关死循环检测 |
| `!消息` | 以 `!` 为始直发闲谈，不劳灵枢 |


## 常见问题

**日志里刷 `[WARN]: Failed to update secure chat state for <player>: 'Chat disabled due to missing profile public key. Please try reconnecting.` 警告？**

此乃 Minecraft `enforce-secure-profile` 安全聊天验证所致，非本插件之过。
FancyHelper 会自动尝试将 `server.properties` 中此项改为 `false`，改后重启即可。若自更失败，请手动改之。

**为什么要装 [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)？**

用以捕捉指令输出及拦截系统讯息，不装置亦可运行，然功能不全。
君可使用 `/fancy lib install protocollib` 指令自动下载装置（需 OP 权限）。

## 指令与权限

| 指令 | 描述 | 默认权限 |
| :--- | :--- | :--- |
| `/fancyhelper` | 插件主指令 (别名: `/cli`, `/fancy`) | `fancyhelper.cli` |
| `/fancyhelper reload` | 重载插件配置 | `fancyhelper.reload` |
| `/fancyhelper lib install protocollib` | 下载并安装 ProtocolLib 依赖 | OP |

| 权限 | 描述 | 默认 |
| :--- | :--- | :--- |
| `fancyhelper.cli` | 允许使用 CLI 模式 | OP |
| `fancyhelper.reload` | 允许重载配置 | OP |
| `fancyhelper.notice` | 允许查看插件公告 | OP |

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

---

**© 2026 baicaizhale 保留所有权利。**
