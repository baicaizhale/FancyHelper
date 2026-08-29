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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * #edit / #write 的 JSON 行格式解析测试。
 * computeEdit / executeWriteOperation 是 private 实例方法但内部不依赖 plugin 状态，
 * 这里 mock 构造 ToolExecutor 后反射调用，验证 JSON 行格式（推荐）与旧 | 分隔格式（兼容）都能工作。
 */
@DisplayName("ToolExecutor 文件工具 JSON 行格式测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ToolExecutorFileFormatTest {

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

    private File write(String name, String content) throws Exception {
        File f = tempDir.resolve(name).toFile();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private String read(String name) throws Exception {
        return Files.readString(tempDir.resolve(name), StandardCharsets.UTF_8);
    }

    private Object callEdit(String args) throws Exception {
        return callEdit(args, true);
    }

    private Object callEdit(String args, boolean writeToDisk) throws Exception {
        Method m = ToolExecutor.class.getDeclaredMethod("computeEdit", File.class, String.class, boolean.class);
        m.setAccessible(true);
        return m.invoke(executor, tempDir.toFile(), args, writeToDisk);
    }

    private boolean editSuccess(Object preview) throws Exception {
        Field f = preview.getClass().getDeclaredField("success");
        f.setAccessible(true);
        return f.getBoolean(preview);
    }

    private String editResult(Object preview) throws Exception {
        Field f = preview.getClass().getDeclaredField("result");
        f.setAccessible(true);
        return (String) f.get(preview);
    }

    private String callWrite(String args) throws Exception {
        Method m = ToolExecutor.class.getDeclaredMethod("executeWriteOperation", File.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(executor, tempDir.toFile(), args);
    }

    @Test
    @DisplayName("#edit JSON 行：基础替换成功")
    void testEditJsonLine() throws Exception {
        write("config.yml", "enabled: true\nsetting: old\n");
        Object preview = callEdit("{\"path\":\"config.yml\",\"original\":\"setting: old\",\"replacement\":\"setting: new\"}");
        assertTrue(editSuccess(preview), editResult(preview));
        assertTrue(read("config.yml").contains("setting: new"));
    }

    @Test
    @DisplayName("#edit JSON 行：range 省略时自动搜索")
    void testEditJsonLineAutoRange() throws Exception {
        write("config.yml", "a: 1\nb: 2\n");
        Object preview = callEdit("{\"path\":\"config.yml\",\"original\":\"b: 2\",\"replacement\":\"b: 20\"}");
        assertTrue(editSuccess(preview), editResult(preview));
        assertTrue(read("config.yml").contains("b: 20"));
    }

    @Test
    @DisplayName("#edit JSON 行：带 range 且含换行替换")
    void testEditJsonLineWithRangeAndNewline() throws Exception {
        write("config.yml", "line1\nline2\nline3\n");
        Object preview = callEdit("{\"path\":\"config.yml\",\"range\":\"2-2\",\"original\":\"line2\",\"replacement\":\"line2-modified\"}");
        assertTrue(editSuccess(preview), editResult(preview));
        // Files.write(Iterable) 每个元素后追加平台行分隔符（Windows 为 \r\n，含末尾），
        // 这是既有写盘行为（与旧格式一致），断言时归一化行尾
        assertEquals("line1\nline2-modified\nline3", read("config.yml").replace("\r", "").replaceAll("\\n+$", ""));
    }

    @Test
    @DisplayName("#edit JSON 行：缺失 path 报错")
    void testEditJsonMissingPath() throws Exception {
        write("config.yml", "a: 1\n");
        Object preview = callEdit("{\"original\":\"a\",\"replacement\":\"b\"}");
        assertFalse(editSuccess(preview));
        assertTrue(editResult(preview).contains("缺少 path"));
    }

    @Test
    @DisplayName("#edit 旧格式兼容：path|original|replacement")
    void testEditLegacyFormat() throws Exception {
        write("config.yml", "enabled: true\n");
        Object preview = callEdit("config.yml|enabled: true|enabled: false");
        assertTrue(editSuccess(preview), editResult(preview));
        assertTrue(read("config.yml").contains("enabled: false"));
    }

    @Test
    @DisplayName("#write JSON 行：content 含真实换行")
    void testWriteJsonLine() throws Exception {
        String result = callWrite("{\"path\":\"new.yml\",\"content\":\"a: 1\\nb: 2\\n\"}");
        assertTrue(result.startsWith("成功写入文件"), result);
        assertEquals("a: 1\nb: 2\n", read("new.yml"));
    }

    @Test
    @DisplayName("#write JSON 行：content 含字面 \\n（JSON 转义）")
    void testWriteJsonLineLiteralBackslashN() throws Exception {
        String result = callWrite("{\"path\":\"esc.yml\",\"content\":\"a\\\\nb\"}");
        assertTrue(result.startsWith("成功写入文件"), result);
        // JSON 中 \\n 表示字面反斜杠+n
        assertEquals("a\\nb", read("esc.yml"));
    }

    @Test
    @DisplayName("#write 旧格式兼容：path|content + \\n 转义")
    void testWriteLegacyFormat() throws Exception {
        String result = callWrite("old.yml|enabled: true\\nsetting: value");
        assertTrue(result.startsWith("成功写入文件"), result);
        assertEquals("enabled: true\nsetting: value", read("old.yml"));
    }

    @Test
    @DisplayName("#write JSON 行：缺失 path 报错")
    void testWriteJsonMissingPath() throws Exception {
        String result = callWrite("{\"content\":\"x\"}");
        assertTrue(result.contains("缺少 path"), result);
    }

    @Test
    @DisplayName("extractFilePathFromArgs：JSON 行与旧格式都能提取路径")
    void testExtractFilePath() throws Exception {
        Method m = ToolExecutor.class.getDeclaredMethod("extractFilePathFromArgs", String.class);
        m.setAccessible(true);
        assertEquals("config.yml", m.invoke(executor, "{\"path\":\"config.yml\",\"content\":\"x\"}"));
        assertEquals("config.yml", m.invoke(executor, "config.yml|enabled: true"));
        assertEquals("config.yml", m.invoke(executor, "config.yml"));
    }

    private String callValidateYaml(String type, String args) throws Exception {
        Method m = ToolExecutor.class.getDeclaredMethod("validateYamlSyntax", File.class, String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(executor, tempDir.toFile(), type, args);
    }

    @Test
    @DisplayName("validateYamlSyntax：合法 YAML 写入返回 null（放行）")
    void testValidateYamlWriteValid() throws Exception {
        assertNull(callValidateYaml("write", "{\"path\":\"a.yml\",\"content\":\"a: 1\\nb: 2\\n\"}"));
    }

    @Test
    @DisplayName("validateYamlSyntax：含未转义双引号的 YAML 写入返回错误（拒绝）")
    void testValidateYamlWriteBrokenQuote() throws Exception {
        String err = callValidateYaml("write", "{\"path\":\"a.yml\",\"content\":\"key: \\\"值含\\\"引号\"}");
        assertNotNull(err);
    }

    @Test
    @DisplayName("validateYamlSyntax：非 yml/yaml 目标跳过校验返回 null")
    void testValidateYamlSkipsNonYaml() throws Exception {
        assertNull(callValidateYaml("write", "{\"path\":\"a.txt\",\"content\":\"anything {not yaml}\"}"));
    }

    @Test
    @DisplayName("validateYamlSyntax：edit dry-run 后内容非法 YAML 返回错误")
    void testValidateYamlEditBroken() throws Exception {
        write("config.yml", "enabled: true\n");
        // edit 将 enabled: true 替换为未闭合双引号的内容，dry-run 后应识别为非法 YAML
        String err = callValidateYaml("edit", "{\"path\":\"config.yml\",\"original\":\"enabled: true\",\"replacement\":\"enabled: \\\"坏\"}");
        assertNotNull(err);
    }

    @Test
    @DisplayName("validateYamlSyntax：edit dry-run 后内容合法 YAML 返回 null")
    void testValidateYamlEditValid() throws Exception {
        write("config.yml", "enabled: true\n");
        assertNull(callValidateYaml("edit", "{\"path\":\"config.yml\",\"original\":\"enabled: true\",\"replacement\":\"enabled: false\"}"));
    }
}
