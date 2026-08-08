package org.YanPl.model;

/**
 * 原生函数调用的结构化工具调用载体。
 *
 * @param id           OpenAI tool_call_id（如 call_abc），Responses API 为 call_id，可 null
 * @param name         工具名（不带 # 前缀，如 edit / edit_memory）
 * @param argumentsJson 原始 JSON 参数字符串（跨流式 delta 累加后得到；无效 JSON 保留原始片段）
 */
public record NativeToolCall(String id, String name, String argumentsJson) {
}
