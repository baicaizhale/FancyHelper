package org.YanPl.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.YanPl.FancyHelper;
import org.YanPl.util.I18n;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务器级记忆管理器：存储管理员写入的全局规则/事实，对所有玩家的 AI 会话生效。
 * 与 {@link InstructionManager}（玩家级、每玩家一文件）不同，本类是服务器粒度、单文件持久化。
 */
public class ServerMemoryManager {
    private static final Type LIST_TYPE = new TypeToken<List<ServerMemory>>(){}.getType();

    private static final Pattern LATIN_PATTERN = Pattern.compile("[a-z0-9]{2,}");
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]+");
    private static final int MAX_KEYWORDS = 40;

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "的", "了", "我", "你", "他", "她", "它", "是", "否", "吗", "呢", "啊", "请", "帮",
            "想", "要", "什", "么", "怎", "一", "个", "和", "与", "在", "这", "那",
            "the", "a", "an", "i", "you", "please", "me", "can", "help", "to", "for",
            "of", "is", "are", "do", "what", "how"));

    private final FancyHelper plugin;
    private final File memoryFile;
    private final Gson gson;
    private final List<ServerMemory> cache;

    public static class ServerMemory {
        private String content;
        private String timestamp;
        private String category;
        private String author;
        private String lastUsed;

        public ServerMemory(String content, String category, String author) {
            this.content = content;
            this.category = category != null ? category : "rule";
            this.author = author;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        public String getContent() { return content; }
        public String getTimestamp() { return timestamp; }
        public String getCategory() { return category; }
        public String getAuthor() { return author; }
        public String getLastUsed() { return lastUsed; }
        public void setLastUsed(String lastUsed) { this.lastUsed = lastUsed; }
    }

    public ServerMemoryManager(FancyHelper plugin) {
        this.plugin = plugin;
        File runtimeDir = new File(plugin.getDataFolder(), "runtime");
        if (!runtimeDir.exists()) {
            runtimeDir.mkdirs();
        }
        this.memoryFile = new File(runtimeDir, "server_memories.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.cache = new CopyOnWriteArrayList<>();
        loadFromFile();
    }

    public String addMemory(String content, String category, String author) {
        if (content == null || content.trim().isEmpty()) {
            return "error: " + I18n.t("inst.error.empty");
        }
        ServerMemory memory = new ServerMemory(content.trim(), category, author);
        cache.add(memory);
        evictIfFull();
        saveToFile();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[ServerMemory] " + author + " 添加了服务器记忆: " + content.trim());
        }
        return "success: 已记住: " + content.trim();
    }

    public String removeMemory(int index) {
        if (index < 1 || index > cache.size()) {
            return "error: " + I18n.t("inst.error.invalid.index", cache.size());
        }
        ServerMemory removed = cache.remove(index - 1);
        saveToFile();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[ServerMemory] 删除了服务器记忆: " + removed.getContent());
        }
        return "success: 已删除第 " + index + " 条服务器记忆: " + removed.getContent();
    }

    public String updateMemory(int index, String content, String category) {
        if (index < 1 || index > cache.size()) {
            return "error: " + I18n.t("inst.error.invalid.index", cache.size());
        }
        if (content == null || content.trim().isEmpty()) {
            return "error: " + I18n.t("inst.error.empty");
        }
        ServerMemory existing = cache.get(index - 1);
        ServerMemory updated = new ServerMemory(content.trim(), category, existing.getAuthor());
        cache.set(index - 1, updated);
        saveToFile();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[ServerMemory] 修改了第 " + index + " 条服务器记忆: " + content.trim());
        }
        return "success: 已修改第 " + index + " 条服务器记忆为: " + content.trim();
    }

    public String clearMemories() {
        cache.clear();
        if (memoryFile.exists()) {
            memoryFile.delete();
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[ServerMemory] 清空了所有服务器记忆");
        }
        return "success: 已清空所有服务器记忆";
    }

    public String listMemories() {
        if (cache.isEmpty()) {
            return "当前没有任何服务器记忆";
        }
        StringBuilder sb = new StringBuilder("服务器记忆列表:\n");
        for (int i = 0; i < cache.size(); i++) {
            ServerMemory memory = cache.get(i);
            sb.append(i + 1).append(". [").append(memory.getCategory()).append("] ");
            sb.append(memory.getContent());
            if (memory.getAuthor() != null && !memory.getAuthor().isEmpty()) {
                sb.append(" (by ").append(memory.getAuthor()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 返回缓存副本（防御外部修改）
     */
    public List<ServerMemory> getMemories() {
        return new ArrayList<>(cache);
    }

    /**
     * 根据查询文本做 Top-K 相关性筛选，返回最相关的记忆（并按命中更新 lastUsed，仅内存）。
     * 关键词提取：英文单词 + 中文单字/相邻二字组，子串命中打分。
     */
    public List<ServerMemory> getMemoriesForPrompt(String queryText, int topK, int minRelevance) {
        if (topK <= 0 || cache.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> keywords = extractKeywords(queryText);
        if (keywords.isEmpty()) {
            return new ArrayList<>();
        }

        List<ScoredMemory> scored = new ArrayList<>();
        for (ServerMemory memory : cache) {
            int score = scoreMemory(memory, keywords);
            if (score >= minRelevance) {
                scored.add(new ScoredMemory(memory, score));
            }
        }

        scored.sort((a, b) -> {
            if (a.score != b.score) return b.score - a.score;
            return b.memory.getTimestamp().compareTo(a.memory.getTimestamp());
        });

        List<ServerMemory> result = new ArrayList<>();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            ServerMemory memory = scored.get(i).memory;
            memory.setLastUsed(now);
            result.add(memory);
        }
        return result;
    }

    public void shutdown() {
        saveToFile();
        cache.clear();
    }

    private int scoreMemory(ServerMemory memory, List<String> keywords) {
        int score = 0;
        String contentLower = memory.getContent().toLowerCase();
        String categoryLower = memory.getCategory().toLowerCase();
        for (String keyword : keywords) {
            boolean isBigram = keyword.length() == 2 && isAllCjk(keyword);
            if (contentLower.contains(keyword)) {
                score += isBigram ? 3 : 2;
            }
            if (categoryLower.contains(keyword)) {
                score += 3;
            }
        }
        return score;
    }

    private List<String> extractKeywords(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String lower = query.toLowerCase();
        Set<String> keywords = new HashSet<>();

        Matcher latinMatcher = LATIN_PATTERN.matcher(lower);
        while (latinMatcher.find()) {
            String word = latinMatcher.group();
            if (!STOPWORDS.contains(word)) {
                keywords.add(word);
            }
        }

        Matcher cjkMatcher = CJK_PATTERN.matcher(lower);
        while (cjkMatcher.find()) {
            String segment = cjkMatcher.group();
            for (int i = 0; i < segment.length(); i++) {
                String ch = String.valueOf(segment.charAt(i));
                if (!STOPWORDS.contains(ch)) {
                    keywords.add(ch);
                }
            }
            for (int i = 0; i < segment.length() - 1; i++) {
                String bigram = segment.substring(i, i + 2);
                if (!isPureStopword(bigram)) {
                    keywords.add(bigram);
                }
            }
        }

        List<String> result = new ArrayList<>(keywords);
        if (result.size() > MAX_KEYWORDS) {
            result = new ArrayList<>(result.subList(0, MAX_KEYWORDS));
        }
        return result;
    }

    private boolean isPureStopword(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!STOPWORDS.contains(String.valueOf(s.charAt(i)))) {
                return false;
            }
        }
        return true;
    }

    private boolean isAllCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '一' || c > '鿿') {
                return false;
            }
        }
        return true;
    }

    private void evictIfFull() {
        int max = plugin.getConfigManager().getServerMemoryMaxEntries();
        while (cache.size() > max) {
            ServerMemory lru = findLeastRecentlyUsed();
            if (lru == null) break;
            cache.remove(lru);
            plugin.getLogger().info("[ServerMemory] 已淘汰最久未使用的记忆: " + lru.getContent());
        }
    }

    private ServerMemory findLeastRecentlyUsed() {
        ServerMemory lru = null;
        for (ServerMemory memory : cache) {
            if (lru == null) {
                lru = memory;
            } else if (compareLastUsed(memory, lru) < 0) {
                lru = memory;
            }
        }
        return lru;
    }

    private int compareLastUsed(ServerMemory a, ServerMemory b) {
        if (a.getLastUsed() == null && b.getLastUsed() == null) return 0;
        if (a.getLastUsed() == null) return -1;
        if (b.getLastUsed() == null) return 1;
        return a.getLastUsed().compareTo(b.getLastUsed());
    }

    private void saveToFile() {
        try {
            String json = gson.toJson(cache);
            Files.write(memoryFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("[ServerMemory] 保存服务器记忆失败: " + e.getMessage());
        }
    }

    private void loadFromFile() {
        if (!memoryFile.exists()) return;
        try {
            String json = new String(Files.readAllBytes(memoryFile.toPath()), StandardCharsets.UTF_8);
            List<ServerMemory> loaded = gson.fromJson(json, LIST_TYPE);
            if (loaded != null) {
                cache.addAll(loaded);
            }
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().warning("[ServerMemory] 读取服务器记忆失败: " + e.getMessage());
        }
    }

    private static class ScoredMemory {
        final ServerMemory memory;
        final int score;

        ScoredMemory(ServerMemory memory, int score) {
            this.memory = memory;
            this.score = score;
        }
    }
}
