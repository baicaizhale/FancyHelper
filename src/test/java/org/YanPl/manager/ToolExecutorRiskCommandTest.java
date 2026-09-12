package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("YOLO 风险命令匹配")
class ToolExecutorRiskCommandTest {

    private static final List<String> RISKY = Arrays.asList("op", "deop", "stop", "ban", "kill", "whitelist add");

    @Test
    @DisplayName("普通风险命令命中")
    void plainRiskyCommands() {
        assertTrue(ToolExecutor.isRiskyCommandPublic("op Steve", RISKY));
        assertTrue(ToolExecutor.isRiskyCommandPublic("stop", RISKY));
        assertTrue(ToolExecutor.isRiskyCommandPublic("/kill @e", RISKY));
        assertTrue(ToolExecutor.isRiskyCommandPublic("whitelist add hacker", RISKY));
    }

    @Test
    @DisplayName("命名空间前缀不能绕过风险确认")
    void namespacePrefixedRiskyCommands() {
        assertTrue(ToolExecutor.isRiskyCommandPublic("minecraft:op Steve", RISKY));
        assertTrue(ToolExecutor.isRiskyCommandPublic("bukkit:op Steve", RISKY));
        assertTrue(ToolExecutor.isRiskyCommandPublic("essentials:ban grief", RISKY));
        assertTrue(ToolExecutor.isRiskyCommandPublic("/cmi:stop", RISKY));
        assertTrue(ToolExecutor.isRiskyCommandPublic("bukkit:minecraft:op Steve", RISKY));
    }

    @Test
    @DisplayName("风险命令的连字衍生命令同样命中")
    void derivedPrefixCommands() {
        assertTrue(ToolExecutor.isRiskyCommandPublic("ban-ip 1.2.3.4", RISKY));
        assertTrue(ToolExecutor.isRiskyCommandPublic("minecraft:ban-ip 1.2.3.4", RISKY));
    }

    @Test
    @DisplayName("execute run 的子命令递归检查不受命名空间影响")
    void executeRunRecursion() {
        assertTrue(ToolExecutor.isRiskyCommandPublic("execute as @a run op Steve", RISKY));
        assertTrue(ToolExecutor.isRiskyCommandPublic("execute as @a run bukkit:op Steve", RISKY));
    }

    @Test
    @DisplayName("普通命令不被误判")
    void harmlessCommandsNotFlagged() {
        assertFalse(ToolExecutor.isRiskyCommandPublic("give @p diamond", RISKY));
        assertFalse(ToolExecutor.isRiskyCommandPublic("tp @a 0 64 0", RISKY));
        // 冒号出现在参数里时不是命名空间前缀，不能把参数当命令名剥掉
        assertFalse(ToolExecutor.isRiskyCommandPublic("msg @a hi:op Steve", RISKY));
        assertFalse(ToolExecutor.isRiskyCommandPublic("tellraw @a {\"text\":\"op Steve\"}", RISKY));
    }

    @Test
    @DisplayName("空风险列表不命中")
    void emptyOrNullList() {
        assertFalse(ToolExecutor.isRiskyCommandPublic("op Steve", Collections.emptyList()));
        assertFalse(ToolExecutor.isRiskyCommandPublic("op Steve", null));
    }
}
