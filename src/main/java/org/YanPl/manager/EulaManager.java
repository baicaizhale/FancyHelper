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
            "【重要提示 — 请仔细阅读】",
            "本最终用户许可协议（下称\"本协议\"）是您（下称\"用户\"",
            "或\"您\"）与 FancyHelper 开发者（下称\"开发者\"或\"我方\"）",
            "之间就 FancyHelper 插件（下称\"本插件\"）的使用所订立",
            "的具有法律约束力的协议。",
            "",
            "安装、复制、下载、访问或以任何方式使用本插件，即表示",
            "您确认您已阅读、理解并同意受本协议全部条款的约束。",
            "如果您不同意本协议的任何条款，您必须立即停止使用",
            "本插件，并从您的服务器中彻底删除本插件的所有文件。",
            "",
            "==============================================",
            "  第一条  定义与解释",
            "==============================================",
            "1.1 \"本插件\"指 FancyHelper 软件及其所有组件、文档、",
            "    配置文件及后续更新版本。",
            "1.2 \"AI 服务\"指本插件所集成的第三方人工智能服务，",
            "    包括但不限于 Cloudflare Workers AI、OpenAI API、",
            "    DeepSeek API、Metaso API、Tavily API 等。",
            "1.3 \"AI 生成内容\"指由上述 AI 服务自动生成的任何",
            "    文本、代码、命令、建议、回答或其他输出。",
            "1.4 \"用户\"指安装、配置或使用本插件的个人或实体，",
            "    包括但不限于 Minecraft 服务器管理员、服务器所有者",
            "    以及通过本插件与 AI 交互的最终玩家。",
            "",
            "==============================================",
            "  第二条  AI 生成内容免责声明",
            "==============================================",
            "2.1 本插件集成第三方 AI 模型。AI 生成内容完全由算法",
            "    自动产出，其生成过程不受开发者人为干预或控制。",
            "2.2 AI 生成内容不代表、不反映开发者的任何观点、立场、",
            "    意见或建议。开发者对 AI 生成内容的真实性、准确性、",
            "    完整性、合法性、时效性、适用性或无害性不作任何",
            "    形式的保证或担保。",
            "2.3 AI 技术固有地存在\"幻觉 (Hallucination)\"风险，",
            "    即 AI 可能生成看似合理但实际错误、虚构或误导的",
            "    信息。用户应独立核实所有 AI 生成内容后再据此采取",
            "    任何行动。",
            "2.4 开发者不对因依赖、使用或信任 AI 生成内容而产生的",
            "    任何损失、损害、责任或后果承担任何直接或间接责任。",
            "2.5 AI 可能生成受版权、商标、专利或其他知识产权保护",
            "    的内容。开发者不保证 AI 生成内容不侵犯第三方权利，",
            "    用户应自行承担因使用此类内容而产生的知识产权风险。",
            "",
            "==============================================",
            "  第三条  无担保声明 (NO WARRANTY)",
            "==============================================",
            "3.1 本插件按\"原样 (AS IS)\"及\"可提供 (AS AVAILABLE)\"",
            "    的基础提供，不附带任何形式的明示或暗示担保。",
            "3.2 在法律允许的最大范围内，开发者明确否认所有担保，",
            "    包括但不限于：适销性担保、特定用途适用性担保、",
            "    所有权担保、不侵权担保、以及因交易过程或商业惯例",
            "    产生的任何担保。",
            "3.3 开发者不保证本插件无错误、无缺陷、无安全漏洞、",
            "    运行不间断、与所有服务器环境兼容或能满足用户的",
            "    特定需求或期望。",
            "3.4 开发者不保证本插件与任何特定 Minecraft 服务端",
            "    版本、第三方插件或其他软件的兼容性或互操作性。",
            "3.5 用户明确理解并同意，使用本插件及 AI 服务的风险",
            "    完全由用户自行承担。本插件提供的所有信息、内容和",
            "    功能均\"按原样\"提供，用户依赖上述信息、内容或功能",
            "    的风险自负。",
            "",
            "==============================================",
            "  第四条  责任限制与赔偿豁免",
            "==============================================",
            "4.1 在法律允许的最大范围内，开发者在任何情况下均不对",
            "    因使用或无法使用本插件而引起的任何损害承担责任，",
            "    无论该等损害是直接的、间接的、偶然的、特殊的、",
            "    惩罚性的或后果性的。",
            "4.2 上述损害包括但不限于：数据丢失或损坏、数据泄露、",
            "    服务器宕机或损坏、Minecraft 世界存档损坏、利润损失、",
            "    收入损失、商誉损失、业务中断、计算机故障或系统崩溃、",
            "    第三方索赔、以及任何其他商业损害或损失。",
            "4.3 上述责任限制适用于基于合同、侵权（包括过失）、",
            "    严格责任、产品责任、法定责任或其他任何法律理论的",
            "    索赔，即使开发者已被告知该等损害的可能性。",
            "4.4 如果适用法律不允许对默示担保或某些损害进行排除",
            "    或限制，则上述排除或限制仅在法律允许的最大范围内",
            "    适用。开发者的总赔偿责任在任何情况下均不超过",
            "    人民币壹佰元（¥100.00）或您为使用本插件实际支付",
            "    的金额（以较低者为准）。若本插件为免费提供，则",
            "    开发者的总赔偿责任为零。",
            "",
            "==============================================",
            "  第五条  第三方服务与数据",
            "==============================================",
            "5.1 本插件依赖第三方 AI 服务提供商（包括但不限于",
            "    Cloudflare、OpenAI、DeepSeek、Metaso、Tavily）。",
            "    开发者不是上述第三方服务的所有者、运营者或控制者。",
            "5.2 用户通过本插件发送的对话内容、指令和上下文信息",
            "    将传输至上述第三方服务商的服务器进行处理。第三方",
            "    服务商的隐私政策、数据使用政策和服务条款独立于",
            "    本协议，不受开发者控制。",
            "5.3 开发者不对任何第三方服务商的以下方面承担任何责任：",
            "    (a) 服务的可用性、稳定性、速度或可靠性；",
            "    (b) 数据处理、存储、保留或删除政策；",
            "    (c) 内容审核、过滤或审查政策；",
            "    (d) 数据安全措施的充分性或数据泄露事件；",
            "    (e) 服务条款或隐私政策的变更。",
            "5.4 用户明确知悉并同意，开发者不是数据处理者或数据",
            "    控制者，开发者不参与、不介入、也无法控制 AI 服务",
            "    提供商对用户数据的处理行为。",
            "",
            "==============================================",
            "  第六条  用户义务与使用合规",
            "==============================================",
            "6.1 用户承诺仅将本插件用于合法目的，并严格遵守：",
            "    (a) 用户所在国家/地区的全部适用法律法规；",
            "    (b) 中华人民共和国法律法规（因开发者所在地）；",
            "    (c) 所有适用的国际法律和条约；",
            "    (d) Mojang AB 的最终用户许可协议及 Minecraft",
            "        使用准则；",
            "    (e) 各 AI 服务提供商的使用条款和可接受使用政策。",
            "6.2 用户不得利用本插件从事或促成以下行为：",
            "    (a) 生成、传播或存储任何违法、侵权、诽谤、淫秽、",
            "        骚扰、仇恨、歧视、暴力或其他不当内容；",
            "    (b) 侵犯任何第三方的知识产权、隐私权、名誉权或",
            "        其他合法权益；",
            "    (c) 干扰、破坏或未经授权访问任何计算机系统、网络",
            "        或服务器；",
            "    (d) 从事欺诈、虚假陈述或其他欺骗行为；",
            "    (e) 违反出口管制法律或经济制裁规定。",
            "6.3 因用户违反本第六条而产生的全部法律责任、索赔、",
            "    损失、损害和费用（包括合理的律师费）均由用户",
            "    独立承担，与开发者无关。",
            "",
            "==============================================",
            "  第七条  本地数据存储风险",
            "==============================================",
            "7.1 本插件将对话历史、玩家交互记录及 AI 上下文以明文",
            "    格式存储在服务器本地文件系统中。",
            "7.2 开发者不对本地存储数据的安全性、保密性或完整性",
            "    提供任何保证。本地数据可能因服务器安全漏洞、恶意",
            "    软件、未授权访问、系统故障、人为错误等原因被泄露、",
            "    篡改或丢失。",
            "7.3 用户应自行采取适当的安全措施保护本地数据，包括",
            "    但不限于文件系统权限控制、定期备份和服务器安全加固。",
            "7.4 用户知悉并同意，对话内容可能被服务器管理员或其他",
            "    有文件系统访问权限的人员查看。开发者不对任何内部",
            "    数据访问行为承担责任。",
            "",
            "==============================================",
            "  第八条  知识产权",
            "==============================================",
            "8.1 本插件（包括但不限于源代码、目标代码、文档、界面",
            "    设计、配置文件和所有相关的知识产权）的所有权和",
            "    知识产权均归开发者所有，受中华人民共和国著作权法",
            "    和国际著作权条约的保护。",
            "8.2 本插件根据 GNU General Public License v3.0",
            "    (GPL-3.0) 许可条款开源。用户对源代码的使用、修改",
            "    和分发须遵守 GPL-3.0 许可协议的规定。",
            "8.3 本协议中的任何内容均不构成向用户转让或许可开发者",
            "    的任何商标、服务标志或商业外观。",
            "8.4 AI 生成内容的知识产权归属问题当前在全球范围内均",
            "    存在法律不确定性。开发者不对 AI 生成内容的可版权性、",
            "    可专利性或知识产权归属作任何声明或保证。用户应自行",
            "    承担使用 AI 生成内容可能引发的知识产权风险。",
            "",
            "==============================================",
            "  第九条  协议变更",
            "==============================================",
            "9.1 开发者保留随时自行决定修改、增补或替换本协议",
            "     任何条款的权利，无需事先通知用户。",
            "9.2 修改后的协议将在本插件更新时随新版 EULA 文件",
            "     一同分发，自新版插件安装或启动之时起生效。",
            "9.3 用户在协议修改后继续使用本插件的行为，构成对",
            "     修改后协议的完全接受。如果用户不同意修改后的条款，",
            "     其唯一且排他的救济措施是立即停止使用本插件并删除",
            "     所有相关文件。",
            "",
            "==============================================",
            "  第十条  终止",
            "==============================================",
            "10.1 本协议在用户停止使用本插件并删除所有相关文件前",
            "     持续有效。",
            "10.2 开发者保留随时基于任何原因或无需原因终止或暂停",
            "     用户对本插件访问的权利，无需事先通知或承担责任。",
            "10.3 本协议终止后，第二至八条、第十二条、第十三条",
            "     中按其性质应在终止后继续有效的条款应持续有效。",
            "",
            "==============================================",
            "  第十一条  不可抗力",
            "==============================================",
            "开发者不对因超出其合理控制范围的原因导致的任何延迟",
            "或履行不能承担责任，包括但不限于：自然灾害、战争、",
            "恐怖主义、内乱、政府行为、网络攻击、第三方服务中断、",
            "电力或电信基础设施故障、以及互联网整体或局部中断。",
            "",
            "==============================================",
            "  第十二条  管辖法律与争议解决",
            "==============================================",
            "12.1 本协议的解释、效力和履行均适用中华人民共和国法律，",
            "     但不适用其冲突法规则。《联合国国际货物销售合同",
            "     公约》明确排除适用。",
            "12.2 因本协议引起或与之相关的任何争议，双方应首先",
            "     本着诚信原则通过友好协商解决。",
            "12.3 如协商不成，任何一方可将争议提交至开发者所在地",
            "     有管辖权的人民法院诉讼解决。",
            "12.4 无论任何冲突法原则如何，用户明确同意开发者所在地",
            "     法院对因本协议产生的所有争议具有属人管辖权。",
            "",
            "==============================================",
            "  第十三条  一般条款",
            "==============================================",
            "13.1 【可分割性】如本协议任何条款被有管辖权的法院或",
            "     行政机关认定为无效、非法或不可执行，该条款应在",
            "     必要的最小范围内进行修改或限制以使其有效和可执行，",
            "     本协议的其余条款应继续完全有效。",
            "13.2 【不放弃权利】开发者未能或延迟行使本协议项下的",
            "     任何权利、权力或特权，不构成对该等权利的放弃；",
            "     单独或部分行使任何权利也不妨碍对该权利的任何其他",
            "     或进一步的行使。",
            "13.3 【完整协议】本协议构成用户与开发者之间就本插件",
            "     使用的完整协议，取代双方之前就同一主题达成的所有",
            "     口头或书面的沟通、陈述和协议。",
            "13.4 【转让】用户不得将其在本协议项下的权利或义务转让",
            "     给任何第三方。开发者可自由转让本协议。",
            "13.5 【标题】本协议中的条款标题仅为方便阅读而设，",
            "     不影响本协议任何条款的解释或含义。",
            "13.6 【语言】本协议以中文书就。任何翻译版本仅供参考，",
            "     如中文版本与翻译版本存在歧义，以中文版本为准。",
            "",
            "==============================================",
            "  第十四条  明确同意",
            "==============================================",
            "14.1 用户通过发送 agree 指令或以其他方式明确表示同意，",
            "     即构成用户对本协议全部条款的不可撤销的接受。",
            "14.2 用户的同意表示用户确认：",
            "      (a) 已完整阅读并充分理解本协议的每一条款；",
            "      (b) 已获得就本协议寻求独立法律意见的机会；",
            "      (c) 同意受本协议全部条款的法律约束；",
            "      (d) 理解并接受本插件及AI服务的所有风险。",
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
