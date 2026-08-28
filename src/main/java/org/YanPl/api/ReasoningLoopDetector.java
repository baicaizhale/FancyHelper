package org.YanPl.api;

/**
 * 思考循环（重复）检测工具。
 *
 * <p>背景：部分思考模型（如 gemma-4）偶发"思考链异常"——反复输出同一段内心戏
 * （reasoning）却始终不出正文，把流式请求拖到超时/无输出。早前用"思考字符数/时长预算"
 * 截断，但<b>长思考是正常现象</b>，按预算截断会误杀正常请求（例如思考 4000+ 字符、
 * 10s 即出正文却被判为循环）。
 *
 * <p>这里改为真正检测"重复"：只有当累积的思考内容<b>尾部由同一单元连续重复构成</b>时
 * 才判定为循环。长但持续推进的思考不会被误判。
 */
public final class ReasoningLoopDetector {

    private ReasoningLoopDetector() {
    }

    /** 最短重复单元长度（字符）：低于此长度的重复视为正常措辞，不判为循环。 */
    static final int MIN_UNIT_LEN = 16;

    /** 单元至少连续重复次数：尾部单元连续重复达到该次数才判为循环。 */
    static final int MIN_REPEATS = 3;

    /** 只扫描尾部这么多字符，限制长思考下的最坏开销（避免 O(n^2) 全量扫描）。 */
    static final int SCAN_WINDOW = 4096;

    /**
     * 检测思考内容是否出现"重复循环"。
     *
     * @param s 累积的思考内容（reasoning）
     * @return 检测到的重复单元（非 null 表示疑似循环）；未检测到返回 null
     */
    static String detect(String s) {
        if (s == null) {
            return null;
        }
        int n = s.length();
        if (n < MIN_UNIT_LEN * MIN_REPEATS) {
            return null;
        }
        // 只取尾部窗口，窗口外的历史内容不参与判定
        int start = Math.max(0, n - SCAN_WINDOW);
        String tail = s.substring(start);
        int tn = tail.length();

        // 枚举重复单元长度 p，检查尾部是否由"最后一个单元"连续重复 MIN_REPEATS 次构成。
        // p <= tn / MIN_REPEATS 保证所有待比较的倒数单元起始偏移均 >= 0，不会越界。
        for (int p = MIN_UNIT_LEN; p <= tn / MIN_REPEATS; p++) {
            int base = tn - p; // 最后一个单元在 tail 中的起始偏移
            boolean repeated = true;
            for (int r = 1; r < MIN_REPEATS; r++) {
                int offset = tn - (r + 1) * p; // 倒数第 (r+1) 个单元起始偏移
                if (!tail.regionMatches(offset, tail, base, p)) {
                    repeated = false;
                    break;
                }
            }
            if (repeated) {
                return tail.substring(base);
            }
        }
        return null;
    }
}
