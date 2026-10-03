package org.YanPl.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.YanPl.FancyHelper;
import org.YanPl.util.ColorUtil;
import org.YanPl.util.I18n;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 初始化向导：面向新手服主的聊天框内问答式配置流程（/fancy init）。
 * <p>
 * 玩家每答一步立即写入配置文件（答到哪存到哪，中途退出不丢已配项）。
 * 输入接管挂在 {@link CLIManager#handleChat} 最顶部：向导期间玩家不在 CLI 模式，
 * 消息被吞不会广播；全程识别 quit/exit/stop/退出 立即结束。
 * <p>
 * 线程模型：卡片渲染与配置写入在聊天线程（与既有 pendingAgreement 分支一致）；
 * 网络校验（Fancy validateKey / OpenAI 模型列表 / Cloudflare accounts）异步执行，
 * 回主线程前检查 plugin.isEnabled()，回调内检查 player.isOnline()。
 */
public class InitWizardManager {

    private static final String CF_GUIDE_URL = "https://blog.baicaizhale.top/post/create-cf-key-for-fhai";
    private static final String OPENAI_EXAMPLE_URL = "https://api.deepseek.com/chat/completions";
    /** 模型列表分页大小 */
    private static final int MODELS_PAGE_SIZE = 8;
    /** 无操作超时（毫秒），每答一步自动续期 */
    private static final long SESSION_TIMEOUT_MS = 10 * 60 * 1000L;
    /** 总步骤数（语言/AI/搜索/抓取 + 猫娘/错误上报/统计/自动升级） */
    private static final int TOTAL_STEPS = 8;

    private final FancyHelper plugin;
    private final Map<UUID, WizardSession> sessions = new ConcurrentHashMap<>();

    public InitWizardManager(FancyHelper plugin) {
        this.plugin = plugin;
        // 每 30 秒清扫一次过期会话（expiry 在每次输入后续期，故不能只在 start 时排一次性任务）
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, WizardSession>> it = sessions.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, WizardSession> e = it.next();
                if (now > e.getValue().expiry) {
                    it.remove();
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null && p.isOnline()) {
                        p.sendMessage(I18n.t("wizard.timeout"));
                    }
                }
            }
        }, 20L * 30, 20L * 30);
    }

    // ============================================================
    //  状态定义
    // ============================================================

    private enum Step {
        LANG(1), PROVIDER(2), SEARCH(3), WEBFETCH(4),
        CHECKPOINT(-1),
        MEOW(5), REPORT(6), STATS(7), UPGRADE(8),
        DONE(-1);

        final int displayNumber;

        Step(int displayNumber) {
            this.displayNumber = displayNumber;
        }
    }

    /**
     * PROVIDER 步骤内的子流程状态：
     * REGISTER_ASK（是否注册 FancyConsole）→ [FANCY_KEY 绑定] → CHOOSE（选 AI 提供商）
     * → [OPENAI_* / CF_KEY 子流程] → SEARCH。
     * 注册与 AI 提供商解耦：注册了也可以选 OpenAI/Cloudflare（此时 Fancy 搜索仍可用）。
     */
    private enum ProviderSub {
        REGISTER_ASK, CHOOSE, FANCY_KEY, OPENAI_URL, OPENAI_KEY, OPENAI_MODELS, OPENAI_MANUAL_MODEL, CF_KEY
    }

    /**
     * 绑定 Fancy key 子流程结束后去哪（由把玩家带进该子流程的入口决定）。
     * 同一个 key 粘贴卡被三处复用：2a 注册询问、2b 选 Fancy 但未绑 key、搜索步选 Fancy 搜索但未绑 key。
     */
    private enum KeyFlowReturn {
        TO_CHOOSE,       // 2a 注册询问进入：绑完（或跳过）回 AI 提供商选择卡
        FINISH_PROVIDER, // 2b 选了 Fancy 但未绑 key：绑完（或跳过）落 provider.ai 并进搜索
        SEARCH_SAVE      // 搜索步选了 Fancy 搜索但未绑 key：绑完存 fancy-tavily 进网页抓取，跳过则回搜索卡
    }

    /** SEARCH 步骤内的子流程状态 */
    private enum SearchSub {
        CHOOSE, FANCY_KEY, METASO_KEY, TAVILY_KEY
    }

    private static class WizardSession {
        Step step = Step.LANG;
        // 初始子状态是"是否注册 FancyConsole"询问（2a），答完才进 CHOOSE 选 AI 提供商（2b）
        ProviderSub providerSub = ProviderSub.REGISTER_ASK;
        SearchSub searchSub = SearchSub.CHOOSE;
        long expiry = System.currentTimeMillis() + SESSION_TIMEOUT_MS;

        // 汇总展示用
        String language;
        String providerLabel;
        String searchLabel;
        boolean webfetchOn = true;

        // PROVIDER 子流程临时数据
        boolean declinedFancy;  // 仅当 2a 明确选择"不注册"时为 true（决定后续 Fancy 选项是否画删除线）
        KeyFlowReturn keyFlowReturn;
        String pendingKey;      // 异步校验防竞态：回调仅处理与会话中一致的 key
        String openAiUrl;
        String openAiKey;
        List<String> openAiModels = new ArrayList<>();
        int modelPage = 0;
    }

    // ============================================================
    //  公开入口
    // ============================================================

    /**
     * 启动/重启向导（/fancy init）。进行中重复触发则重新开始。
     */
    public void start(Player player) {
        sessions.remove(player.getUniqueId());
        WizardSession session = new WizardSession();
        sessions.put(player.getUniqueId(), session);
        renderLang(player);
    }

    public boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /**
     * 未初始化门控的询问卡（/fancy 进入但 provider=fancy 且无 api-key 时展示）。
     * 不创建会话；玩家点击 [开始初始化向导]（/cli init）后才真正 start。
     */
    public void showUninitializedPrompt(Player player) {
        sendDivider(player);
        sendHeader(player);
        player.sendMessage("");
        player.sendMessage(I18n.t("wizard.gate.question"));
        player.sendMessage(I18n.t("wizard.gate.desc"));
        player.sendMessage("");
        player.spigot().sendMessage(clickable(I18n.t("wizard.gate.yes"), "/cli init", I18n.t("wizard.gate.yes.hover")));
        player.spigot().sendMessage(clickable(I18n.t("wizard.gate.skip"), "/cli reg", I18n.t("wizard.gate.skip.hover")));
        player.sendMessage("");
        sendDivider(player);
    }

    /**
     * 处理玩家聊天输入（由 CLIManager.handleChat 顶部转发）。
     * @return true 表示消息已被向导消费
     */
    public boolean handleChat(Player player, String message) {
        UUID uuid = player.getUniqueId();
        WizardSession session = sessions.get(uuid);
        if (session == null) return false;

        String input = message.trim();
        String lower = input.toLowerCase();

        // 全程退出关键词
        if (lower.equals("quit") || lower.equals("exit") || lower.equals("stop") || input.equals("退出")) {
            exitWizard(player);
            return true;
        }

        if (System.currentTimeMillis() > session.expiry) {
            sessions.remove(uuid);
            player.sendMessage(I18n.t("wizard.timeout"));
            return true;
        }

        // 每次有效交互续期
        session.expiry = System.currentTimeMillis() + SESSION_TIMEOUT_MS;

        switch (session.step) {
            case LANG:
                return handleLang(player, session, input);
            case PROVIDER:
                return handleProvider(player, session, input);
            case SEARCH:
                return handleSearch(player, session, input);
            case WEBFETCH:
                return handleWebfetch(player, session, input);
            case CHECKPOINT:
                return handleCheckpoint(player, session, input);
            case MEOW:
                return handleAdvanced(player, session, input, "meow", Step.REPORT);
            case REPORT:
                return handleAdvanced(player, session, input, "auto_report", Step.STATS);
            case STATS:
                return handleAdvanced(player, session, input, "stats_report", Step.UPGRADE);
            case UPGRADE:
                return handleAdvanced(player, session, input, "auto_upgrade", Step.DONE);
            default:
                sessions.remove(uuid);
                return true;
        }
    }

    /** 玩家退出向导（主动退出或退服清理共用） */
    public void cleanup(UUID uuid) {
        sessions.remove(uuid);
    }

    private void exitWizard(Player player) {
        sessions.remove(player.getUniqueId());
        player.sendMessage(I18n.t("wizard.exited"));
        player.sendMessage(I18n.t("wizard.exited.hint"));
    }

    // ============================================================
    //  各步骤：语言
    // ============================================================

    private boolean handleLang(Player player, WizardSession session, String input) {
        String lower = input.toLowerCase();
        String lang;
        if (lower.equals("1") || lower.equals("zh-cn") || input.equals("简体中文") || input.equals("中文")) {
            lang = I18n.LANG_ZH_CN;
        } else if (lower.equals("2") || lower.equals("en-us") || lower.equals("english")) {
            lang = I18n.LANG_EN_US;
        } else if (lower.equals("3") || lower.equals("lzh-cn") || input.equals("文言")) {
            lang = I18n.LANG_LZH_CN;
        } else {
            player.sendMessage(I18n.t("wizard.invalid.input", input));
            return true;
        }

        session.language = I18n.t(switch (lang) {
            case I18n.LANG_EN_US -> "wizard.lang.2";
            case I18n.LANG_LZH_CN -> "wizard.lang.3";
            default -> "wizard.lang.1";
        });
        plugin.getConfigManager().set("settings.language", lang);
        // 立即重载，向导后续文案切换语言
        plugin.getConfigManager().loadConfig();

        goTo(player, session, Step.PROVIDER);
        return true;
    }

    // ============================================================
    //  各步骤：AI 提供商
    // ============================================================

    private boolean handleProvider(Player player, WizardSession session, String input) {
        String lower = input.toLowerCase();
        switch (session.providerSub) {
            case REGISTER_ASK: {
                if (lower.equals("1") || lower.equals("yes") || input.equals("注册") || input.equals("注籍")) {
                    session.keyFlowReturn = KeyFlowReturn.TO_CHOOSE;
                    session.providerSub = ProviderSub.FANCY_KEY;
                    renderFancyKey(player);
                } else if (lower.equals("2") || lower.equals("no") || lower.equals("skip")
                        || input.equals("不注册") || input.equals("不注籍")) {
                    session.declinedFancy = true;
                    session.providerSub = ProviderSub.CHOOSE;
                    player.sendMessage(I18n.t("wizard.fancyreg.skipnote"));
                    renderProviderChoose(player, session);
                } else {
                    player.sendMessage(I18n.t("wizard.invalid.input", input));
                }
                return true;
            }
            case CHOOSE: {
                if (lower.equals("1") || lower.equals("fancy")) {
                    if (session.declinedFancy) {
                        // 选项已画删除线，这里兜底拦截直接打字输入的情况
                        player.sendMessage(I18n.t("wizard.fancyreg.needregister"));
                        renderProviderChoose(player, session);
                        return true;
                    }
                    if (plugin.getFancyConsoleManager().hasApiKey()) {
                        session.providerLabel = I18n.t("wizard.provider.1");
                        plugin.getConfigManager().set("provider.ai", "fancy");
                        goTo(player, session, Step.SEARCH);
                    } else {
                        // 注册时跳过了 key：就地补绑，绑完（或再跳过）直接以 Fancy 落地
                        session.keyFlowReturn = KeyFlowReturn.FINISH_PROVIDER;
                        session.providerSub = ProviderSub.FANCY_KEY;
                        renderFancyKey(player);
                    }
                } else if (lower.equals("2") || lower.equals("openai")) {
                    session.providerLabel = I18n.t("wizard.provider.2");
                    session.providerSub = ProviderSub.OPENAI_URL;
                    renderOpenAiUrl(player);
                } else if (lower.equals("3") || lower.equals("cloudflare")) {
                    session.providerLabel = I18n.t("wizard.provider.3");
                    session.providerSub = ProviderSub.CF_KEY;
                    renderCfKey(player);
                } else {
                    player.sendMessage(I18n.t("wizard.invalid.input", input));
                }
                return true;
            }
            case FANCY_KEY:
                return handleFancyKeyInput(player, session, input);
            case OPENAI_URL:
                return handleOpenAiUrl(player, session, input);
            case OPENAI_KEY:
                return handleOpenAiKey(player, session, input);
            case OPENAI_MODELS:
                return handleOpenAiModelPick(player, session, input);
            case OPENAI_MANUAL_MODEL:
                saveOpenAiConfig(player, session, input);
                return true;
            case CF_KEY:
                return handleCfKeyInput(player, session, input);
            default:
                return true;
        }
    }

    private boolean handleFancyKeyInput(Player player, WizardSession session, String input) {
        String lower = input.toLowerCase();
        if (lower.equals("skip")) {
            player.sendMessage(I18n.t("wizard.skip.step"));
            switch (session.keyFlowReturn) {
                case FINISH_PROVIDER -> {
                    // 用户坚持选 Fancy 但没绑 key：尊重选择，落 provider.ai（绑定前 AI 暂不可用）
                    player.sendMessage(I18n.t("wizard.fancyreg.skipnote"));
                    finishFancyProvider(player, session);
                }
                case SEARCH_SAVE -> {
                    session.searchSub = SearchSub.CHOOSE;
                    renderSearch(player, session);
                }
                default -> {
                    player.sendMessage(I18n.t("wizard.fancyreg.skipnote"));
                    session.providerSub = ProviderSub.CHOOSE;
                    renderProviderChoose(player, session);
                }
            }
            return true;
        }
        if (lower.equals("retry")) {
            renderFancyKey(player);
            return true;
        }
        if (lower.startsWith("/")) {
            player.sendMessage(I18n.t("wizard.invalid.input", input));
            return true;
        }

        final String key = input;
        session.pendingKey = key;
        player.sendMessage(I18n.t("wizard.fancy.validating"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            FancyConsoleManager.ValidateKeyResult result = plugin.getFancyConsoleManager().validateKey(key);
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    cleanup(player.getUniqueId());
                    return;
                }
                WizardSession current = sessions.get(player.getUniqueId());
                // 玩家在等待期间又粘贴了新 key：旧结果作废，等新结果
                // （key 卡被 2a/2b/搜索步三处复用，providerSub 或 searchSub 处于 FANCY_KEY 均合法）
                if (current == null || current != session
                        || (current.providerSub != ProviderSub.FANCY_KEY && current.searchSub != SearchSub.FANCY_KEY)
                        || !key.equals(current.pendingKey)) {
                    return;
                }
                if (result.valid) {
                    plugin.getFancyConsoleManager().setApiKey(key);
                    current.pendingKey = null;
                    player.sendMessage(I18n.t("wizard.fancy.success"));
                    if (result.email != null && !result.email.isEmpty()) {
                        player.sendMessage(I18n.t("wizard.fancy.success.account", result.email));
                    }
                    if (result.tier != null && !result.tier.isEmpty()) {
                        player.sendMessage(I18n.t("wizard.fancy.success.tier", result.tier));
                    }
                    switch (current.keyFlowReturn) {
                        case FINISH_PROVIDER -> finishFancyProvider(player, current);
                        case SEARCH_SAVE -> {
                            current.searchSub = SearchSub.CHOOSE;
                            saveFancySearch(player, current);
                        }
                        default -> {
                            current.providerSub = ProviderSub.CHOOSE;
                            renderProviderChoose(player, current);
                        }
                    }
                } else if (result.serviceUnavailable) {
                    player.sendMessage(I18n.t("wizard.fancy.unavailable", result.error != null ? result.error : "?"));
                    player.sendMessage(I18n.t("wizard.fancy.unavailable.hint"));
                } else {
                    player.sendMessage(I18n.t("wizard.fancy.invalid", result.error != null ? result.error : "?"));
                    player.sendMessage(I18n.t("wizard.fancy.retry.hint"));
                }
            });
        });
        return true;
    }

    /** 玩家选定 Fancy 为 AI 提供商后的落地：写 provider.ai 并进搜索步（key 可能尚未绑定，已两次提示） */
    private void finishFancyProvider(Player player, WizardSession session) {
        session.providerLabel = I18n.t("wizard.provider.1");
        plugin.getConfigManager().set("provider.ai", "fancy");
        session.providerSub = ProviderSub.CHOOSE;
        session.searchSub = SearchSub.CHOOSE;
        goTo(player, session, Step.SEARCH);
    }

    /** 搜索步选定 Fancy 搜索后的落地：写 fancy-tavily 并进网页抓取步 */
    private void saveFancySearch(Player player, WizardSession session) {
        plugin.getConfigManager().set("provider.search", "fancy-tavily");
        session.searchLabel = I18n.t("wizard.search.1");
        player.sendMessage(I18n.t("wizard.search.saved", session.searchLabel));
        goTo(player, session, Step.WEBFETCH);
    }

    private boolean handleOpenAiUrl(Player player, WizardSession session, String input) {
        String lower = input.toLowerCase();
        String url;
        if (lower.equals("yes")) {
            url = OPENAI_EXAMPLE_URL;
        } else {
            url = input;
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                player.sendMessage(I18n.t("wizard.openai.url.invalid"));
                return true;
            }
        }
        session.openAiUrl = url.trim();
        session.providerSub = ProviderSub.OPENAI_KEY;
        renderOpenAiKey(player);
        return true;
    }

    private boolean handleOpenAiKey(Player player, WizardSession session, String input) {
        if (input.isEmpty() || input.toLowerCase().startsWith("/") || input.equalsIgnoreCase("yes")) {
            player.sendMessage(I18n.t("wizard.invalid.input", input));
            return true;
        }

        final String url = session.openAiUrl;
        final String key = input;
        session.pendingKey = key;
        player.sendMessage(I18n.t("wizard.openai.models.fetching"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            FetchModelsResult result = fetchOpenAiModels(url, key);
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    cleanup(player.getUniqueId());
                    return;
                }
                WizardSession current = sessions.get(player.getUniqueId());
                if (current == null || current != session || current.providerSub != ProviderSub.OPENAI_KEY
                        || !key.equals(current.pendingKey)) {
                    return;
                }
                current.pendingKey = null;
                current.openAiUrl = url;
                current.openAiKey = key;
                if (result.models != null && !result.models.isEmpty()) {
                    current.openAiModels = result.models;
                    current.modelPage = 0;
                    current.providerSub = ProviderSub.OPENAI_MODELS;
                    renderOpenAiModels(player, current);
                } else {
                    player.sendMessage(I18n.t("wizard.openai.models.fail",
                            result.error != null ? result.error : "响应为空"));
                    current.providerSub = ProviderSub.OPENAI_MANUAL_MODEL;
                    renderOpenAiManualModel(player);
                }
            });
        });
        return true;
    }

    private boolean handleOpenAiModelPick(Player player, WizardSession session, String input) {
        String lower = input.toLowerCase();
        // 翻页
        if (lower.equals("next") || input.equals("下一页") || input.equals("后页")) {
            int pages = pageCount(session.openAiModels.size());
            if (session.modelPage < pages - 1) session.modelPage++;
            renderOpenAiModels(player, session);
            return true;
        }
        if (lower.equals("prev") || input.equals("上一页") || input.equals("前页")) {
            if (session.modelPage > 0) session.modelPage--;
            renderOpenAiModels(player, session);
            return true;
        }
        if (lower.equals("other") || input.equals("手动") || input.equals("手書")) {
            session.providerSub = ProviderSub.OPENAI_MANUAL_MODEL;
            renderOpenAiManualModel(player);
            return true;
        }
        // 页内编号选择
        if (input.matches("\\d+")) {
            int n = Integer.parseInt(input);
            int index = session.modelPage * MODELS_PAGE_SIZE + n - 1;
            if (n >= 1 && index < session.openAiModels.size()) {
                saveOpenAiConfig(player, session, session.openAiModels.get(index));
                return true;
            }
            player.sendMessage(I18n.t("wizard.invalid.input", input));
            return true;
        }
        // 其余任意文本按手动输入模型名处理
        saveOpenAiConfig(player, session, input);
        return true;
    }

    private void saveOpenAiConfig(Player player, WizardSession session, String model) {
        String trimmed = model.trim();
        if (trimmed.isEmpty() || trimmed.toLowerCase().startsWith("/")) {
            player.sendMessage(I18n.t("wizard.invalid.input", model));
            return;
        }
        ConfigManager cm = plugin.getConfigManager();
        cm.set("provider.ai", "openai");
        cm.set("openai.api_url", session.openAiUrl);
        cm.set("openai.api_key", session.openAiKey);
        cm.set("openai.model", trimmed);
        cm.set("openai.co-model", trimmed);
        player.sendMessage(I18n.t("wizard.openai.saved", trimmed));
        session.providerSub = ProviderSub.CHOOSE;
        goTo(player, session, Step.SEARCH);
    }

    private boolean handleCfKeyInput(Player player, WizardSession session, String input) {
        String lower = input.toLowerCase();
        if (lower.equals("skip")) {
            player.sendMessage(I18n.t("wizard.skip.step"));
            player.sendMessage(I18n.t("wizard.cf.retry.hint"));
            goTo(player, session, Step.SEARCH);
            return true;
        }
        if (lower.equals("retry")) {
            renderCfKey(player);
            return true;
        }
        if (lower.startsWith("/")) {
            player.sendMessage(I18n.t("wizard.invalid.input", input));
            return true;
        }

        final String key = input;
        session.pendingKey = key;
        player.sendMessage(I18n.t("wizard.cf.validating"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String error = validateCloudflareKey(key);
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    cleanup(player.getUniqueId());
                    return;
                }
                WizardSession current = sessions.get(player.getUniqueId());
                if (current == null || current != session || current.providerSub != ProviderSub.CF_KEY
                        || !key.equals(current.pendingKey)) {
                    return;
                }
                current.pendingKey = null;
                if (error == null) {
                    ConfigManager cm = plugin.getConfigManager();
                    cm.set("provider.ai", "cloudflare");
                    cm.set("cloudflare.cf_key", key);
                    player.sendMessage(I18n.t("wizard.cf.success"));
                    player.sendMessage(I18n.t("wizard.cf.saved"));
                    goTo(player, current, Step.SEARCH);
                } else if (isCloudflareAuthError(error)) {
                    player.sendMessage(I18n.t("wizard.cf.invalid", error));
                    player.sendMessage(I18n.t("wizard.cf.retry.hint"));
                } else {
                    player.sendMessage(I18n.t("wizard.cf.unavailable", error));
                    player.sendMessage(I18n.t("wizard.cf.unavailable.hint"));
                }
            });
        });
        return true;
    }

    // ============================================================
    //  各步骤：搜索
    // ============================================================

    private boolean handleSearch(Player player, WizardSession session, String input) {
        String lower = input.toLowerCase();
        switch (session.searchSub) {
            case CHOOSE: {
                if (lower.equals("1") || lower.equals("fancy")) {
                    if (session.declinedFancy) {
                        // 选项已画删除线，这里兜底拦截直接打字输入的情况
                        player.sendMessage(I18n.t("wizard.fancyreg.needregister"));
                        renderSearch(player, session);
                        return true;
                    }
                    if (plugin.getFancyConsoleManager().hasApiKey()) {
                        saveFancySearch(player, session);
                    } else {
                        // 未绑 key：先补绑定，绑完存 fancy-tavily；再跳过则回搜索卡
                        session.keyFlowReturn = KeyFlowReturn.SEARCH_SAVE;
                        session.searchSub = SearchSub.FANCY_KEY;
                        renderFancyKey(player);
                    }
                } else if (lower.equals("2") || lower.equals("metaso")) {
                    session.searchSub = SearchSub.METASO_KEY;
                    renderSearchKey(player, true);
                } else if (lower.equals("3") || lower.equals("tavily")) {
                    session.searchSub = SearchSub.TAVILY_KEY;
                    renderSearchKey(player, false);
                } else if (lower.equals("4") || lower.equals("skip")) {
                    session.searchLabel = I18n.t("wizard.search.4");
                    player.sendMessage(I18n.t("wizard.skip.step"));
                    goTo(player, session, Step.WEBFETCH);
                } else {
                    player.sendMessage(I18n.t("wizard.invalid.input", input));
                }
                return true;
            }
            case FANCY_KEY:
                return handleFancyKeyInput(player, session, input);
            case METASO_KEY: {
                if (lower.equals("skip")) {
                    player.sendMessage(I18n.t("wizard.skip.step"));
                    session.searchSub = SearchSub.CHOOSE;
                    goTo(player, session, Step.WEBFETCH);
                    return true;
                }
                String token = input;
                if (!token.toLowerCase().startsWith("mk-")) {
                    player.sendMessage(I18n.t("wizard.search.prefix.warn", "mk-"));
                }
                plugin.getConfigManager().set("metaso.enabled", true);
                plugin.getConfigManager().set("metaso.api_token", token);
                plugin.getConfigManager().set("provider.search", "metaso");
                session.searchLabel = I18n.t("wizard.search.2");
                player.sendMessage(I18n.t("wizard.search.saved", session.searchLabel));
                session.searchSub = SearchSub.CHOOSE;
                goTo(player, session, Step.WEBFETCH);
                return true;
            }
            case TAVILY_KEY: {
                if (lower.equals("skip")) {
                    player.sendMessage(I18n.t("wizard.skip.step"));
                    session.searchSub = SearchSub.CHOOSE;
                    goTo(player, session, Step.WEBFETCH);
                    return true;
                }
                String key = input;
                if (!key.toLowerCase().startsWith("tvly-")) {
                    player.sendMessage(I18n.t("wizard.search.prefix.warn", "tvly-"));
                }
                plugin.getConfigManager().set("tavily.enabled", true);
                plugin.getConfigManager().set("tavily.api_key", key);
                plugin.getConfigManager().set("provider.search", "tavily");
                session.searchLabel = I18n.t("wizard.search.3");
                player.sendMessage(I18n.t("wizard.search.saved", session.searchLabel));
                session.searchSub = SearchSub.CHOOSE;
                goTo(player, session, Step.WEBFETCH);
                return true;
            }
            default:
                return true;
        }
    }

    // ============================================================
    //  各步骤：网页抓取 / 检查点 / 进阶开关
    // ============================================================

    private boolean handleWebfetch(Player player, WizardSession session, String input) {
        Boolean enable = parseYesNo(input);
        if (enable == null) {
            player.sendMessage(I18n.t("wizard.invalid.input", input));
            return true;
        }
        session.webfetchOn = enable;
        plugin.getConfigManager().set("provider.jina", enable ? "fancy" : "none");
        player.sendMessage(enable ? I18n.t("wizard.summary.enabled") : I18n.t("wizard.summary.disabled"));
        goTo(player, session, Step.CHECKPOINT);
        return true;
    }

    private boolean handleCheckpoint(Player player, WizardSession session, String input) {
        String lower = input.toLowerCase();
        if (lower.equals("1") || lower.equals("continue") || input.equals("继续") || input.equals("續")) {
            goTo(player, session, Step.MEOW);
        } else if (lower.equals("2") || lower.equals("finish") || input.equals("结束") || input.equals("結束")) {
            finish(player, session);
        } else {
            player.sendMessage(I18n.t("wizard.invalid.input", input));
        }
        return true;
    }

    /**
     * 进阶开关统一处理（猫娘/错误上报/统计/自动升级）。
     * @param configKey settings 段下的键名
     * @param next 当前开关答完后的下一步
     */
    private boolean handleAdvanced(Player player, WizardSession session, String input, String configKey, Step next) {
        Boolean value = parseYesNo(input);
        if (value == null) {
            player.sendMessage(I18n.t("wizard.invalid.input", input));
            return true;
        }
        plugin.getConfigManager().set("settings." + configKey, value);
        player.sendMessage(value ? I18n.t("wizard.summary.enabled") : I18n.t("wizard.summary.disabled"));
        if (next == Step.DONE) {
            finish(player, session);
        } else {
            goTo(player, session, next);
        }
        return true;
    }

    /** 解析是/否输入；null 表示未识别 */
    private Boolean parseYesNo(String input) {
        String lower = input.toLowerCase();
        switch (lower) {
            case "1": case "y": case "yes": case "on": case "true":
                return true;
            case "2": case "n": case "no": case "off": case "false":
                return false;
        }
        return switch (input) {
            case "开", "开启", "是" -> true;
            case "关", "关闭", "否" -> false;
            default -> null;
        };
    }

    /** 完成向导：展示终卡并直接带玩家进入 CLI 对话模式 */
    private void finish(Player player, WizardSession session) {
        sessions.remove(player.getUniqueId());
        sendDivider(player);
        sendHeader(player);
        player.sendMessage("");
        player.sendMessage(I18n.t("wizard.done.title"));
        player.sendMessage("");
        sendSummary(player, session, true);
        player.sendMessage("");
        if (player.hasPermission("fancyhelper.cli")) {
            // 自动带进对话模式：新手常在终卡后直接打字聊天（此时不在 CLI 模式，
            // 消息会当普通聊天广播出去且没有任何 AI 回复），不依赖玩家点按钮或敲 /fancy
            player.sendMessage(I18n.t("wizard.done.hint.auto"));
            plugin.getCliManager().enterCLI(player);
        } else {
            player.sendMessage(I18n.t("wizard.done.hint"));
            player.spigot().sendMessage(clickable(I18n.t("wizard.done.button"), "/cli", I18n.t("wizard.done.button.hover")));
        }
        player.sendMessage("");
        sendDivider(player);
    }

    // ============================================================
    //  卡片渲染
    // ============================================================

    private void renderLang(Player player) {
        sendCardTop(player, Step.LANG);
        player.sendMessage(I18n.t("wizard.lang.question"));
        player.sendMessage("");
        player.spigot().sendMessage(clickable(" §x[1] §f" + I18n.t("wizard.lang.1") + " §7- " + I18n.t("wizard.lang.1.desc"),
                "/cli select 1", I18n.t("wizard.option.hint")));
        player.spigot().sendMessage(clickable(" §x[2] §f" + I18n.t("wizard.lang.2") + " §7- " + I18n.t("wizard.lang.2.desc"),
                "/cli select 2", I18n.t("wizard.option.hint")));
        player.spigot().sendMessage(clickable(" §x[3] §f" + I18n.t("wizard.lang.3") + " §7- " + I18n.t("wizard.lang.3.desc"),
                "/cli select 3", I18n.t("wizard.option.hint")));
        sendCardFooter(player, false);
    }

    /** 第 2 步第 1 卡：是否注册 FancyConsole（注册与 AI 提供商解耦） */
    private void renderProviderAsk(Player player) {
        sendCardTop(player, Step.PROVIDER);
        player.sendMessage(I18n.t("wizard.fancyreg.question"));
        player.sendMessage("");
        player.spigot().sendMessage(clickable(" §x[1] §f" + I18n.t("wizard.fancyreg.1") + " §7- " + I18n.t("wizard.fancyreg.1.desc"),
                "/cli select 1", I18n.t("wizard.option.hint")));
        player.spigot().sendMessage(clickable(" §x[2] §f" + I18n.t("wizard.fancyreg.2") + " §7- " + I18n.t("wizard.fancyreg.2.desc"),
                "/cli select 2", I18n.t("wizard.option.hint")));
        sendCardFooter(player, false);
    }

    /** 第 2 步第 2 卡：选 AI 提供商；仅在 2a 明确"不注册"时 Fancy 选项画删除线禁用 */
    private void renderProviderChoose(Player player, WizardSession session) {
        sendCardTop(player, Step.PROVIDER);
        player.sendMessage(I18n.t("wizard.provider.question"));
        if (plugin.getFancyConsoleManager().hasApiKey()) {
            player.sendMessage(I18n.t("wizard.fancyreg.note.bound"));
        }
        player.sendMessage("");
        if (!session.declinedFancy) {
            player.spigot().sendMessage(clickable(" §x[1] §f" + I18n.t("wizard.provider.1") + " §7- " + I18n.t("wizard.provider.1.desc"),
                    "/cli select 1", I18n.t("wizard.option.hint")));
        } else {
            player.spigot().sendMessage(disabledOption(" §7[1] §8§m" + I18n.t("wizard.provider.1")
                    + I18n.t("wizard.provider.disabled.suffix"), I18n.t("wizard.provider.disabled.hover")));
        }
        player.spigot().sendMessage(clickable(" §x[2] §f" + I18n.t("wizard.provider.2") + " §7- " + I18n.t("wizard.provider.2.desc"),
                "/cli select 2", I18n.t("wizard.option.hint")));
        player.spigot().sendMessage(clickable(" §x[3] §f" + I18n.t("wizard.provider.3") + " §7- " + I18n.t("wizard.provider.3.desc"),
                "/cli select 3", I18n.t("wizard.option.hint")));
        sendCardFooter(player, false);
    }

    private void renderFancyKey(Player player) {
        sendCardTop(player, Step.PROVIDER);
        player.sendMessage(I18n.t("wizard.fancy.question"));
        player.sendMessage("");
        player.spigot().sendMessage(link(I18n.t("wizard.fancy.click"),
                plugin.getFancyConsoleManager().getRegistrationUrl(), I18n.t("wizard.fancy.hover")));
        player.sendMessage(I18n.t("wizard.fancy.paste"));
        sendCardFooter(player, true);
    }

    private void renderOpenAiUrl(Player player) {
        sendCardTop(player, Step.PROVIDER);
        player.sendMessage(I18n.t("wizard.openai.url.question"));
        player.sendMessage(I18n.t("wizard.openai.url.example"));
        player.sendMessage(I18n.t("wizard.openai.url.useexample"));
        sendCardFooter(player, false);
    }

    private void renderOpenAiKey(Player player) {
        sendCardTop(player, Step.PROVIDER);
        player.sendMessage(I18n.t("wizard.openai.key.question"));
        sendCardFooter(player, false);
    }

    private void renderOpenAiModels(Player player, WizardSession session) {
        sendCardTop(player, Step.PROVIDER);
        player.sendMessage(I18n.t("wizard.openai.models.question"));
        player.sendMessage("");
        int pages = pageCount(session.openAiModels.size());
        int page = Math.min(session.modelPage, pages - 1);
        int start = page * MODELS_PAGE_SIZE;
        int end = Math.min(start + MODELS_PAGE_SIZE, session.openAiModels.size());
        for (int i = start; i < end; i++) {
            int n = i - start + 1;
            player.spigot().sendMessage(clickable(" §x[" + n + "] §f" + session.openAiModels.get(i),
                    "/cli select " + n, I18n.t("wizard.openai.models.pick.hover")));
        }
        player.sendMessage("");
        // 翻页 + 手动输入
        TextComponent nav = new TextComponent();
        if (page > 0) {
            nav.addExtra(clickable(I18n.t("wizard.openai.models.prev"), "/cli select prev", null));
            nav.addExtra(new TextComponent("  "));
        }
        if (page < pages - 1) {
            nav.addExtra(clickable(I18n.t("wizard.openai.models.next"), "/cli select next", null));
            nav.addExtra(new TextComponent("  "));
        }
        if (pages > 1) {
            nav.addExtra(new TextComponent(TextComponent.fromLegacyText(I18n.t("wizard.openai.models.page", page + 1, pages))));
        }
        if (nav.getExtra() != null && !nav.getExtra().isEmpty()) {
            player.spigot().sendMessage(nav);
        }
        player.spigot().sendMessage(clickable(I18n.t("wizard.openai.models.other"), "/cli select other",
                I18n.t("wizard.openai.models.other.hover")));
        sendCardFooter(player, false);
    }

    private void renderOpenAiManualModel(Player player) {
        sendCardTop(player, Step.PROVIDER);
        player.sendMessage(I18n.t("wizard.openai.models.fail.hint"));
        sendCardFooter(player, false);
    }

    private void renderCfKey(Player player) {
        sendCardTop(player, Step.PROVIDER);
        player.sendMessage(I18n.t("wizard.cf.question"));
        player.sendMessage("");
        player.spigot().sendMessage(link(I18n.t("wizard.cf.click"), CF_GUIDE_URL, I18n.t("wizard.cf.hover")));
        player.sendMessage(I18n.t("wizard.cf.paste"));
        sendCardFooter(player, true);
    }

    private void renderSearch(Player player, WizardSession session) {
        sendCardTop(player, Step.SEARCH);
        player.sendMessage(I18n.t("wizard.search.question"));
        player.sendMessage("");
        if (!session.declinedFancy) {
            player.spigot().sendMessage(clickable(" §x[1] §f" + I18n.t("wizard.search.1") + " §7- " + I18n.t("wizard.search.1.desc"),
                    "/cli select 1", I18n.t("wizard.option.hint")));
        } else {
            player.spigot().sendMessage(disabledOption(" §7[1] §8§m" + I18n.t("wizard.search.1")
                    + I18n.t("wizard.provider.disabled.suffix"), I18n.t("wizard.provider.disabled.hover")));
        }
        player.spigot().sendMessage(clickable(" §x[2] §f" + I18n.t("wizard.search.2") + " §7- " + I18n.t("wizard.search.2.desc"),
                "/cli select 2", I18n.t("wizard.option.hint")));
        player.spigot().sendMessage(clickable(" §x[3] §f" + I18n.t("wizard.search.3") + " §7- " + I18n.t("wizard.search.3.desc"),
                "/cli select 3", I18n.t("wizard.option.hint")));
        player.spigot().sendMessage(clickable(" §x[4] §f" + I18n.t("wizard.search.4") + " §7- " + I18n.t("wizard.search.4.desc"),
                "/cli select 4", I18n.t("wizard.option.hint")));
        sendCardFooter(player, false);
    }

    private void renderSearchKey(Player player, boolean metaso) {
        sendCardTop(player, Step.SEARCH);
        player.sendMessage(I18n.t(metaso ? "wizard.search.metaso.question" : "wizard.search.tavily.question"));
        player.sendMessage("");
        player.spigot().sendMessage(link(I18n.t(metaso ? "wizard.search.metaso.click" : "wizard.search.tavily.click"),
                metaso ? "https://metaso.cn/search-api/api-keys" : "https://app.tavily.com/home",
                I18n.t(metaso ? "wizard.search.metaso.hover" : "wizard.search.tavily.hover")));
        sendCardFooter(player, true);
    }

    private void renderWebfetch(Player player) {
        sendCardTop(player, Step.WEBFETCH);
        player.sendMessage(I18n.t("wizard.webfetch.question"));
        player.sendMessage("");
        player.spigot().sendMessage(clickable(" §x[1] §f" + I18n.t("wizard.webfetch.1") + " §7- " + I18n.t("wizard.webfetch.1.desc"),
                "/cli select 1", I18n.t("wizard.option.hint")));
        player.spigot().sendMessage(clickable(" §x[2] §f" + I18n.t("wizard.webfetch.2") + " §7- " + I18n.t("wizard.webfetch.2.desc"),
                "/cli select 2", I18n.t("wizard.option.hint")));
        sendCardFooter(player, false);
    }

    private void renderCheckpoint(Player player, WizardSession session) {
        sendCardTop(player, Step.CHECKPOINT);
        player.sendMessage(I18n.t("wizard.basic.done"));
        sendSummary(player, session, false);
        player.sendMessage("");
        player.spigot().sendMessage(clickable(I18n.t("wizard.basic.continue"), "/cli select 1",
                I18n.t("wizard.basic.continue.hover")));
        player.spigot().sendMessage(clickable(I18n.t("wizard.basic.finish"), "/cli select 2",
                I18n.t("wizard.basic.finish.hover")));
        sendCardFooter(player, false);
    }

    private void renderAdvanced(Player player, Step step) {
        sendCardTop(player, step);
        String q;
        String desc;
        switch (step) {
            case MEOW -> {
                q = "wizard.adv.meow.question";
                desc = "wizard.adv.meow.desc";
            }
            case REPORT -> {
                q = "wizard.adv.report.question";
                desc = "wizard.adv.report.desc";
            }
            case STATS -> {
                q = "wizard.adv.stats.question";
                desc = "wizard.adv.stats.desc";
            }
            default -> {
                q = "wizard.adv.upgrade.question";
                desc = "wizard.adv.upgrade.desc";
            }
        }
        player.sendMessage(I18n.t(q));
        player.sendMessage(I18n.t(desc));
        player.sendMessage("");
        TextComponent line = new TextComponent();
        line.addExtra(clickable(I18n.t("wizard.adv.yes"), "/cli select 1", I18n.t("wizard.adv.yes.hover")));
        line.addExtra(new TextComponent("   "));
        line.addExtra(clickable(I18n.t("wizard.adv.no"), "/cli select 2", I18n.t("wizard.adv.no.hover")));
        player.spigot().sendMessage(line);
        sendCardFooter(player, false);
    }

    // ============================================================
    //  渲染工具
    // ============================================================

    private void sendDivider(Player player) {
        player.sendMessage(ChatColor.DARK_GRAY.toString() + ChatColor.STRIKETHROUGH
                + "--------------------------------------------------");
    }

    /** ▌ FancyHelper ── 初始化向导 */
    private void sendHeader(Player player) {
        TextComponent line = new TextComponent();
        TextComponent bar = new TextComponent("▌ ");
        bar.setColor(net.md_5.bungee.api.ChatColor.DARK_GRAY);
        line.addExtra(bar);
        TextComponent brand = new TextComponent("FancyHelper ");
        brand.setColor(net.md_5.bungee.api.ChatColor.of("#30AEE5"));
        line.addExtra(brand);
        TextComponent dash = new TextComponent("──");
        dash.setColor(net.md_5.bungee.api.ChatColor.DARK_GRAY);
        dash.setStrikethrough(true);
        line.addExtra(dash);
        TextComponent title = new TextComponent(" " + I18n.t("wizard.title"));
        title.setColor(net.md_5.bungee.api.ChatColor.WHITE);
        line.addExtra(title);
        player.spigot().sendMessage(line);
    }

    private void sendCardTop(Player player, Step step) {
        sendDivider(player);
        sendHeader(player);
        if (step.displayNumber > 0) {
            player.sendMessage(ColorUtil.translateCustomColors(
                    I18n.t("wizard.step.progress", step.displayNumber, TOTAL_STEPS)
                            + "  " + progressDots(step.displayNumber)));
        }
        player.sendMessage("");
    }

    private String progressDots(int current) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= TOTAL_STEPS; i++) {
            if (i < current) sb.append("§x●");
            else if (i == current) sb.append("§z●");
            else sb.append("§8○");
        }
        return sb.toString();
    }

    private void sendCardFooter(Player player, boolean skippable) {
        player.sendMessage("");
        if (skippable) {
            player.sendMessage(I18n.t("wizard.skip.hint"));
        }
        player.sendMessage(I18n.t("wizard.footer.quit"));
        sendDivider(player);
    }

    private void sendSummary(Player player, WizardSession session, boolean includeAdvanced) {
        player.sendMessage(I18n.t("wizard.basic.desc"));
        player.sendMessage(I18n.t("wizard.basic.language",
                session.language != null ? session.language : I18n.t("wizard.summary.notset")));
        player.sendMessage(I18n.t("wizard.basic.provider",
                session.providerLabel != null ? session.providerLabel : I18n.t("wizard.summary.notset")));
        player.sendMessage(I18n.t("wizard.basic.search",
                session.searchLabel != null ? session.searchLabel : I18n.t("wizard.summary.notset")));
        player.sendMessage(I18n.t("wizard.basic.webfetch",
                session.webfetchOn ? I18n.t("wizard.summary.enabled") : I18n.t("wizard.summary.disabled")));
        if (includeAdvanced) {
            ConfigManager cm = plugin.getConfigManager();
            String on = I18n.t("wizard.summary.enabled");
            String off = I18n.t("wizard.summary.disabled");
            player.sendMessage(I18n.t("wizard.basic.meow", cm.isMeowEnabled() ? on : off));
            player.sendMessage(I18n.t("wizard.basic.report", cm.isAutoReportEnabled() ? on : off));
            player.sendMessage(I18n.t("wizard.basic.stats", cm.isStatsReportEnabled() ? on : off));
            player.sendMessage(I18n.t("wizard.basic.upgrade", cm.isAutoUpgrade() ? on : off));
        }
    }

    /** 可点击选项（RUN_COMMAND，经 /cli select 转发回 handleChat） */
    private TextComponent clickable(String legacyText, String clickCommand, String hover) {
        TextComponent c = new TextComponent(TextComponent.fromLegacyText(ColorUtil.translateCustomColors(legacyText)));
        c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, clickCommand));
        if (hover != null) {
            c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)));
        }
        return c;
    }

    /** 可点击链接（OPEN_URL） */
    private TextComponent link(String legacyText, String url, String hover) {
        TextComponent c = new TextComponent(TextComponent.fromLegacyText(ColorUtil.translateCustomColors(legacyText)));
        c.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)));
        return c;
    }

    /** 禁用态选项：置灰删除线、不可点击，悬停解释原因 */
    private TextComponent disabledOption(String legacyText, String hover) {
        TextComponent c = new TextComponent(TextComponent.fromLegacyText(ColorUtil.translateCustomColors(legacyText)));
        c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)));
        return c;
    }

    // ============================================================
    //  流程控制
    // ============================================================

    /** 进入下一步并渲染对应卡片 */
    private void goTo(Player player, WizardSession session, Step next) {
        session.step = next;
        session.expiry = System.currentTimeMillis() + SESSION_TIMEOUT_MS;
        switch (next) {
            case PROVIDER -> {
                // 已绑定过 Fancy 时免问注册，直接进 AI 提供商选择
                if (plugin.getFancyConsoleManager().hasApiKey()) {
                    session.providerSub = ProviderSub.CHOOSE;
                    renderProviderChoose(player, session);
                } else {
                    renderProviderAsk(player);
                }
            }
            case SEARCH -> renderSearch(player, session);
            case WEBFETCH -> renderWebfetch(player);
            case CHECKPOINT -> renderCheckpoint(player, session);
            case MEOW -> renderAdvanced(player, Step.MEOW);
            case REPORT -> renderAdvanced(player, Step.REPORT);
            case STATS -> renderAdvanced(player, Step.STATS);
            case UPGRADE -> renderAdvanced(player, Step.UPGRADE);
            default -> finish(player, session);
        }
    }

    private int pageCount(int size) {
        return Math.max(1, (size + MODELS_PAGE_SIZE - 1) / MODELS_PAGE_SIZE);
    }

    // ============================================================
    //  网络校验（均须在异步线程调用）
    // ============================================================

    /**
     * 从 OpenAI 兼容 chat/completions 地址推导模型列表地址：
     * 剥掉末尾 /chat/completions（或 /completions）与多余斜杠得到 base；
     * base 以 /v1 结尾则补 /models，否则补 /v1/models。
     * 阿里云兼容模式（/compatible-mode/v1/chat/completions）自然落在 /v1 规则上。
     */
    public static String deriveModelsUrl(String apiUrl) {
        String base = apiUrl == null ? "" : apiUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String lower = base.toLowerCase();
        if (lower.endsWith("/chat/completions")) {
            base = base.substring(0, base.length() - "/chat/completions".length());
        } else if (lower.endsWith("/completions")) {
            base = base.substring(0, base.length() - "/completions".length());
        }
        if (base.toLowerCase().endsWith("/v1")) {
            return base + "/models";
        }
        return base + "/v1/models";
    }

    private static class FetchModelsResult {
        final List<String> models;
        final String error;

        FetchModelsResult(List<String> models, String error) {
            this.models = models;
            this.error = error;
        }
    }

    /**
     * GET {base}/models 拉取 OpenAI 兼容模型列表。
     * 任何失败都返回带 error 的结果（不抛异常），由调用方引导用户手动输入模型名。
     */
    private FetchModelsResult fetchOpenAiModels(String apiUrl, String apiKey) {
        String modelsUrl;
        try {
            modelsUrl = deriveModelsUrl(apiUrl);
            if (!modelsUrl.startsWith("http://") && !modelsUrl.startsWith("https://")) {
                return new FetchModelsResult(null, "URL 格式错误");
            }
        } catch (Exception e) {
            return new FetchModelsResult(null, e.getMessage());
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelsUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return new FetchModelsResult(null, "API Key 可能无效 (HTTP " + response.statusCode() + ")");
            }
            if (response.statusCode() != 200) {
                return new FetchModelsResult(null, "HTTP " + response.statusCode());
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("data") || !json.get("data").isJsonArray()) {
                return new FetchModelsResult(null, "响应中没有模型数据");
            }
            List<String> models = new ArrayList<>();
            for (JsonElement el : json.getAsJsonArray("data")) {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.has("id") && obj.get("id").isJsonPrimitive()) {
                        models.add(obj.get("id").getAsString());
                    }
                }
            }
            models.sort(String.CASE_INSENSITIVE_ORDER);
            if (models.isEmpty()) {
                return new FetchModelsResult(null, "响应中没有模型数据");
            }
            return new FetchModelsResult(models, null);
        } catch (Exception e) {
            return new FetchModelsResult(null, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /**
     * 校验 Cloudflare API Token：GET /client/v4/accounts + Bearer。
     * @return null 表示有效；以 "auth:" 前缀表示 key 无效，其余为服务/网络不可用
     */
    private String validateCloudflareKey(String cfKey) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cloudflare.com/client/v4/accounts"))
                    .header("Authorization", "Bearer " + cfKey)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            String firstError = null;
            JsonObject json = null;
            try {
                json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("errors") && json.get("errors").isJsonArray()
                        && json.getAsJsonArray("errors").size() > 0
                        && json.getAsJsonArray("errors").get(0).isJsonObject()) {
                    firstError = json.getAsJsonArray("errors").get(0).getAsJsonObject()
                            .get("message").getAsString();
                }
            } catch (Exception ignored) {
                // 非 JSON 响应体：按状态码处理
            }

            if (status == 200 && json != null && json.has("success") && json.get("success").getAsBoolean()) {
                return null;
            }
            if (status == 400 || status == 401 || status == 403) {
                return "auth:" + (firstError != null ? firstError : "HTTP " + status);
            }
            return "HTTP " + status + (firstError != null ? " - " + firstError : "");
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }

    private boolean isCloudflareAuthError(String error) {
        return error != null && error.startsWith("auth:");
    }
}
