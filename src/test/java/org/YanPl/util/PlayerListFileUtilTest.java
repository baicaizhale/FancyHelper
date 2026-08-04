package org.YanPl.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlayerListFileUtil 单元测试")
class PlayerListFileUtilTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("writeJson 后可被 readJson 完整读回")
    void testWriteReadRoundTrip() throws IOException {
        Set<UUID> uuids = new LinkedHashSet<>();
        uuids.add(UUID.randomUUID());
        uuids.add(UUID.randomUUID());
        File file = tempDir.resolve("players.json").toFile();

        PlayerListFileUtil.writeJson(file, uuids);

        assertEquals(uuids, PlayerListFileUtil.readJson(file));
    }

    @Test
    @DisplayName("readJson 在文件不存在时返回空集合")
    void testReadJsonMissingFile() throws IOException {
        File file = tempDir.resolve("not_exist.json").toFile();

        assertTrue(PlayerListFileUtil.readJson(file).isEmpty());
    }

    @Test
    @DisplayName("readJson 忽略非法条目")
    void testReadJsonIgnoresInvalidEntries() throws IOException {
        UUID valid = UUID.randomUUID();
        File file = tempDir.resolve("players.json").toFile();
        Files.writeString(file.toPath(),
            "[\"" + valid + "\", \"not-a-uuid\", 123, null]");

        Set<UUID> result = PlayerListFileUtil.readJson(file);

        assertEquals(Set.of(valid), result);
    }

    @Test
    @DisplayName("readJson 遇到损坏/空文件应返回空集合而非抛异常")
    void testReadJsonMalformedFileReturnsEmpty() throws IOException {
        File malformed = tempDir.resolve("malformed.json").toFile();
        Files.writeString(malformed.toPath(), "{invalid json!!!");
        assertTrue(PlayerListFileUtil.readJson(malformed).isEmpty());

        File empty = tempDir.resolve("empty.json").toFile();
        Files.writeString(empty.toPath(), "");
        assertTrue(PlayerListFileUtil.readJson(empty).isEmpty());
    }

    @Test
    @DisplayName("readLegacyTxt 按行读取 UUID 并忽略空行")
    void testReadLegacyTxt() throws IOException {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        File file = tempDir.resolve("players.txt").toFile();
        Files.writeString(file.toPath(), a + "\n\n" + b + "\n");

        Set<UUID> result = PlayerListFileUtil.readLegacyTxt(file);

        assertEquals(Set.of(a, b), result);
    }

    @Test
    @DisplayName("writeJson 自动创建父目录")
    void testWriteJsonCreatesParentDir() throws IOException {
        Set<UUID> uuids = new HashSet<>();
        uuids.add(UUID.randomUUID());
        File file = tempDir.resolve("runtime").resolve("players.json").toFile();

        PlayerListFileUtil.writeJson(file, uuids);

        assertTrue(file.exists());
        assertEquals(uuids, PlayerListFileUtil.readJson(file));
    }

    @Test
    @DisplayName("旧版 txt 内容迁移为 JSON 后数据一致")
    void testLegacyTxtMigrationEquivalence() throws IOException {
        Set<UUID> expected = new LinkedHashSet<>();
        expected.add(UUID.randomUUID());
        expected.add(UUID.randomUUID());
        File txt = tempDir.resolve("players.txt").toFile();
        File json = tempDir.resolve("players.json").toFile();
        StringBuilder sb = new StringBuilder();
        for (UUID u : expected) {
            sb.append(u).append('\n');
        }
        Files.writeString(txt.toPath(), sb.toString());

        Set<UUID> migrated = PlayerListFileUtil.readLegacyTxt(txt);
        PlayerListFileUtil.writeJson(json, migrated);

        assertEquals(expected, PlayerListFileUtil.readJson(json));
    }
}
