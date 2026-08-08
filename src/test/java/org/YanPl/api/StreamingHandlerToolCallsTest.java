package org.YanPl.api;

import com.google.gson.JsonObject;
import org.YanPl.FancyHelper;
import org.YanPl.model.NativeToolCall;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("StreamingHandler 原生 tool_calls 累加测试")
class StreamingHandlerToolCallsTest {

    private StreamingHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        FancyHelper plugin = Mockito.mock(FancyHelper.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        Player player = Mockito.mock(Player.class);
        when(plugin.getConfigManager()).thenReturn(Mockito.mock(org.YanPl.manager.ConfigManager.class));
        when(plugin.getConfigManager().getApiTimeoutSeconds()).thenReturn(120);
        handler = new StreamingHandler(plugin, player);
    }

    /** 通过反射调用私有 extractTextFromSSE（无需真实 HTTP 流）。 */
    private void feed(String json) throws Exception {
        Method m = StreamingHandler.class.getDeclaredMethod("extractTextFromSSE", String.class);
        m.setAccessible(true);
        m.invoke(handler, json);
    }

    private void finalizeCalls() throws Exception {
        Method m = StreamingHandler.class.getDeclaredMethod("finalizeNativeToolCalls");
        m.setAccessible(true);
        m.invoke(handler);
    }

    @Test
    @DisplayName("跨多个 delta 的 tool_calls 参数片段累加")
    void testAccumulateAcrossDeltas() throws Exception {
        // 首个 delta：id + name
        feed("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"run\",\"arguments\":\"\"}}]}}]}");
        // 参数片段 1
        feed("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"comm\"}}]}}]}");
        // 参数片段 2
        feed("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"and\\\":\\\"say hi\\\"}\"}}]}}]}");

        finalizeCalls();
        List<NativeToolCall> calls = handler.getNativeToolCalls();
        assertEquals(1, calls.size());
        assertEquals("run", calls.get(0).name());
        assertEquals("call_1", calls.get(0).id());
        assertEquals("{\"command\":\"say hi\"}", calls.get(0).argumentsJson());
    }

    @Test
    @DisplayName("content 与 tool_calls 同框时两者都被处理")
    void testContentAndToolCallsTogether() throws Exception {
        feed("{\"choices\":[{\"delta\":{\"content\":\"我先查一下\",\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"search\",\"arguments\":\"{\\\"query\\\":\\\"x\\\"}\"}}]}}]}");
        finalizeCalls();
        List<NativeToolCall> calls = handler.getNativeToolCalls();
        assertEquals(1, calls.size());
        assertEquals("search", calls.get(0).name());
    }

    @Test
    @DisplayName("后续 delta 无 name 也能继续累加（null 容忍）")
    void testNullNameOnLaterDelta() throws Exception {
        feed("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"edit_memory\"}}]}}]}");
        // 后续 delta 无 function.name，只有 arguments
        feed("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"index\\\":1}\"}}]}}]}");
        finalizeCalls();
        List<NativeToolCall> calls = handler.getNativeToolCalls();
        assertEquals(1, calls.size());
        assertEquals("edit_memory", calls.get(0).name());
    }

    @Test
    @DisplayName("无 tool_calls 的普通流返回空列表")
    void testNoToolCalls() throws Exception {
        feed("{\"choices\":[{\"delta\":{\"content\":\"普通回复\"}}]}");
        finalizeCalls();
        assertTrue(handler.getNativeToolCalls().isEmpty());
    }

    @Test
    @DisplayName("两个并行的 tool_calls 按 index 区分")
    void testParallelToolCalls() throws Exception {
        feed("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"search\",\"arguments\":\"{}\"}}]}}]}");
        feed("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":1,\"function\":{\"name\":\"run\",\"arguments\":\"{}\"}}]}}]}");
        finalizeCalls();
        List<NativeToolCall> calls = handler.getNativeToolCalls();
        assertEquals(2, calls.size());
        assertEquals("search", calls.get(0).name());
        assertEquals("run", calls.get(1).name());
    }

    @Test
    @DisplayName("Cloudflare Responses function_call 事件防御性解析")
    void testResponsesFunctionCallEvents() throws Exception {
        feed("{\"type\":\"response.output_item.added\",\"data\":{\"item\":{\"type\":\"function_call\",\"call_id\":\"fc_9\",\"name\":\"webfetch\",\"arguments\":\"{\\\"url\\\"\"}}}");
        feed("{\"type\":\"response.function_call_arguments.delta\",\"data\":{\"output_index\":0,\"delta\":\":\\\"https://x.com\\\"}\"}}");
        finalizeCalls();
        List<NativeToolCall> calls = handler.getNativeToolCalls();
        assertEquals(1, calls.size());
        assertEquals("webfetch", calls.get(0).name());
        assertEquals("fc_9", calls.get(0).id());
    }

    private JsonObject obj(String json) {
        return new com.google.gson.Gson().fromJson(json, JsonObject.class);
    }
}
