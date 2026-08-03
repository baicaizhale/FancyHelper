package org.YanPl.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家列表文件读写工具：支持 runtime 目录下的 JSON 数组格式（每项为 UUID 字符串），
 * 以及旧版 txt（每行一个 UUID）格式的读取，供旧版本升级迁移使用。
 */
public final class PlayerListFileUtil {

    private PlayerListFileUtil() {
    }

    /**
     * 从 JSON 数组文件读取 UUID 集合（文件不存在时返回空集合，非法条目自动忽略）
     */
    public static Set<UUID> readJson(File file) throws IOException {
        Set<UUID> result = new HashSet<>();
        if (!file.exists()) return result;
        String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JsonArray arr;
        try {
            arr = new Gson().fromJson(json, JsonArray.class);
        } catch (JsonParseException e) {
            // 文件损坏/为空时降级为空集合，避免抛出未检查异常
            return result;
        }
        if (arr == null) return result;
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonPrimitive()) continue;
            try {
                result.add(UUID.fromString(el.getAsString().trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    /**
     * 将 UUID 集合以美化 JSON 数组格式写入文件（自动创建父目录）
     */
    public static void writeJson(File file, Collection<UUID> uuids) throws IOException {
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        JsonArray arr = new JsonArray();
        for (UUID uuid : uuids) {
            arr.add(uuid.toString());
        }
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(arr);
        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * 从旧版 txt 文件读取 UUID 集合（每行一个 UUID，文件不存在时返回空集合，非法条目自动忽略）
     */
    public static Set<UUID> readLegacyTxt(File file) throws IOException {
        Set<UUID> result = new HashSet<>();
        if (!file.exists()) return result;
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            try {
                result.add(UUID.fromString(line.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }
}
