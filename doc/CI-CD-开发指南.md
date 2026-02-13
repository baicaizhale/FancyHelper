# FancyHelper CI/CD 开发指南

本文档面向 FancyHelper 项目的所有开发者，帮助你理解项目的自动化构建流程，让你在本地开发时也能跑通同样的检查。

---

## 目录

- [什么是 CI/CD？为什么要用？](#什么是-cicd为什么要用)
- [本地开发环境配置](#本地开发环境配置)
- [测试框架使用指南](#测试框架使用指南)
- [CI/CD 工作流详解](#cicd-工作流详解)
- [常见问题解答](#常见问题解答)

---

## 什么是 CI/CD？为什么要用？

**CI（持续集成）**：每次代码提交后，自动运行测试和检查，确保新代码没有破坏现有功能。

**CD（持续部署）**：测试通过后，自动构建并发布新版本。

### 好处是什么？

| 问题 | 没有 CI/CD | 有 CI/CD |
|------|-----------|---------|
| 代码合并后突然崩了 | 测了好几天才发现 | 合并时立刻发现 |
| 测试覆盖率低 | 不知道哪些代码没测过 | 自动统计，一目了然 |
| 发布新版本 | 手动打包、手动上传 | 打个标签，全自动发布 |
| 代码有安全漏洞 | 被黑客攻击了才知道 | 每次构建都扫描依赖 |

### FancyHelper 使用了哪些工具？

| 工具 | 作用 | 类比 |
|------|------|------|
| JUnit 5 | 单元测试框架 | 考试出题系统 |
| Mockito | 模拟依赖对象 | 考试时的"道具" |
| JaCoCo | 统计测试覆盖率 | 考试成绩统计 |
| OWASP Dependency-Check | 扫描依赖漏洞 | 安检扫描仪 |

---

## 本地开发环境配置

### 准备工作

你需要安装以下软件：

| 软件 | 版本要求 | 如何检查 |
|------|---------|---------|
| JDK | 17 或 21 | `java -version` |
| Maven | 3.8+ | `mvn -version` |

### 安装 JDK

**Linux/Mac:**
```bash
# 使用 SDKMAN 安装（推荐）
curl -s "https://get.sdkman.io" | bash
sdk install java 17-tem
```

**Windows:**
1. 下载 [Temurin JDK 17](https://adoptium.net/)
2. 安装并配置环境变量 `JAVA_HOME`

### 安装 Maven

**Linux/Mac:**
```bash
# 使用包管理器
# Ubuntu/Debian
sudo apt install maven

# Mac
brew install maven
```

**Windows:**
1. 下载 [Maven](https://maven.apache.org/download.cgi)
2. 解压并配置环境变量

### IDEA 配置（推荐）

1. 打开 IntelliJ IDEA
2. 选择 `File → Open`，选中项目根目录
3. 等待 Maven 自动导入依赖
4. 确认 SDK 设置：`File → Project Structure → Project SDK` 选择 Java 17

---

## 测试框架使用指南

### 为什么写测试？

想象一下：你修改了一个小功能，结果不小心把其他功能弄坏了。如果没有测试，你可能要等用户投诉才发现。有了测试，每次改代码都能立刻知道有没有搞砸。

### JUnit 5 基础用法

JUnit 5 是 Java 最流行的测试框架，用它写测试就像给代码"出考题"。

#### 一个简单的测试类

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {

    @Test
    @DisplayName("加法应该正确计算")  // 测试名称，更易读
    void testAdd() {
        Calculator calc = new Calculator();
        int result = calc.add(2, 3);
        
        // 断言：验证结果是否符合预期
        assertEquals(5, result, "2 + 3 应该等于 5");
    }
    
    @Test
    @DisplayName("除以零应该抛出异常")
    void testDivideByZero() {
        Calculator calc = new Calculator();
        
        // 断言：验证是否抛出预期的异常
        assertThrows(ArithmeticException.class, () -> {
            calc.divide(1, 0);
        });
    }
}
```

#### 常用断言方法

| 方法 | 含义 | 示例 |
|------|------|------|
| `assertEquals(expected, actual)` | 验证相等 | `assertEquals(5, result)` |
| `assertTrue(condition)` | 验证为真 | `assertTrue(list.isEmpty())` |
| `assertFalse(condition)` | 验证为假 | `assertFalse(hasError)` |
| `assertNull(value)` | 验证为 null | `assertNull(result)` |
| `assertNotNull(value)` | 验证非 null | `assertNotNull(user)` |
| `assertThrows(异常类, 代码块)` | 验证抛出异常 | 见上例 |

#### 参数化测试：一次测试多组数据

如果要测试同一个方法的多种输入，可以用参数化测试，避免重复写多个测试方法：

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TodoItemTest {

    // 用 CSV 格式提供多组数据
    @ParameterizedTest
    @CsvSource({
        "pending, PENDING",
        "in_progress, IN_PROGRESS", 
        "completed, COMPLETED",
        "cancelled, CANCELLED"
    })
    @DisplayName("状态字符串应该正确解析")
    void testStatusParsing(String input, TodoItem.Status expected) {
        TodoItem.Status result = TodoItem.Status.fromString(input);
        assertEquals(expected, result);
    }
    
    // 测试一组相同的输入
    @ParameterizedTest
    @ValueSource(strings = {"pending", "in_progress", "completed"})
    @DisplayName("有效的状态字符串不应抛异常")
    void testValidStatuses(String status) {
        assertDoesNotThrow(() -> TodoItem.Status.fromString(status));
    }
}
```

### Mockito：模拟依赖对象

#### 什么是 Mock？

假设你要测试一个"用户服务"，它依赖"数据库访问层"。但测试时你不想真的连数据库，这时就可以用 Mockito 创建一个"假"的数据库访问层，让测试更简单、更快速。

#### Mock 使用示例

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)  // 启用 Mockito
class UserServiceTest {

    @Mock  // 自动创建一个假的 UserRepository
    private UserRepository userRepository;
    
    @Test
    @DisplayName("根据ID查询用户")
    void testFindById() {
        // 1. 准备：设置 mock 对象的行为
        User mockUser = new User(1L, "张三");
        when(userRepository.findById(1L)).thenReturn(mockUser);
        
        // 2. 执行：调用要测试的方法
        UserService service = new UserService(userRepository);
        User result = service.findById(1L);
        
        // 3. 验证：检查结果
        assertEquals("张三", result.getName());
        
        // 4. 验证：确认 mock 方法被调用了
        verify(userRepository, times(1)).findById(1L);
    }
    
    @Test
    @DisplayName("用户不存在时返回空")
    void testFindByIdNotFound() {
        // 设置：返回空
        when(userRepository.findById(999L)).thenReturn(null);
        
        UserService service = new UserService(userRepository);
        User result = service.findById(999L);
        
        assertNull(result);
    }
}
```

#### 常用 Mockito 方法

| 方法 | 用途 |
|------|------|
| `when(mock.方法()).thenReturn(值)` | 设置 mock 返回值 |
| `when(mock.方法()).thenThrow(异常)` | 设置 mock 抛出异常 |
| `verify(mock).方法()` | 验证方法被调用过 |
| `verify(mock, times(n)).方法()` | 验证方法被调用了 n 次 |
| `verify(mock, never()).方法()` | 验证方法从未被调用 |

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行指定的测试类
mvn test -Dtest=TodoItemTest

# 运行指定的测试方法
mvn test -Dtest=TodoItemTest#testStatusParsing

# 跳过测试（紧急发布时使用）
mvn package -DskipTests
```

### JaCoCo：测试覆盖率统计

#### 什么是覆盖率？

覆盖率 = 被测试代码执行过的行数 / 总代码行数

覆盖率越高，说明测试越充分，遗漏 bug 的可能性越小。

#### 如何查看覆盖率报告

```bash
# 运行测试并生成覆盖率报告
mvn test

# 报告位置
# 打开这个文件查看详细报告
target/site/jacoco/index.html
```

#### 覆盖率报告解读

- **绿色行**：已测试覆盖
- **红色行**：未覆盖
- **黄色行**：部分覆盖（如 if 分支只测了一半）

#### 覆盖率目标

| 指标 | 目标值 | 含义 |
|------|--------|------|
| 行覆盖率 | ≥ 70% | 每 100 行代码至少有 70 行被测试执行过 |
| 分支覆盖率 | ≥ 60% | if/switch 分支至少 60% 被测试到 |
| 类覆盖率 | ≥ 80% | 至少 80% 的类有对应的测试 |

---

## CI/CD 工作流详解

### 整体流程图

```
代码提交 → 单元测试 → 安全扫描 → 构建发布
    │          │          │          │
    │          ↓          ↓          ↓
    │        JUnit5     OWASP      打包JAR
    │          +          +          +
    │       JaCoCo     漏洞扫描    发布Release
    │
    └── 如果任意一步失败，后续步骤不会执行
```

### 三个阶段详解

#### 阶段一：单元测试（test）

**测试矩阵**：同时测试 Java 17 和 Java 21 两个版本，确保代码在两个版本下都能正常运行

**产出**：
- 测试报告（详细到每个测试用例的执行结果）
- 覆盖率报告（JaCoCo 生成的 HTML 报告）

**如果失败怎么办？**
1. 在 GitHub Actions 页面下载产物（test-report-java17 或 test-report-java21）
2. 查看具体哪个测试用例失败了
3. 本地复现：`mvn test -Dtest=失败的测试类名`

#### 阶段二：安全扫描（security）

**扫描内容**：使用 OWASP Dependency-Check 检查项目依赖的第三方库是否有已知安全漏洞（CVE）

**产出**：依赖安全报告（dependency-check-report），包含：
- 发现的漏洞数量和严重等级
- 受影响的依赖包名称和版本
- 漏洞详情和修复建议

**发现漏洞怎么办？**
1. 下载报告，确定是哪个依赖有问题
2. 升级到修复了漏洞的新版本
3. 如果暂时无法升级，评估风险并记录在文档中

#### 阶段三：构建发布（build）

**只有在前面两个阶段都通过后才会执行**

**构建类型**：

| 触发方式 | 构建类型 | 版本号 | 说明 |
|---------|---------|--------|------|
| PR/日常提交 | 快照包 | #abc123d | 用 commit SHA 做版本号，用于测试 |
| 推送 v 开头的 Tag | 正式包 | 标签版本号 | 自动发布 GitHub Release |
| 手动触发 snapshot | 快照包 | #abc123d | 用于测试 |
| 手动触发 release | 正式包 | 自定义版本号 | 用于紧急发布 |

### 手动触发工作流

在 GitHub 仓库页面，点击 `Actions` 标签页，选择 `CI-Build & Release`，点击 `Run workflow`：

| 参数 | 说明 |
|------|------|
| build_type | `snapshot`（测试包）或 `release`（正式包） |
| custom_version | 仅 release 生效，自定义版本号如 `3.4.0` |
| skip_checks | 勾选后跳过所有检查（仅用于紧急发布！） |

### 发布正式版本

#### 方法一：打 Tag 自动发布（推荐）

```bash
# 1. 确保代码在 master 分支
git checkout master
git pull

# 2. 创建版本标签
git tag v3.4.0

# 3. 推送标签到远程
git push origin v3.4.0

# 4. 等待 CI/CD 自动完成构建和发布
```

#### 方法二：手动触发

1. 进入 GitHub Actions 页面
2. 选择 `CI-Build & Release`
3. 点击 `Run workflow`
4. 选择 `build_type: release`
5. 填写版本号如 `3.4.0`
6. 点击运行

---

## 常见问题解答

### Q1: 本地测试通过了，CI 上测试失败了？

**可能原因**：
1. 本地代码没有全部提交
2. 本地有缓存，CI 是全新环境
3. 环境变量不同

**解决方法**：
```bash
# 清理并重新测试
mvn clean test
```

### Q2: 如何添加新的测试类？

1. 在 `src/test/java/org/YanPl/` 下创建测试类
2. 类名建议以 `Test` 结尾，如 `UserServiceTest`
3. 使用 `@Test` 注解标记测试方法
4. 运行 `mvn test` 验证

```java
// 测试类模板
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class MyNewTest {
    
    @Test
    @DisplayName("测试描述")
    void testSomething() {
        // 准备
        
        // 执行
        
        // 验证
    }
}
```

### Q3: 覆盖率太低怎么办？

1. 查看覆盖率报告：`target/site/jacoco/index.html`
2. 找到红色标记的未覆盖代码
3. 为这些代码编写测试用例
4. 重点覆盖：
   - 业务核心逻辑
   - 边界条件（空值、极端值）
   - 异常处理分支

### Q4: 如何在本地完整模拟 CI 检查？

```bash
# 运行完整的 CI 检查流程
mvn clean test
```

### Q5: CI 工作流失败如何排查？

1. 进入 GitHub Actions 页面
2. 点击失败的工作流运行记录
3. 展开失败的步骤查看日志
4. 下载产物（测试报告、安全报告）查看详情
5. 本地复现并修复

### Q6: 紧急发布时如何跳过检查？

⚠️ **警告**：跳过检查有风险，仅在紧急情况下使用

**方法**：手动触发时勾选 `skip_checks`

---

## 快速参考卡片

### 常用命令

| 命令 | 作用 |
|------|------|
| `mvn test` | 运行所有测试 |
| `mvn test -Dtest=类名` | 运行指定测试类 |
| `mvn clean package` | 清理并打包 |

### 报告位置

| 报告 | 文件路径 |
|------|---------|
| 测试报告 | `target/surefire-reports/` |
| 覆盖率报告 | `target/site/jacoco/index.html` |

### 覆盖率目标

- 行覆盖率 ≥ 70%
- 分支覆盖率 ≥ 60%
- 类覆盖率 ≥ 80%

---

## 参考资料

- [JUnit 5 用户指南](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito 文档](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [JaCoCo 官方文档](https://www.jacoco.org/jacoco/trunk/doc/)
- [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/)

---

**文档版本**: 3.0  
**最后更新**: 2026-02-13  
**维护者**: FancyHelper 开发团队