package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EULA 管理器：负责管理和实时监控 eula.txt 文件。
 */
public class EulaManager {
    private final FancyHelper plugin;
    private final File eulaFile;
    private final File licenseFile;
    private final List<String> eulaContent;
    private final List<String> licenseContent;
    private final AtomicBoolean isEulaValid = new AtomicBoolean(true);
    private final AtomicBoolean isLicenseValid = new AtomicBoolean(true);
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = true;

    /**
     * 初始化 EULA 管理器。
     * 
     * @param plugin 插件实例
     */
    public EulaManager(FancyHelper plugin) {
        this.plugin = plugin;
        File readmeDir = new File(plugin.getDataFolder(), "README");
        if (!readmeDir.exists()) {
            readmeDir.mkdirs();
        }
        this.eulaFile = new File(readmeDir, "eula.txt");
        this.licenseFile = new File(readmeDir, "license.txt");
        
        this.eulaContent = Arrays.asList(
            "==============================================",
            "  FancyHelper 最终用户许可协议 (EULA)",
            "==============================================",
            "",
            "本最终用户许可协议（下称\"本协议\"）是您（下称\"用户\"",
            "或\"您\"）与 FancyHelper 开发者（下称\"开发者\"）之间，",
            "就 FancyHelper 插件（下称\"本插件\"）的安装、使用所",
            "订立的具有法律约束力的合同。",
            "",
            "==============================================",
            "  第一条  定义",
            "==============================================",
            "1.1 \"本插件\"指 FancyHelper 软件及其全部组件、文档、",
            "    配置文件及后续更新、补丁。",
            "1.2 \"第三方服务\"指本插件集成或调用的外部人工智能、",
            "    搜索及其他技术服务。",
            "1.3 \"用户\"指安装、配置、使用本插件的任何个人或组织，",
            "    包括服务器所有者、管理员及经授权的使用者。",
            "",
            "==============================================",
            "  第二条  工具属性与责任切割",
            "==============================================",
            "2.1 本插件仅为辅助性技术工具，开发者不参与、不介入、",
            "    不控制用户的具体服务器运营行为，亦不对用户的实际",
            "    管理决策承担任何责任。",
            "2.2 本插件提供的所有生成内容、操作建议或执行路径，",
            "    均需经用户明确确认后方可生效。用户保留对任何操作",
            "    的最终否决权与决定权。",
            "2.3 用户明确知悉，本插件涉及服务器指令生成、文件访问",
            "    及配置调整等高风险功能，自愿承担由此产生的全部",
            "    后果。",
            "",
            "==============================================",
            "  第三条  第三方服务",
            "==============================================",
            "3.1 本插件依赖的第三方服务由独立主体运营，开发者与其",
            "    无关联关系，亦非其控制者或代理方。",
            "3.2 用户通过本插件传输的数据可能由第三方服务处理，",
            "    相关行为受该第三方独立条款约束。开发者不控制其",
            "    数据实践，亦不对其服务中断、政策变更或数据事件",
            "    负责。",
            "3.3 开发者不直接收集、存储或控制用户服务器数据。用户",
            "    作为服务器运营者，对全部数据处理活动承担首要责任。",
            "    开发者仅在司法或行政机关最终认定的范围内承担无法",
            "    通过协议免除的法定责任。",
            "",
            "==============================================",
            "  第四条  AI 生成内容",
            "==============================================",
            "4.1 AI 生成内容由第三方算法自动产出，不代表开发者",
            "    立场。开发者不对其真实性、准确性、合法性、非侵权",
            "    性或适用性作任何保证。",
            "4.2 用户应在依赖任何生成内容前独立判断，不得将其作为",
            "    专业建议的替代。因使用或信赖该内容引发的任何争议",
            "    或损失，由用户自行承担。",
            "",
            "==============================================",
            "  第五条  无担保声明",
            "==============================================",
            "5.1 本插件按\"原样\"及\"可用\"状态提供，开发者不提供",
            "    任何明示或默示担保。",
            "5.2 开发者不保证本插件无错误、无中断、绝对安全或与",
            "    特定环境兼容。使用本插件的全部风险由用户自行承担。",
            "",
            "==============================================",
            "  第六条  责任限制",
            "==============================================",
            "6.1 在法律允许的最大范围内，开发者对因本协议、本插件、",
            "    第三方服务或生成内容引起的任何损害不承担责任，",
            "    无论基于何种法律理论，即便已被告知可能性。",
            "6.2 开发者对用户或第三方的总赔偿责任，以用户就使用",
            "    本插件已实际支付的金额为限；本插件免费提供，该",
            "    金额为零。若前述限制被有权机关认定为无效，赔偿",
            "    总额不超过人民币壹佰元整。",
            "6.3 本条规定不影响因开发者故意或重大过失造成的人身",
            "    损害所应承担的法定责任。",
            "",
            "==============================================",
            "  第七条  用户义务与合规",
            "==============================================",
            "7.1 用户承诺仅将本插件用于合法目的，遵守适用法律法规",
            "    及服务器平台规则。",
            "7.2 用户不得利用本插件从事违法、侵权或危害服务器安全",
            "    的行为，亦不得将本插件用于任何未经授权的数据获取",
            "    或系统干扰。",
            "7.3 用户对通过其服务器使用本插件的所有人员行为承担",
            "    全部责任。",
            "",
            "==============================================",
            "  第八条  数据与隐私",
            "==============================================",
            "8.1 本插件可能在服务器本地存储运行日志等信息，用户应",
            "    自行采取安全措施保护此类数据。",
            "8.2 开发者无义务亦无技术能力访问、删除或迁移用户服务",
            "    器上的数据。",
            "8.3 本插件可能包含用于改进产品的可选运行信息反馈功能，",
            "    用户可自行启用或关闭。",
            "",
            "==============================================",
            "  第九条  知识产权",
            "==============================================",
            "9.1 本插件的源代码根据 GNU General Public License",
            "    v3.0 (GPL-3.0) 发布，用户对源代码的权利受该许可证",
            "    保护。",
            "9.2 本协议中的使用限制、免责及责任限制条款，仅适用于",
            "    本插件运行时的功能使用，不影响 GPL-3.0 赋予用户的",
            "    源代码相关权利。",
            "9.3 AI 生成内容的知识产权归属依相关法律确定，开发者",
            "    对此不作保证，相关风险由用户承担。",
            "",
            "==============================================",
            "  第十条  协议修改",
            "==============================================",
            "10.1 开发者保留修改本协议的权利，修改后的条款随新版",
            "     插件分发。",
            "10.2 用户继续使用本插件即视为接受修改后的协议。若",
            "     不同意，应停止使用并删除本插件。",
            "",
            "==============================================",
            "  第十一条  期限与终止",
            "==============================================",
            "11.1 本协议自首次使用起生效，至删除本插件时终止。",
            "11.2 依其性质应存续的条款在终止后继续有效。",
            "",
            "==============================================",
            "  第十二条  争议解决",
            "==============================================",
            "12.1 本协议适用中华人民共和国法律。",
            "12.2 因本协议产生的争议，应协商解决；协商不成的，",
            "     提交开发者所在地有管辖权的人民法院诉讼解决。",
            "",
            "==============================================",
            "  第十三条  其他",
            "==============================================",
            "13.1 若本协议任何条款被认定无效，其余条款继续有效。",
            "13.2 本协议构成双方就本主题事项的完整协议。",
            "13.3 用户声明已具备订立合同的法定资格，并已充分理解",
            "     本协议内容。",
            "",
            "==============================================",
            "  第十四条  同意",
            "==============================================",
            "您通过本插件要求的确认方式表示同意，即构成对本协议",
            "全部条款的接受。",
            "",
            "==============================================",
            "  © 2024-2026 FancyHelper 开发者。保留所有权利。",
            "=============================================="
        );

        this.licenseContent = Arrays.asList(
            "本项目使用 GPL-3.0 开源。",
            "协议文本见 https://github.com/baicaizhale/FancyHelper?tab=GPL-3.0-1-ov-file"
        );

        ensureFiles();
        startRealtimeMonitoring();
    }

    /**
     * 确保 EULA 和 License 文件存在且内容正确。
     */
    private synchronized void ensureFiles() {
        ensureEulaFile();
        ensureLicenseFile();
    }

    private void ensureEulaFile() {
        try {
            boolean needsUpdate = false;
            if (!eulaFile.exists()) {
                needsUpdate = true;
            } else {
                List<String> currentContent = Files.readAllLines(eulaFile.toPath(), StandardCharsets.UTF_8);
                if (!eulaContent.equals(currentContent)) {
                    needsUpdate = true;
                }
            }

            if (needsUpdate) {
                Files.write(eulaFile.toPath(), eulaContent, StandardCharsets.UTF_8);
                plugin.getLogger().info("EULA 文件已创建或还原: " + eulaFile.getPath());
            }
            isEulaValid.set(true);
        } catch (IOException e) {
            plugin.getLogger().severe("无法读写 EULA 文件 (可能由于权限不足): " + e.getMessage());
            isEulaValid.set(false);
        }
    }

    private void ensureLicenseFile() {
        try {
            boolean needsUpdate = false;
            if (!licenseFile.exists()) {
                needsUpdate = true;
            } else {
                List<String> currentContent = Files.readAllLines(licenseFile.toPath(), StandardCharsets.UTF_8);
                if (!licenseContent.equals(currentContent)) {
                    needsUpdate = true;
                }
            }

            if (needsUpdate) {
                Files.write(licenseFile.toPath(), licenseContent, StandardCharsets.UTF_8);
                plugin.getLogger().info("License 文件已创建或还原: " + licenseFile.getPath());
            }
            isLicenseValid.set(true);
        } catch (IOException e) {
            plugin.getLogger().severe("无法读写 License 文件 (可能由于权限不足): " + e.getMessage());
            isLicenseValid.set(false);
        }
    }

    /**
     * 使用 Java WatchService 开始实时监控文件变动。
     */
    private void startRealtimeMonitoring() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            Path path = eulaFile.getParentFile().toPath();
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_CREATE);

            watchThread = new Thread(() -> {
                while (running) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException | ClosedWatchServiceException e) {
                        break;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                        Path eventPath = (Path) event.context();
                        String fileName = eventPath.getFileName().toString();
                        if (fileName.equals(eulaFile.getName()) || fileName.equals(licenseFile.getName())) {
                            // 文件变动，立即还原
                            // 稍微延迟一下以防某些编辑器锁定文件
                            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                            ensureFiles();
                        }
                    }

                    if (!key.reset()) {
                        break;
                    }
                }
            }, "FancyHelper-EULA-Monitor");
            watchThread.setDaemon(true);
            watchThread.start();
            plugin.getLogger().info("EULA 实时监控已启动。");
        } catch (IOException e) {
            plugin.getLogger().severe("无法启动 EULA 实时监控: " + e.getMessage());
            // 回退到每分钟检查一次的模式
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::ensureEulaFile, 1200L, 1200L);
        }
    }

    /**
     * 检查 EULA 是否有效。
     * 
     * @return 如果有效则返回 true，否则返回 false。
     */
    public boolean isEulaValid() {
        return isEulaValid.get();
    }

    /**
     * 获取 EULA 文本内容。
     * 
     * @return EULA 文本列表
     */
    public List<String> getEulaContent() {
        return eulaContent;
    }

    /**
     * 创建一个包含 EULA 内容的虚拟书本。
     * 
     * @return 包含 EULA 的 ItemStack (WRITTEN_BOOK)
     */
    public ItemStack getEulaBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        if (meta != null) {
            meta.setTitle("FancyHelper EULA");
            meta.setAuthor("FancyHelper");
            
            // 按字符数分页，Minecraft 书本每页约 256 字符限制，使用 128 作为安全边距
            final int MAX_PAGE_CHARS = 128;
            StringBuilder pageBuilder = new StringBuilder();
            
            for (String line : eulaContent) {
                String lineWithNewline = line + "\n";
                
                // 如果当前行加入后会超出页面限制，先添加当前页面
                if (pageBuilder.length() + lineWithNewline.length() > MAX_PAGE_CHARS) {
                    if (pageBuilder.length() > 0) {
                        meta.addPage(pageBuilder.toString());
                        pageBuilder = new StringBuilder();
                    }
                }
                
                // 如果单行就超过页面限制，需要智能换行
                String remainingLine = lineWithNewline;
                while (remainingLine.length() > MAX_PAGE_CHARS) {
                    int splitIndex = findSmartSplitIndex(remainingLine, MAX_PAGE_CHARS);
                    pageBuilder.append(remainingLine, 0, splitIndex).append("\n");
                    meta.addPage(pageBuilder.toString());
                    pageBuilder = new StringBuilder();
                    remainingLine = remainingLine.substring(splitIndex).trim();
                    if (remainingLine.isEmpty()) break;
                }
                
                pageBuilder.append(remainingLine);
            }
            
            // 添加最后一页
            if (pageBuilder.length() > 0) {
                meta.addPage(pageBuilder.toString());
            }
            
            book.setItemMeta(meta);
        }
        
        return book;
    }
    
    /**
     * 智能换行：按空格或中文字符边界分割
     * 
     * @param text 要分割的文本
     * @param maxLen 最大长度
     * @return 建议的分割位置
     */
    private int findSmartSplitIndex(String text, int maxLen) {
        if (text.length() <= maxLen) return text.length();
        
        // 先尝试在空格处分割
        int lastSpace = text.lastIndexOf(' ', maxLen);
        if (lastSpace > maxLen / 2) {
            return lastSpace;
        }
        
        // 中文字符边界检查（中文在 Unicode 中位于 4E00-9FA5 范围）
        for (int i = maxLen - 1; i >= maxLen / 2; i--) {
            char c = text.charAt(i);
            if (c < 0x4E00 || c > 0x9FA5) { // 非中文字符
                return i + 1;
            }
        }
        
        return maxLen;
    }

    /**
     * 重新加载 EULA 和 License 文件并检查。
     */
    public void reload() {
        ensureFiles();
    }

    /**
     * 强制替换 EULA 和 License 文件为当前最新内容。
     * 通常用于版本更新时。
     */
    public void forceReplaceFiles() {
        try {
            Files.write(eulaFile.toPath(), eulaContent, StandardCharsets.UTF_8);
            Files.write(licenseFile.toPath(), licenseContent, StandardCharsets.UTF_8);
            plugin.getLogger().warning("由于版本更新，EULA 和 License 文件已强制更新。");
            plugin.getLogger().warning("请查看 plugins/FancyHelper/README/ 目录下的 eula.txt 和 license.txt 文件了解最新协议内容。");
            isEulaValid.set(true);
            isLicenseValid.set(true);
        } catch (IOException e) {
            plugin.getLogger().severe("强制更新文件失败: " + e.getMessage());
            isEulaValid.set(false);
            isLicenseValid.set(false);
        }
    }

    /**
     * 停止监控并释放资源。
     */
    public void shutdown() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }
}
