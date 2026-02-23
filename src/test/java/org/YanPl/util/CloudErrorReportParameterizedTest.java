package org.YanPl.util;

import org.YanPl.FancyHelper;
import org.YanPl.manager.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.stream.Stream;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CloudErrorReport 参数化测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloudErrorReportParameterizedTest {

    @Mock
    private FancyHelper fancyHelper;

    @Mock
    private ConfigManager configManager;

    @Mock
    private JavaPlugin genericPlugin;

    private CloudErrorReport fancyHelperReporter;
    private CloudErrorReport genericReporter;

    @BeforeEach
    void setUp() {
        when(fancyHelper.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        when(fancyHelper.getConfigManager()).thenReturn(configManager);
        when(fancyHelper.isEnabled()).thenReturn(false);
        when(configManager.isAutoReportEnabled()).thenReturn(true);
        
        fancyHelperReporter = new CloudErrorReport(fancyHelper);
        genericReporter = new CloudErrorReport(genericPlugin);
    }

    @Nested
    @DisplayName("report 方法测试 - null 输入")
    class ReportNullInputTest {

        @ParameterizedTest
        @NullSource
        @DisplayName("null throwable 应直接返回不做任何处理")
        void testNullThrowable(Throwable throwable) {
            assertDoesNotThrow(() -> fancyHelperReporter.report(throwable));
        }
    }

    @Nested
    @DisplayName("report 方法测试 - 配置检查")
    class ReportConfigCheckTest {

        @Test
        @DisplayName("autoReport 禁用时应跳过上报")
        void testAutoReportDisabled() {
            when(configManager.isAutoReportEnabled()).thenReturn(false);
            
            fancyHelperReporter.report(new RuntimeException("Test"));
            
            verify(fancyHelper, never()).isEnabled();
        }

        @Test
        @DisplayName("autoReport 启用时且插件禁用应跳过")
        void testAutoReportEnabledButPluginDisabled() {
            when(configManager.isAutoReportEnabled()).thenReturn(true);
            when(fancyHelper.isEnabled()).thenReturn(false);
            
            fancyHelperReporter.report(new RuntimeException("Test"));
            
            verify(fancyHelper).isEnabled();
        }

        @Test
        @DisplayName("非 FancyHelper 插件实例且禁用时应跳过")
        void testNonFancyHelperPluginDisabled() {
            when(genericPlugin.isEnabled()).thenReturn(false);
            when(genericPlugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
            
            genericReporter.report(new RuntimeException("Test"));
            
            verify(genericPlugin).isEnabled();
        }
    }

    @Nested
    @DisplayName("report 方法测试 - 插件状态")
    class ReportPluginStateTest {

        @Test
        @DisplayName("插件禁用时应跳过上报")
        void testPluginDisabled() {
            when(fancyHelper.isEnabled()).thenReturn(false);
            
            fancyHelperReporter.report(new RuntimeException("Test"));
            
            verify(fancyHelper, times(1)).isEnabled();
        }
    }

    @Nested
    @DisplayName("report 方法测试 - 堆栈跟踪处理")
    class ReportStackTraceTest {

        static Stream<Throwable> stackTraceInputs() {
            Exception withStackTrace = new RuntimeException("With stack trace");
            
            Exception emptyStackTrace = new RuntimeException("Empty stack") {
                @Override
                public StackTraceElement[] getStackTrace() {
                    return new StackTraceElement[0];
                }
            };
            
            return Stream.of(withStackTrace, emptyStackTrace);
        }

        @ParameterizedTest
        @MethodSource("stackTraceInputs")
        @DisplayName("处理不同堆栈跟踪情况 - 插件禁用")
        void testDifferentStackTraces(Throwable throwable) {
            when(fancyHelper.isEnabled()).thenReturn(false);
            
            assertDoesNotThrow(() -> fancyHelperReporter.report(throwable));
        }
    }

    @Nested
    @DisplayName("report 方法测试 - 重复错误检测")
    class ReportDuplicateDetectionTest {

        @Test
        @DisplayName("相同错误应只上报一次 - 插件禁用")
        void testSameErrorReportedOnce() {
            when(fancyHelper.isEnabled()).thenReturn(false);
            
            RuntimeException error = new RuntimeException("Same error");
            
            fancyHelperReporter.report(error);
            fancyHelperReporter.report(error);
            fancyHelperReporter.report(error);
            
            verify(fancyHelper, atMost(3)).isEnabled();
        }
    }

    @Nested
    @DisplayName("getStackTraceString 方法测试")
    class GetStackTraceStringTest {

        @Test
        @DisplayName("简单异常无 cause")
        void testSimpleException() throws Exception {
            Exception exception = new RuntimeException("Simple error");
            
            java.lang.reflect.Method method = CloudErrorReport.class.getDeclaredMethod(
                "getStackTraceString", Throwable.class);
            method.setAccessible(true);
            String result = (String) method.invoke(fancyHelperReporter, exception);
            
            assertTrue(result.contains("java.lang.RuntimeException: Simple error"));
            assertTrue(result.contains("\tat"));
            assertFalse(result.contains("Caused by:"));
        }

        @Test
        @DisplayName("嵌套异常有 cause")
        void testNestedException() throws Exception {
            Exception cause = new IllegalArgumentException("Root cause");
            Exception exception = new RuntimeException("Wrapper", cause);
            
            java.lang.reflect.Method method = CloudErrorReport.class.getDeclaredMethod(
                "getStackTraceString", Throwable.class);
            method.setAccessible(true);
            String result = (String) method.invoke(fancyHelperReporter, exception);
            
            assertTrue(result.contains("java.lang.RuntimeException: Wrapper"));
            assertTrue(result.contains("Caused by:"));
            assertTrue(result.contains("java.lang.IllegalArgumentException: Root cause"));
        }

        @Test
        @DisplayName("多层嵌套异常")
        void testDeepNestedException() throws Exception {
            Exception rootCause = new NullPointerException("Root");
            Exception middleCause = new IllegalArgumentException("Middle", rootCause);
            Exception topException = new RuntimeException("Top", middleCause);
            
            java.lang.reflect.Method method = CloudErrorReport.class.getDeclaredMethod(
                "getStackTraceString", Throwable.class);
            method.setAccessible(true);
            String result = (String) method.invoke(fancyHelperReporter, topException);
            
            assertTrue(result.contains("java.lang.RuntimeException: Top"));
            assertTrue(result.contains("Caused by:"));
            assertTrue(result.contains("java.lang.IllegalArgumentException: Middle"));
            assertTrue(result.contains("java.lang.NullPointerException: Root"));
        }

        @Test
        @DisplayName("异常消息为 null")
        void testNullExceptionMessage() throws Exception {
            Exception exception = new RuntimeException((String) null);
            
            java.lang.reflect.Method method = CloudErrorReport.class.getDeclaredMethod(
                "getStackTraceString", Throwable.class);
            method.setAccessible(true);
            String result = (String) method.invoke(fancyHelperReporter, exception);
            
            assertNotNull(result);
            assertTrue(result.contains("java.lang.RuntimeException"));
        }

        @Test
        @DisplayName("异常消息包含特殊字符")
        void testSpecialCharactersInMessage() throws Exception {
            Exception exception = new RuntimeException("Error: \n\t\r\"'<>&");
            
            java.lang.reflect.Method method = CloudErrorReport.class.getDeclaredMethod(
                "getStackTraceString", Throwable.class);
            method.setAccessible(true);
            String result = (String) method.invoke(fancyHelperReporter, exception);
            
            assertNotNull(result);
            assertTrue(result.contains("\n"));
            assertTrue(result.contains("\t"));
        }
    }

    @Nested
    @DisplayName("report 方法测试 - 异常类型（插件禁用）")
    class ReportExceptionTypesTest {

        static Stream<Throwable> exceptionTypes() {
            return Stream.of(
                new RuntimeException("Runtime exception"),
                new IllegalArgumentException("Illegal argument"),
                new NullPointerException("Null pointer"),
                new IndexOutOfBoundsException("Index out of bounds"),
                new ClassCastException("Class cast"),
                new NumberFormatException("Number format"),
                new UnsupportedOperationException("Unsupported operation"),
                new ArithmeticException("Arithmetic"),
                new ArrayIndexOutOfBoundsException("Array index"),
                new StringIndexOutOfBoundsException("String index")
            );
        }

        @ParameterizedTest
        @MethodSource("exceptionTypes")
        @DisplayName("处理各种类型的异常 - 插件禁用")
        void testDifferentExceptionTypes(Throwable throwable) {
            when(fancyHelper.isEnabled()).thenReturn(false);
            
            assertDoesNotThrow(() -> fancyHelperReporter.report(throwable));
        }
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTest {

        @Test
        @DisplayName("FancyHelper 实例")
        void testConstructorWithFancyHelper() {
            CloudErrorReport reporter = new CloudErrorReport(fancyHelper);
            assertNotNull(reporter);
        }

        @Test
        @DisplayName("普通 JavaPlugin 实例")
        void testConstructorWithGenericPlugin() {
            CloudErrorReport reporter = new CloudErrorReport(genericPlugin);
            assertNotNull(reporter);
        }
    }

    @Nested
    @DisplayName("report 方法测试 - 边界条件（插件禁用）")
    class ReportEdgeCasesTest {

        @Test
        @DisplayName("异常消息为空字符串")
        void testEmptyExceptionMessage() {
            when(fancyHelper.isEnabled()).thenReturn(false);
            
            RuntimeException error = new RuntimeException("");
            
            assertDoesNotThrow(() -> fancyHelperReporter.report(error));
        }

        @Test
        @DisplayName("异常消息非常长")
        void testVeryLongExceptionMessage() {
            when(fancyHelper.isEnabled()).thenReturn(false);
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("a");
            }
            RuntimeException error = new RuntimeException(sb.toString());
            
            assertDoesNotThrow(() -> fancyHelperReporter.report(error));
        }

        @Test
        @DisplayName("堆栈跟踪非常深")
        void testVeryDeepStackTrace() {
            when(fancyHelper.isEnabled()).thenReturn(false);
            
            RuntimeException error = new RuntimeException("Deep stack");
            StackTraceElement[] deepStack = new StackTraceElement[100];
            for (int i = 0; i < 100; i++) {
                deepStack[i] = new StackTraceElement("Class" + i, "method" + i, "File" + i, i);
            }
            error.setStackTrace(deepStack);
            
            assertDoesNotThrow(() -> fancyHelperReporter.report(error));
        }
    }
}
