# FancyHelper 项目说明

## 项目概述

FancyHelper 是一款基于 AI 驱动的 Minecraft 服务器管理助手插件。它允许管理员通过自然语言与 AI 对话，自动生成并执行复杂的服务器指令，极大降低了插件学习成本和管理负担。

### 核心特性

- **CLI 交互模式**: 通过游戏内聊天框与 AI 进行沉浸式对话
- **多 AI 提供商支持**: 支持 CloudFlare Workers AI 和 OpenAI 兼容 API（OpenAI 官方、Azure OpenAI、DeepSeek、Ollama、阿里云通义千问等）
- **AI 驱动**: 接入 CloudFlare Workers AI，默认使用 `gpt-oss-120b` 模型，支持推理模型参数
- **安全执行**: AI 生成的指令需要玩家手动确认后才会执行（YOLO 模式除外）
- **YOLO 风险保护**: YOLO 模式下对高风险命令仍要求手动确认（如 op、ban、stop 等）
- **智能文档搜索**: 内置多种主流插件预设（LuckPerms, EssentialsX, WorldEdit 等）
- **结果反馈闭环**: 指令执行结果会自动反馈给 AI，支持错误修正
- **配置自愈**: 支持配置文件版本检测与自动更新，保留用户自定义配置
- **旧插件清理**: 自动清理旧版本的 MineAgent 插件文件，防止干扰
- **自动修复安全配置**: 自动检测并修复 `enforce-secure-profile` 配置问题
- **状态可视化**: 实时显示 AI 生成状态（思考中、执行工具、等待确认等）
- **动作栏反馈**: 使用动作栏显示实时状态，不影响聊天体验（可切换至副标题显示）
- **精确 Token 计算**: 使用 jtokkit 实现精确的 token 消耗计算
- **防死循环检测**: 自动检测 AI 陷入重复操作或过度调用，支持豁免机制
- **NBT/组件格式指南**: 内置 NBT 与数据组件格式转换指南，适配 1.20.5+ 版本
- **调试模式**: 支持调试模式，启用后在控制台输出详细调试信息
- **自动状态清理**: 玩家退出时自动清理 CLI 会话状态，避免资源泄漏
- **公告系统**: 支持从远程获取并显示公告，可在控制台和玩家聊天中查看
- **总思考时间记录**: 在对话会话中记录并显示总思考时间
- **TODO 系统**: AI 可以创建和管理 TODO 任务列表，通过虚拟书本展示给玩家
- **文件监听**: 自动监控配置文件变动并实时重载，无需手动重载
- **补充系统提示词**: 支持在基础系统提示词后追加自定义提示，增强 AI 行为控制
- **深度重载**: 支持插件深度重载功能（在非 Paper 现代插件系统环境下）
- **状态显示切换**: 支持在动作栏和副标题之间切换状态显示位置

### 技术栈

- **语言**: Java 17+
- **服务器版本**: Spigot/Paper 1.18 - 1.21
- **构建工具**: Maven
- **核心依赖**:
  - Spigot API [1.18-R0.1, 1.21-R0.1]
  - ProtocolLib 5.4.0（可选依赖，推荐安装以获得完整功能）
  - Gson 2.10.1（JSON 处理）
  - jtokkit 0.6.0（Token 计算）
  - bstats-bukkit 3.1.0（统计）
  - Java 标准库 HttpClient（HTTP 客户端）

## 项目结构

```
src/main/java/org/YanPl/
├── FancyHelper.java              # 插件主类
├── api/
│   └── CloudFlareAI.java         # CloudFlare AI API 封装（支持 Completions 和 Responses API）
├── command/
│   └── CLICommand.java           # CLI 命令处理（含深度重载、设置管理、TODO 查看等）
├── listener/
│   └── ChatListener.java         # 聊天消息监听器（含玩家退出状态清理）
├── manager/
│   ├── CLIManager.java           # CLI 模式管理器（核心对话逻辑，含防循环检测、TODO 工具处理）
│   ├── ConfigManager.java        # 配置管理（含防循环配置读取、调试模式、补充提示词）
│   ├── EulaManager.java          # EULA 文件管理
│   ├── FileWatcherManager.java   # 文件监听管理器（自动监控配置文件变动并重载）
│   ├── NoticeManager.java        # 公告管理器（远程公告获取与显示）
│   ├── PacketCaptureManager.java # 数据包捕获管理器（ProtocolLib）
│   ├── PromptManager.java        # AI 提示词管理
│   ├── TodoManager.java          # TODO 管理器（管理玩家任务列表，支持虚拟书本展示）
│   ├── UpdateManager.java        # 插件更新管理
│   ├── VerificationManager.java  # 安全验证管理
│   └── WorkspaceIndexer.java     # 命令和预设索引
├── model/
│   ├── AIResponse.java           # AI 响应模型
│   ├── DialogueSession.java      # 对话会话模型（含工具调用历史、防循环豁免）
│   └── TodoItem.java             # TODO 任务项模型（支持状态、优先级、描述）
└── util/
    ├── CloudErrorReport.java     # 错误上报
    └── ResourceUtil.java         # 资源文件工具

src/main/resources/
├── config.yml                    # 插件配置文件
├── plugin.yml                    # 插件元数据
└── preset/                       # 插件预设文件目录
    ├── default.txt
    ├── essentialsx.txt
    ├── luckperms.txt
    ├── worldedit.txt
    ├── coreprotect.txt
    ├── mcmmo.txt
    ├── multiverse.txt
    ├── papi.txt
    ├── plugman.txt
    ├── protocollib.txt
    ├── residence.txt
    ├── vault.txt
    ├── 造房必读预设.txt          # 房屋建造预设（必读）
    ├── give.txt                  # give 命令使用指南
    └── nbt格式（用则必看）.txt    # NBT 与数据组件格式详细说明
```

## 构建和运行

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/baicaizhale/FancyHelper.git
cd FancyHelper

# 构建 JAR 文件
mvn clean package
```

构建完成后，JAR 文件位于 `target/FancyHelper.jar`。

### 安装和运行

1. 将 `FancyHelper.jar` 放入服务器的 `plugins` 文件夹
2. 推荐安装 ProtocolLib 插件以获得完整功能（动作栏状态显示、命令输出捕获等）
3. 重启服务器以生成默认配置文件
4. 插件会自动清理旧版本的 MineAgent 插件文件（如有），并将移动到 `plugins/FancyHelper/old/` 目录
5. 编辑 `plugins/FancyHelper/config.yml` 配置 CloudFlare API 密钥（插件会自动获取 account-id）
6. 在游戏中使用 `/fancyhelper` 或 `/cli` 进入对话模式

### 重新加载配置

```bash
/fancyhelper reload
```

## 开发约定

### 代码风格

- 使用 Java 17+ 特性
- 遵循 Spigot API 最佳实践
- 使用中文注释说明关键逻辑

### 异步处理

- AI API 调用和文件操作必须在异步线程中执行
- 使用 `Bukkit.getScheduler().runTaskAsynchronously()` 执行异步任务
- 使用 `Bukkit.getScheduler().runTask()` 回调到主线程处理 Bukkit API

### 资源管理

- HTTP 请求使用 Java 标准库 `HttpClient`（通过 `CloudFlareAI` 类）
- 插件禁用时必须正确关闭资源（见 `FancyHelper.onDisable()` 和 `CLIManager.shutdown()`）
- 使用 `CloudErrorReport` 统一上报错误

### 安全机制

- 所有命令执行前必须经过玩家确认（YOLO 模式除外）
- 文件操作工具（ls, read, diff）需要通过 `VerificationManager` 进行安全验证
- EULA 文件状态会定期检查，防止非法修改
- 防死循环检测机制防止 AI 陷入重复操作

### 日志规范

- 使用 `plugin.getLogger()` 记录日志
- 关键操作使用 `[CLI]`, `[AI]`, `[PacketCapture]` 等前缀标识模块
- 敏感信息（如 API 密钥）不应记录到日志中
- 调试日志仅在启用 `debug` 模式时输出

### 状态管理

- 使用 `CLIManager.GenerationStatus` 枚举管理 AI 生成状态
- 支持的状态包括：`THINKING`, `EXECUTING_TOOL`, `WAITING_CONFIRM`, `WAITING_CHOICE`, `COMPLETED`, `CANCELLED`, `ERROR`, `IDLE`
- 使用动作栏实时显示状态变化，通过 `PacketCaptureManager` 实现协议级拦截
- 玩家退出时自动清理 CLI 会话状态，避免资源泄漏

## 核心功能模块

### CLI 模式

玩家通过 `/cli` 命令进入对话模式，与 AI 交互管理服务器。支持的交互指令：

- `exit` - 退出 CLI 模式
- `stop` - 打断当前 AI 生成或取消待处理操作
- `y` / `n` - 确认或取消执行命令
- `agree` - 同意用户协议或 YOLO 协议
- `/cli exempt_anti_loop` - 豁免当前会话的防循环检测（通过聊天按钮触发）

**YOLO 模式说明**：
- YOLO（You Only Live Once）模式下，大部分 AI 生成的指令会自动执行，无需确认
- 但对于高风险命令（如 op、ban、stop、reload 等），仍需玩家手动确认
- 风险命令列表可在 `config.yml` 的 `settings.yolo_risk_commands` 中配置
- 默认包含：op、deop、stop、reload、restart、kill、nbt、ban、unban、plugman

**插件命令**：
- `/fancyhelper` 或 `/cli` 或 `/fancy` - 进入 CLI 对话模式
- `/fancyhelper reload` - 重新加载配置文件
- `/fancyhelper reload [workspace|config|playerdata|eula|deeply]` - 选择性重载或深度重载
- `/fancyhelper status` - 查看插件状态
- `/fancyhelper notice` - 查看插件公告（需要 `fancyhelper.notice` 权限）
- `/fancyhelper notice read` - 标记公告为已读
- `/fancyhelper checkupdate` - 检查插件更新
- `/fancyhelper upgrade` 或 `/fancyhelper download` - 下载并安装更新
- `/fancyhelper yolo` - 切换到 YOLO 模式
- `/fancyhelper normal` - 切换到正常模式
- `/fancyhelper confirm` - 确认执行命令
- `/fancyhelper cancel` - 取消执行命令
- `/fancyhelper agree` - 同意用户协议或 YOLO 协议
- `/fancyhelper read` - 打开 EULA 书本
- `/fancyhelper thought <文本>` - 发送思考内容给 AI
- `/fancyhelper settings` 或 `/fancyhelper set` - 打开设置菜单
- `/fancyhelper toggle <ls|read|diff>` - 切换文件工具状态
- `/fancyhelper display` - 切换状态显示位置（动作栏/副标题）
- `/fancyhelper select <选项>` - 选择 AI 提供的选项
- `/fancyhelper exempt_anti_loop` - 豁免当前会话的防循环检测
- `/fancyhelper retry` - 重试上一次操作
- `/fancyhelper todo` - 打开 TODO 列表书本

### 状态显示

CLI 模式支持实时状态可视化：

- **动作栏显示**: 使用动作栏显示当前 AI 状态，不影响聊天窗口
- **状态枚举**: 包括思考中、执行工具、等待确认、等待选择、已完成、已取消、错误、空闲
- **计时器**: 显示 AI 生成时间，支持超时警告

### AI 工具调用

AI 可以通过以下工具与服务器交互：

- `#run:<command>` - 执行服务器命令
- `#search:<query>` - 搜索插件 Wiki
- `#ls:<path>` - 列出目录内容
- `#read:<file>` - 读取文件内容
- `#diff:<file>|<old_content>|<new_content>` - 修改文件
- `#getpreset:<file>` - 获取插件预设内容
- `#choose:<options>` - 让玩家做出选择
- `#todo:<json>` - 创建或更新 TODO 列表（JSON 格式：`[{"id":"1","task":"描述","status":"pending"}]`）
- `#over` - 结束当前响应
- `#exit` - 退出 CLI 模式

### 防死循环检测

插件内置智能防循环机制，防止 AI 陷入重复操作：

- **连续相似调用检测**: 当检测到连续多次相似的工具调用时自动打断
- **工具链长度限制**: 单次对话中连续调用工具次数超过上限时自动暂停
- **豁免机制**: 玩家可点击聊天按钮豁免当前会话的防循环检测
- **可配置参数**: 通过 `config.yml` 中的 `anti_loop` 配置节调整检测阈值

### 预设文件

预设文件位于 `src/main/resources/preset/` 目录，用于向 AI 提供插件知识。每个预设文件包含对应插件的命令说明和最佳实践。

当前支持的预设：

- 默认预设（default.txt）
- EssentialsX（essentialsx.txt）
- LuckPerms（luckperms.txt）
- WorldEdit（worldedit.txt）
- CoreProtect（coreprotect.txt）
- mcMMO（mcmmo.txt）
- Multiverse（multiverse.txt）
- PlaceholderAPI（papi.txt）
- PlugMan（plugman.txt）
- ProtocolLib（protocollib.txt）
- Residence（residence.txt）
- Vault（vault.txt）
- 造房必读预设（造房必读预设.txt）- 房屋建造专用预设
- Give 命令指南（give.txt）
- NBT 格式指南（nbt格式（用则必看）.txt）

添加新预设：

1. 在 `preset/` 目录创建 `.txt` 文件（支持中文文件名）
2. 插件启动时会自动索引所有预设文件
3. AI 会根据玩家需求自动加载相关预设

### NBT 与数据组件格式

Minecraft 在 1.20.5 版本对物品数据进行了重大重构：

- **1.20.5+**: 使用 `[数据组件]` 格式（如 `stone[custom_name='{"text":"石头"}']`）
- **1.20.4-**: 使用 `{NBT}` 格式（如 `stone{display:{Name:'{"text":"石头"}'}}`）

插件内置详细的格式转换指南，详见 `nbt格式（用则必看）.txt` 预设文件。

### 命令输出捕获

`PacketCaptureManager` 使用 ProtocolLib 拦截发送给玩家的系统消息和数据包：

- 捕获命令执行结果反馈给 AI
- 支持动作栏消息拦截和显示
- 支持聊天消息捕获
- 确保命令执行结果的完整反馈闭环

### 公告系统

`NoticeManager` 负责从远程获取并显示公告：

- **远程获取**: 从指定 URL 异步获取公告数据
- **多渠道显示**: 支持在控制台和玩家聊天中显示公告
- **权限控制**: 只有拥有 `fancyhelper.notice` 权限的玩家才能查看公告
- **自动显示**: 玩家加入服务器时自动显示公告（可通过配置关闭）
- **公告格式**: 支持多行文本，正确处理换行符

**配置项**：
- `notice.show_on_join`: 玩家加入时是否显示插件公告（默认为 true）

### 调试模式

插件支持调试模式，启用后会在控制台输出详细调试信息：

- **配置项**: `config.yml` 中的 `settings.debug`
- **调试信息包括**:
  - AI 请求详细信息（System Prompt 长度、历史消息数等）
  - API 调用详细信息（URL、模型名称、响应状态码等）
  - 命令索引和预设索引信息
  - ProtocolLib 初始化状态
  - Paper 聊天事件监听器注册状态

### TODO 系统

`TodoManager` 负责管理玩家的 TODO 任务列表：

- **AI 创建任务**: AI 可以通过 `#todo` 工具创建或更新 TODO 列表
- **任务状态**: 支持四种状态 - PENDING（待办）、IN_PROGRESS（进行中）、COMPLETED（已完成）、CANCELLED（已取消）
- **优先级**: 支持设置任务优先级
- **虚拟书本展示**: 通过 `#todo` 命令可打开包含 TODO 列表的虚拟书本
- **聊天展示**: 在聊天栏显示 TODO 进度摘要
- **JSON 格式**: `#todo` 工具接受 JSON 格式输入：`[{"id":"1","task":"任务描述","status":"pending"}]`

**限制**:
- 同一时间只能有一个任务处于 IN_PROGRESS 状态
- 任务 ID 和描述不能为空

**命令**:
- `/fancyhelper todo` - 打开 TODO 列表书本

### 文件监听

`FileWatcherManager` 自动监控插件配置文件变动：

- **监控文件**:
  - `config.yml` - 配置文件
  - `playerdata.yml` - 玩家数据文件
  - `agreed_players.txt` - 同意协议的玩家列表
  - `yolo_agreed_players.txt` - 同意 YOLO 协议的玩家列表
  - `yolo_mode_players.txt` - 启用 YOLO 模式的玩家列表
- **自动重载**: 检测到文件变动时自动重新加载相应配置
- **防抖机制**: 3 秒防抖间隔，避免频繁重载

### 补充系统提示词

插件支持在基础系统提示词后追加自定义提示词：

- **配置项**: `config.yml` 中的 `settings.supplementary_prompt`
- **用途**: 增强或修改 AI 的行为模式
- **示例**: 可以添加特定的规则、风格要求或限制条件

### 深度重载

插件支持深度重载功能（在非 Paper 现代插件系统环境下）：

- **自动检测 PlugMan**: 如果检测到 PlugMan 插件，优先使用其进行重载
- **Paper 现代插件系统**: 在 Paper 现代插件系统下，深度重载会被阻止，需要使用服务器重启
- **重载流程**:
  1. 卸载插件命令
  2. 取消事件监听器
  3. 取消所有计划任务
  4. 禁用插件
  5. 从管理器中移除插件
  6. 关闭 ClassLoader
  7. 重新加载 JAR 文件
  8. 启用插件

**命令**:
- `/fancyhelper reload deeply` 或 `/fancyhelper reload deep` - 执行深度重载

### 状态显示切换

支持在动作栏和副标题之间切换状态显示位置：

- **动作栏（actionbar）**: 默认位置，显示在屏幕上方
- **副标题（subtitle）**: 显示在屏幕中央
- **个人设置**: 每个玩家可以独立设置自己的显示位置

**命令**:
- `/fancyhelper display` - 切换状态显示位置
- `/fancyhelper settings` - 打开设置菜单，包含显示位置切换选项

## 配置文件

主要配置项位于 `config.yml`：

```yaml
# 配置版本，请勿修改
version: 3.2.1

# CloudFlare Workers AI 配置
cloudflare:
  # CloudFlare Workers AI API 密钥，FancyHelper会自动根据密钥来获取对应的account-id
  cf_key: "你的_CLOUDFLARE_API_KEY"

  # 使用的模型名称，不推荐更改，可以是任何一个Text-Generation模型，默认gpt-oss-120b（最聪明）
  model: "@cf/openai/gpt-oss-120b"

# OpenAI 兼容 API 配置（支持自定义 OpenAI 接口）
openai:
  # 是否启用 OpenAI 模式（启用后将优先使用此配置）
  enabled: false

  # OpenAI API 地址（支持自定义地址，如 OpenAI 官方、Azure OpenAI、本地模型等）
  # 示例：
  #   - OpenAI 官方: https://api.openai.com/v1/chat/completions
  #   - DeepSeek: https://api.deepseek.com/chat/completions
  #   - Ollama (本地): http://localhost:11434/v1/chat/completions
  #   - 阿里云通义千问: https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
  #   - 其他兼容 OpenAI 格式的 API
  api_url: https://api.openai.com/v1/chat/completions

  # OpenAI API 密钥
  api_key: your-openai-api-key

  # 使用的模型名称
  # 示例：
  #   - OpenAI: gpt-4o, gpt-4o-mini, o1-preview, o1-mini
  #   - DeepSeek: deepseek-chat, deepseek-reasoner
  #   - Ollama: llama3, mistral
  #   - 阿里云通义千问: qwen-plus
  model: gpt-4o

# 插件设置
settings:
  # 是否启用调试模式，启用后会在控制台输出详细调试信息
  debug: false

  # 补充系统提示词（会自动追加到基础系统提示词后面）
  supplementary_prompt: ""

  # CLI 模式会话的自动过期时间（以分钟为单位）
  timeout_minutes: 10

  # AI API 请求超时时间（以秒为单位）
  # 对于推理模型（如 gpt-oss-120b、deepseek-reasoner、o1 等），建议设置为 120-300 秒
  # 对于普通模型（如 gpt-4o、gpt-4o-mini），建议设置为 60-120 秒
  api_timeout_seconds: 120

  # Token（字符数）剩余警告阈值
  token_warning_threshold: 100

  # 是否启用匿名错误上报，帮助开发者改进插件
  auto_report: true

  # 是否启用自动更新检查
  check_update: true

  # 是否在管理员进入服务器时提示更新
  op_update_notify: true

  # 是否在检测到新版本时自动下载并安装更新
  auto_upgrade: true

  # GitHub 镜像源，用于加速下载
  update_mirror: "https://ghproxy.net/"

  # 自动检测重复工具调用（防止 AI 陷入死循环）
  anti_loop:
    # 连续多少次相似调用触发中断
    threshold_count: 4
    # 相似度阈值 (0.0 - 1.0)，建议 0.95
    similarity_threshold: 0.95
    # 单次对话中连续调用工具的最大次数（防止 AI 陷入逻辑循环或过度消耗）
    max_chain_count: 30

  # YOLO 模式下需要手动确认的风险命令列表（匹配前缀）
  yolo_risk_commands:
    - op
    - deop
    - stop
    - reload
    - restart
    - kill
    - nbt
    - ban
    - unban
    - plugman

# 公告配置
notice:
  # 玩家加入时是否显示插件公告（需要 FancyHelper.notice 权限）
  show_on_join: true
  # 每隔多久从服务器拉取公告（单位：分钟），默认 5 分钟
  refresh_interval: 5
```

**settings 节点包含以下配置：**

- `debug`: 是否启用调试模式（启用后在控制台输出详细调试信息）
- `supplementary_prompt`: 补充系统提示词（会自动追加到基础系统提示词后面）
- `timeout_minutes`: CLI 模式会话的自动过期时间（分钟）
- `api_timeout_seconds`: AI API 请求超时时间（秒）
  - 对于推理模型（如 `gpt-oss-120b`、`deepseek-reasoner`、`o1` 等），建议设置为 **120-300 秒**
  - 对于普通模型（如 `gpt-4o`、`gpt-4o-mini`），建议设置为 **60-120 秒**
  - 默认值为 120 秒
- `token_warning_threshold`: Token 剩余警告阈值
- `auto_report`: 是否启用匿名错误上报
- `check_update`: 是否启用自动更新检查
- `op_update_notify`: 是否在管理员进入服务器时提示更新
- `auto_upgrade`: 是否自动下载并安装更新
- `update_mirror`: GitHub 镜像源
- `anti_loop`: 防死循环检测配置
  - `threshold_count`: 连续相似调用触发中断的次数
  - `similarity_threshold`: 相似度阈值 (0.0 - 1.0)
  - `max_chain_count`: 单次对话中连续调用工具的最大次数
- `yolo_risk_commands`: YOLO 模式下需要手动确认的风险命令列表（匹配前缀）

### OpenAI 兼容 API 配置

插件支持使用 OpenAI 兼容的 API，包括：

#### 支持的 API 提供商

- **OpenAI 官方**: 直接使用 OpenAI 的 Chat Completions API
- **Azure OpenAI**: 使用 Azure OpenAI 服务
- **DeepSeek**: 使用 DeepSeek API
- **Ollama**: 使用本地运行的 Ollama 模型
- **阿里云通义千问**: 使用阿里云通义千问 API（支持 OpenAI 兼容模式）
- **其他兼容 OpenAI 格式的 API**: 任何提供 OpenAI 兼容接口的服务

#### 配置示例

**OpenAI 官方配置：**
```yaml
openai:
  enabled: true
  api_url: "https://api.openai.com/v1/chat/completions"
  api_key: "sk-xxxxxxxxxxxxxxxxxxxxxxxx"
  model: "gpt-4o"
```

**DeepSeek 配置：**
```yaml
openai:
  enabled: true
  api_url: "https://api.deepseek.com/chat/completions"
  api_key: "sk-xxxxxxxxxxxxxxxxxxxxxxxx"
  model: "deepseek-chat"
```

**Ollama 本地配置：**
```yaml
openai:
  enabled: true
  api_url: "http://localhost:11434/v1/chat/completions"
  api_key: "ollama"  # Ollama 通常不需要真实密钥
  model: "llama3"
```

**阿里云通义千问配置：**
```yaml
openai:
  enabled: true
  api_url: "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
  api_key: "sk-xxxxxxxxxxxxxxxxxxxxxxxx"
  model: "qwen-plus"
```

**Azure OpenAI 配置：**
```yaml
openai:
  enabled: true
  api_url: "https://your-resource.openai.azure.com/openai/deployments/your-deployment/chat/completions?api-version=2024-02-01"
  api_key: "your-azure-openai-key"
  model: "gpt-4o"
```

#### 常用模型列表

- **OpenAI**: `gpt-4o`, `gpt-4o-mini`, `o1-preview`, `o1-mini`, `gpt-4-turbo`
- **DeepSeek**: `deepseek-chat`, `deepseek-reasoner`
- **Ollama**: `llama3`, `mistral`, `codellama`, `phi3` 等（取决于本地安装的模型）
- **阿里云通义千问**: `qwen-plus`, `qwen-turbo`, `qwen-max` 等

#### 注意事项

1. 启用 OpenAI 模式后，插件将优先使用 OpenAI 配置，忽略 CloudFlare 配置
2. 确保 API URL 正确且可访问
3. 某些 API 提供商可能有额外的请求头要求，插件目前只支持标准的 `Authorization` 和 `Content-Type` 头
4. 对于推理模型（如 `deepseek-reasoner`、`o1-preview`），插件会自动添加推理参数
5. 阿里云通义千问的 API URL 需要使用 `compatible-mode` 路径

## 测试建议

由于这是一个 Minecraft 插件，测试需要：

1. 设置本地 Spigot/Paper 服务器
2. 安装 ProtocolLib 依赖插件（推荐）
3. 加载插件并配置 CloudFlare API 密钥
4. 使用测试账号进入游戏进行功能测试
5. 验证以下核心功能：
   - AI 对话交互
   - 命令执行与结果反馈
   - 文件操作工具
   - 状态可视化显示
   - YOLO 模式功能
   - 预设加载机制
   - 防死循环检测
   - NBT/组件格式支持
   - 调试模式输出
   - 玩家退出时状态自动清理

## 常见问题

### ProtocolLib 依赖问题

FancyHelper 支持 ProtocolLib 作为可选依赖，用于以下功能：
- 动作栏状态显示
- 命令输出捕获
- 系统消息拦截

**解决方案**：推荐安装 ProtocolLib 插件以获得完整体验（推荐版本 5.4.0+）。未安装时核心功能仍可正常使用，但动作栏显示和命令输出捕获功能将不可用。

### 安全聊天警告

如果出现 "Failed to update secure chat state" 警告，这是由于服务器的 `enforce-secure-profile` 设置引起的。

**自动修复**：插件会自动检测此问题并尝试将 `server.properties` 中的 `enforce-secure-profile` 设置为 `false`。如果自动修改成功，只需重启服务器即可。

**手动修复**：如果自动修改失败，请手动修改 `server.properties`：

```properties
enforce-secure-profile=false
```

### 旧插件清理

插件启动时会自动清理旧版本的 MineAgent 插件文件（文件名包含 "mineagent" 关键词），并将移动到 `plugins/FancyHelper/old/` 目录，防止干扰。

**注意事项**：
- 插件只会移动包含 "mineagent" 关键词的文件/文件夹
- 不会删除任何文件，只是移动到 old 目录
- 如果移动失败，会在日志中显示警告

### 状态显示异常

如果动作栏状态显示不正常，请检查：
1. ProtocolLib 是否正确加载
2. 检查插件日志中的 `[PacketCapture]` 初始化消息
3. 确认玩家客户端版本兼容

### 配置文件更新

插件升级时会自动检测配置版本，执行以下更新流程：

1. **备份旧配置**：将现有的 `config.yml` 备份为 `config.yml.old`
2. **释放新配置**：删除旧配置文件，释放最新的默认配置
3. **迁移用户配置**：将用户自定义的配置项迁移到新配置文件中
4. **移动备份文件**：将 `config.yml.old` 移动到 `plugins/FancyHelper/old/` 目录
5. **更新预设文件**：自动更新 `preset/` 目录中的预设文件

**注意事项**：
- 插件会自动保留用户的自定义配置
- 只有存在于新配置文件中的配置项才会被迁移
- 旧配置文件会保留在 `plugins/FancyHelper/old/` 目录中
- 版本更新时还会强制更新 EULA 和 License 文件

### 防循环检测过于敏感

如果防循环检测误判正常操作：
1. 点击聊天中的 `[ 本次对话不再打断 ]` 按钮豁免当前会话
2. 或调整 `config.yml` 中的 `anti_loop` 配置参数

### 调试模式

如果需要查看详细的调试信息，可以在 `config.yml` 中启用调试模式：

```yaml
settings:
  debug: true
```

启用后，控制台会输出：
- AI 请求的详细信息（System Prompt 长度、历史消息数）
- API 调用的详细信息（URL、模型名称、响应状态码）
- 命令和预设的索引信息
- ProtocolLib 初始化状态
- Paper 聊天事件监听器注册状态

**注意**：调试模式会产生大量日志输出，建议仅在开发或排查问题时启用。

### CloudFlare API 配置

配置 `cf_key` 后，插件会自动获取对应的 `account-id`，无需手动配置。

## 开发注意事项

### Git 提交规范

遵循 Conventional Commits 规范：
- `feat:` 新功能
- `fix:` 修复 bug
- `refactor:` 重构
- `style:` 代码风格调整
- `docs:` 文档更新
- `chore:` 构建/工具更新

示例：
```
feat(CLI): 添加生成状态可视化与实时反馈
fix(CLIManager): 添加错误状态处理并修复异常状态显示
fix(CLI): 将状态显示从副标题改为动作栏并修复计时器问题
feat(防循环): 添加防死循环检测机制
feat: 添加玩家退出时清理CLI状态的监听器
docs(preset): 更新房屋建造预设并优化提示说明
fix(CLI): 退出前自动取消待处理的操作
chore: 更新版本至3.1.0
style(CLIManager): 修正 YOLO 模式消息发送行的缩进
feat(公告): 添加远程公告系统支持
fix(公告): 正确处理公告文本中的换行符
feat(CLI): 在对话会话中记录并显示总思考时间
```

### bStats 统计

插件使用 bstats 进行匿名统计，帮助开发者了解插件使用情况：
- bStats pluginId: 29036
- 统计数据包括：服务器版本、插件版本、玩家数量等
- 不收集任何敏感信息
- 可在 `config.yml` 的 `settings.auto_report` 中禁用

### 旧插件清理

插件启动时会自动清理旧版本的 MineAgent 插件文件：
- 通过 `cleanOldPluginFiles()` 方法实现
- 检测文件名是否包含 "mineagent" 关键词
- 将符合条件的文件移动到 `plugins/FancyHelper/old/` 目录
- 不会删除任何文件，只进行移动操作

### 自动修复安全配置

插件启动时会自动检测 `enforce-secure-profile` 配置：
- 通过反射调用 `shouldEnforceSecureProfile()` 方法
- 如果启用，自动修改 `server.properties` 文件
- 将 `enforce-secure-profile` 设置为 `false`
- 在日志中显示详细的操作结果

### ProtocolLib 集成

在使用 ProtocolLib 功能时：
- 始终检查 ProtocolLib 是否可用
- 使用 try-catch 处理数据包注册失败
- 在 `PacketCaptureManager` 中集中管理所有数据包监听

### 状态管理最佳实践

- 使用 `CLIManager.GenerationStatus` 枚举而非字符串
- 更新状态时同步更新 `generationStates` 和相关映射
- 状态变更应触发相应的 UI 更新（动作栏）
- 玩家退出时自动清理 CLI 会话状态，避免资源泄漏

### CloudFlare API 使用

- 插件支持两种 API：Completions API 和 Responses API
- gpt-oss 模型自动使用 Responses API，支持推理参数
- cf_key 配置后自动获取 account-id
- 使用 Java 标准库 `HttpClient`，无需 OkHttp 依赖

### 防循环检测实现

- 使用 Levenshtein 距离计算字符串相似度
- 检测连续相似调用和工具链长度
- 支持会话级豁免机制
- 配置参数位于 `ConfigManager.getAntiLoop*()` 方法

### 调试日志实现

- 所有调试日志输出都应使用 `ConfigManager.isDebug()` 进行判断
- 调试信息应包含足够的上下文，便于问题排查
- 避免在调试日志中输出敏感信息（如 API 密钥）

### 资源清理

- 玩家退出时自动清理 CLI 会话状态，防止资源泄漏
- 取消待处理的操作，避免后台任务继续执行
- 插件禁用时正确关闭所有资源（见 `FancyHelper.onDisable()` 和 `CLIManager.shutdown()`）
- `FileWatcherManager` 会在插件禁用时自动关闭 WatchService 和监控线程

## 许可证

© 2026 baicaizhale, zip8919. 保留所有权利。