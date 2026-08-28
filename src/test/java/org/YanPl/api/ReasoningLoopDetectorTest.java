package org.YanPl.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReasoningLoopDetector 思考循环重复检测")
class ReasoningLoopDetectorTest {

    @Test
    @DisplayName("null / 空 / 过短输入不判为循环")
    void testNullAndTooShort() {
        assertNull(ReasoningLoopDetector.detect(null));
        assertNull(ReasoningLoopDetector.detect(""));
        assertNull(ReasoningLoopDetector.detect("短内容"));
    }

    @Test
    @DisplayName("正常（非重复）长思考不判为循环")
    void testNormalLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("步骤").append(i).append("：检查状态，继续推理；");
        }
        assertNull(ReasoningLoopDetector.detect(sb.toString()));
    }

    @Test
    @DisplayName("尾部同一单元连续重复 3 次判为循环并返回该单元")
    void testDetectsRepeatingTail() {
        String unit = "let me think again carefully";
        assertTrue(unit.length() >= ReasoningLoopDetector.MIN_UNIT_LEN,
                "测试单元长度应不低于 MIN_UNIT_LEN");
        String s = unit + unit + unit;
        assertEquals(unit, ReasoningLoopDetector.detect(s));
    }

    @Test
    @DisplayName("循环前存在其它思考内容仍能检测到尾部重复")
    void testDetectsRepeatingTailAfterPrefix() {
        String unit = "ABCDEFGHIJKLMNOPQRST"; // 20 字符
        String prefix = "这是很长的一段前置思考内容，与循环无关。";
        String s = prefix + unit + unit + unit;
        assertEquals(unit, ReasoningLoopDetector.detect(s));
    }

    @Test
    @DisplayName("只重复 2 次不判为循环")
    void testTwoRepeatsNotDetected() {
        String unit = "ABCDEFGHIJKLMNOPQRST";
        assertNull(ReasoningLoopDetector.detect(unit + unit));
    }

    @Test
    @DisplayName("重复单元短于阈值不判为循环")
    void testShortUnitNotDetected() {
        String unit = "好的"; // 2 字符 < MIN_UNIT_LEN
        assertNull(ReasoningLoopDetector.detect(unit + unit + unit + unit + unit));
    }

    @Test
    @DisplayName("超过扫描窗口的长思考，尾部循环仍可检测")
    void testLoopAtTailBeyondScanWindow() {
        String unit = "ZYXWVUTSRQPONMLKJIHG"; // 20 字符
        StringBuilder prefix = new StringBuilder();
        // 构造超过 SCAN_WINDOW 的非重复前置内容
        while (prefix.length() < ReasoningLoopDetector.SCAN_WINDOW + 100) {
            prefix.append("前置思考内容编号").append(prefix.length()).append('；');
        }
        String s = prefix + unit + unit + unit;
        assertEquals(unit, ReasoningLoopDetector.detect(s));
    }
}
