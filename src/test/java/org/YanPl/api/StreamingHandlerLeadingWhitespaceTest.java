package org.YanPl.api;

import org.YanPl.FancyHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 回归测试：部分模型（如 FancyConsole default → agnes-2.5-flash）在正文前会先输出
 * \n 或 \n\n 等空白 delta。若这些空白原样进入 buffer，CLIManager 按行切分后会在
 * 玩家聊天框产生 "◆ " 空行 + 缩进正文。StreamingHandler 应在流起始时裁掉它们。
 */
@DisplayName("StreamingHandler 正文前导空白处理测试")
class StreamingHandlerLeadingWhitespaceTest {

    private StreamingHandler handler;

    @BeforeEach
    void setUp() {
        FancyHelper plugin = Mockito.mock(FancyHelper.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        Player player = Mockito.mock(Player.class);
        when(plugin.getConfigManager()).thenReturn(Mockito.mock(org.YanPl.manager.ConfigManager.class));
        when(plugin.getConfigManager().getApiTimeoutSeconds()).thenReturn(120);
        handler = new StreamingHandler(plugin, player);
    }

    /** 通过真实 SSE 字节流驱动 processStream，返回 onComplete 回调的完整文本。 */
    private String runStream(String sseBody) throws Exception {
        List<String> chunks = new ArrayList<>();
        AtomicReference<String> completed = new AtomicReference<>();
        handler.setOnChunkCallback(chunks::add);
        handler.setOnCompleteCallback(completed::set);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = (HttpResponse<InputStream>) Mockito.mock(HttpResponse.class);
        when(response.body()).thenReturn(new ByteArrayInputStream(sseBody.getBytes(StandardCharsets.UTF_8)));
        handler.processStream(response);
        return completed.get() == null ? "" : completed.get();
    }

    @Test
    @DisplayName("正文前的 \n\n 空白 delta 被丢弃，正文直接从首行开始")
    void testLeadingBlankLineDeltasStripped() throws Exception {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"\\n\"}}]}\n\n"
                   + "data: {\"choices\":[{\"delta\":{\"content\":\"\\n\"}}]}\n\n"
                   + "data: {\"choices\":[{\"delta\":{\"content\":\"你好，我是Fancy。有什么能帮到你的吗？\"}}]}\n\n"
                   + "data: [DONE]\n\n";
        String full = runStream(sse);
        assertEquals("你好，我是Fancy。有什么能帮到你的吗？", full);
        assertFalse(full.startsWith("\n"), "正文不应以换行开头");
        assertFalse(full.startsWith(" "), "正文不应以缩进开头");
    }

    @Test
    @DisplayName("首个 chunk 自带前导空白（\n\n + 缩进）时同样被裁掉")
    void testLeadingWhitespaceInsideFirstChunkStripped() throws Exception {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"\\n\\n    你好，我是Fancy。有什么能帮到你的吗？\"}}]}\n\n"
                   + "data: [DONE]\n\n";
        String full = runStream(sse);
        assertEquals("你好，我是Fancy。有什么能帮到你的吗？", full);
    }

    @Test
    @DisplayName("正文开始后的段落空行不受影响")
    void testInterParagraphBlankLinesPreserved() throws Exception {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"第一段\\n\\n第二段\"}}]}\n\n"
                   + "data: [DONE]\n\n";
        String full = runStream(sse);
        assertEquals("第一段\n\n第二段", full);
    }

    @Test
    @DisplayName("流中段出现的纯空白 chunk 仍按原逻辑进入 buffer（不做全局裁剪）")
    void testMidStreamWhitespaceUnchanged() throws Exception {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"开头\"}}]}\n\n"
                   + "data: {\"choices\":[{\"delta\":{\"content\":\"\\n\\n\"}}]}\n\n"
                   + "data: {\"choices\":[{\"delta\":{\"content\":\"结尾\"}}]}\n\n"
                   + "data: [DONE]\n\n";
        String full = runStream(sse);
        assertEquals("开头\n\n结尾", full);
    }
}
