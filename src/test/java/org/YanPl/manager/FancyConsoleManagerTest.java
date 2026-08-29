package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FancyConsoleManager 状态码分类测试")
class FancyConsoleManagerTest {

    @Test
    @DisplayName("5xx 服务端错误应判定为服务不可用")
    void testServerErrorsAreUnavailable() {
        assertTrue(FancyConsoleManager.isServiceUnavailableStatus(500));
        assertTrue(FancyConsoleManager.isServiceUnavailableStatus(502));
        assertTrue(FancyConsoleManager.isServiceUnavailableStatus(503));
        assertTrue(FancyConsoleManager.isServiceUnavailableStatus(504));
        assertTrue(FancyConsoleManager.isServiceUnavailableStatus(599));
    }

    @Test
    @DisplayName("200 与 4xx 不应判定为服务不可用")
    void testNonServerErrorsAreNotUnavailable() {
        assertFalse(FancyConsoleManager.isServiceUnavailableStatus(200));
        assertFalse(FancyConsoleManager.isServiceUnavailableStatus(400));
        assertFalse(FancyConsoleManager.isServiceUnavailableStatus(401));
        assertFalse(FancyConsoleManager.isServiceUnavailableStatus(403));
        assertFalse(FancyConsoleManager.isServiceUnavailableStatus(404));
        assertFalse(FancyConsoleManager.isServiceUnavailableStatus(429));
    }
}
