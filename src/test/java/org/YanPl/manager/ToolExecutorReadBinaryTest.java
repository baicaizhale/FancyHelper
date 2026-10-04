package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * #read 二进制防护测试。
 * executeReadOperation 是 private 实例方法但读取路径不依赖 plugin 运行态，
 * mock 构造后反射调用，验证：正常 UTF-8 放行；解码失败（GBK/UTF-16 BOM）与
 * 含空字节（SQLite 头/纯零/UTF-16LE）默认拒绝并给出 force 提示；force 模式遮蔽空字节。
 */
@DisplayName("ToolExecutor #read 二进制防护测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ToolExecutorReadBinaryTest {

    @Mock
    private FancyHelper plugin;

    @Mock
    private CLIManager cliManager;

    @Mock
    private ConfigManager configManager;

    private ToolExecutor executor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.getApiTimeoutSeconds()).thenReturn(30);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        executor = new ToolExecutor(plugin, cliManager);
    }

    private void writeBytes(String name, byte[] bytes) throws Exception {
        Files.write(tempDir.resolve(name), bytes);
    }

    private String callRead(String args) throws Exception {
        Method m = ToolExecutor.class.getDeclaredMethod("executeReadOperation", File.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(executor, tempDir.toFile(), args);
    }

    @Test
    @DisplayName("正常 UTF-8（含中文）按行号放行")
    void testReadNormalUtf8() throws Exception {
        writeBytes("config.yml", "你好 world\n第二行\n".getBytes(StandardCharsets.UTF_8));
        String result = callRead("config.yml");
        assertTrue(result.contains("1: 你好 world"), result);
        assertTrue(result.contains("2: 第二行"), result);
    }

    @Test
    @DisplayName("SQLite 文件（头部含 0x00）默认拒绝并提示 force")
    void testReadSqliteHeaderRefused() throws Exception {
        byte[] sqlite = new byte[100];
        byte[] header = "SQLite format 3\u0000".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(header, 0, sqlite, 0, header.length);
        writeBytes("database.db", sqlite);
        String result = callRead("database.db");
        assertTrue(result.contains("空字节"), result);
        assertTrue(result.contains("force"), result);
    }

    @Test
    @DisplayName("纯零文件默认拒绝")
    void testReadZerosRefused() throws Exception {
        writeBytes("blob.bin", new byte[512]);
        String result = callRead("blob.bin");
        assertTrue(result.contains("空字节"), result);
    }

    @Test
    @DisplayName("force 模式：空字节替换为 ␀ 并加污染警示")
    void testReadZerosForce() throws Exception {
        writeBytes("blob.bin", new byte[64]);
        String result = callRead("blob.bin force");
        assertTrue(result.startsWith("⚠"), result);
        assertTrue(result.contains("␀"), result);
        assertTrue(result.contains("1: "), result);
    }

    @Test
    @DisplayName("force 与行号范围可同时使用")
    void testReadForceWithRange() throws Exception {
        writeBytes("mixed.log", "line1\nbin\u0000ary\nline3\n".getBytes(StandardCharsets.UTF_8));
        String result = callRead("mixed.log 1-2 force");
        assertTrue(result.startsWith("⚠"), result);
        assertTrue(result.contains("1: line1"), result);
        assertTrue(result.contains("2: bin␀ary"), result);
        assertFalse(result.contains("line3"), result);
    }

    @Test
    @DisplayName("混有空字节的文本不加 force 时拒绝")
    void testReadMixedWithoutForceRefused() throws Exception {
        writeBytes("mixed.log", "line1\nbin\u0000ary\nline3\n".getBytes(StandardCharsets.UTF_8));
        String result = callRead("mixed.log");
        assertTrue(result.contains("空字节"), result);
        assertFalse(result.contains("line1"), result);
    }

    @Test
    @DisplayName("GBK 编码文件（解码失败）拒绝并说明非 UTF-8")
    void testReadGbkRefused() throws Exception {
        // "你好" 的 GBK 字节：C4 E3 BA C3
        writeBytes("gbk.log", new byte[]{(byte) 0xC4, (byte) 0xE3, (byte) 0xBA, (byte) 0xC3});
        String result = callRead("gbk.log");
        assertTrue(result.contains("UTF-8"), result);
    }

    @Test
    @DisplayName("UTF-16LE 文本（无 BOM，隔字节 0x00）被空字节规则拦截")
    void testReadUtf16LeRefusedByNul() throws Exception {
        writeBytes("utf16.txt", new byte[]{0x68, 0x00, 0x69, 0x00});
        String result = callRead("utf16.txt");
        assertTrue(result.contains("空字节"), result);
    }

    @Test
    @DisplayName("UTF-16 BOM（FF FE）解码失败，按非 UTF-8 拒绝")
    void testReadUtf16BomRefusedByDecode() throws Exception {
        writeBytes("utf16bom.txt", new byte[]{(byte) 0xFF, (byte) 0xFE, 0x68, 0x00, 0x69, 0x00});
        String result = callRead("utf16bom.txt");
        assertTrue(result.contains("UTF-8"), result);
    }

    @Test
    @DisplayName("空文件正常返回空内容")
    void testReadEmptyFile() throws Exception {
        writeBytes("empty.txt", new byte[0]);
        assertEquals("", callRead("empty.txt"));
    }

    @Test
    @DisplayName("decodeStrictUtf8：合法 UTF-8 原样返回，GBK 判 null，纯零非 null（走空字节规则）")
    void testDecodeStrictUtf8() {
        assertEquals("a\nb", ToolExecutor.decodeStrictUtf8("a\nb".getBytes(StandardCharsets.UTF_8)));
        assertNull(ToolExecutor.decodeStrictUtf8(new byte[]{(byte) 0xC4, (byte) 0xE3, (byte) 0xBA, (byte) 0xC3}));
        String zeros = ToolExecutor.decodeStrictUtf8(new byte[4]);
        assertNotNull(zeros);
        assertTrue(zeros.indexOf('\u0000') >= 0);
    }
}
