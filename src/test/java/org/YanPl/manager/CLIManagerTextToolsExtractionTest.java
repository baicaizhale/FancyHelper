package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CLIManager 文本协议多工具提取测试")
class CLIManagerTextToolsExtractionTest {

    @SuppressWarnings("unchecked")
    private List<String> extractToolCalls(String response) throws Exception {
        Method m = CLIManager.class.getDeclaredMethod("extractToolCalls", String.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, response);
    }

    @Test
    @DisplayName("正文含两个 #run 时全部提取（真实 bug 场景）")
    void testMultipleRunExtracted() throws Exception {
        String resp = "好的，这就给主人**苹果**和**木头**喵。\n"
                + "#run: give @p apple\n"
                + "#run: give @p oak_log";
        List<String> calls = extractToolCalls(resp);
        assertEquals(2, calls.size(), "应提取两个 #run");
        assertEquals("#run:give @p apple", calls.get(0));
        assertEquals("#run:give @p oak_log", calls.get(1));
    }

    @Test
    @DisplayName("正文叙述中的 # 不误触发")
    void testHashInProseIgnored() throws Exception {
        String resp = "这个话题#有点奇怪，比如 #run 不在这里。\n#run: give @p diamond";
        List<String> calls = extractToolCalls(resp);
        assertEquals(1, calls.size());
        assertEquals("#run:give @p diamond", calls.get(0));
    }

    @Test
    @DisplayName("JSON 参数（#todo/#ask）整体提取")
    void testJsonArgExtracted() throws Exception {
        String resp = "先记录任务。\n#todo: [{\"id\":\"1\",\"task\":\"建房子\"}]\n完成";
        List<String> calls = extractToolCalls(resp);
        assertEquals(1, calls.size());
        assertEquals("#todo:[{\"id\":\"1\",\"task\":\"建房子\"}]", calls.get(0));
    }

    @Test
    @DisplayName("混合工具全部提取且保序")
    void testMixedToolsOrdered() throws Exception {
        String resp = "#run: say hello\n#search: minecraft wiki\n#run: give @p apple";
        List<String> calls = extractToolCalls(resp);
        assertEquals(3, calls.size());
        assertEquals("#run:say hello", calls.get(0));
        assertEquals("#search:minecraft wiki", calls.get(1));
        assertEquals("#run:give @p apple", calls.get(2));
    }

    @Test
    @DisplayName("无工具返回空列表")
    void testNoToolsEmpty() throws Exception {
        assertTrue(extractToolCalls("好的喵，今天天气不错。").isEmpty());
        assertTrue(extractToolCalls("").isEmpty());
        assertTrue(extractToolCalls(null).isEmpty());
    }

    @Test
    @DisplayName("思考块不干扰提取")
    void testThoughtStripped() throws Exception {
        String resp = "<thinking>我需要给玩家物品</thinking>\n#run: give @p apple\n#run: give @p oak_log";
        List<String> calls = extractToolCalls(resp);
        assertEquals(2, calls.size());
        assertEquals("#run:give @p apple", calls.get(0));
        assertEquals("#run:give @p oak_log", calls.get(1));
    }
}
