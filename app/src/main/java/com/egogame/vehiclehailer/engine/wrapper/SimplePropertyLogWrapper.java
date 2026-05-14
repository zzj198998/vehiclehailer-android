package com.egogame.vehiclehailer.engine.wrapper;

import java.util.Map;
import java.util.regex.Matcher;

/**
 * 简单属性日志解析包装器 — 对应鱼蛋的 SimplePropertyLogWrapper
 *
 * 直接通过正则从logcat行中提取属性值，
 * 支持 named group 语法和值映射。
 *
 * 用法：
 *   new SimplePropertyLogWrapper("door_state",
 *       "DoorStatus:(\\w+)", null)
 *   .parseProperty("DoorStatus:OPEN")
 *   → PropertyParseResult("door_state", "OPEN")
 */
public class SimplePropertyLogWrapper extends PropertyLogWrapper {

    private Map<String, String> valueMapping;

    public SimplePropertyLogWrapper(String propertyName, String regex) {
        super(propertyName, regex, null);
    }

    public SimplePropertyLogWrapper(String propertyName, String regex, String overrideValue) {
        super(propertyName, regex, overrideValue);
    }

    public SimplePropertyLogWrapper(String propertyName, String regex,
                                    String overrideValue, int groupIndex) {
        super(propertyName, regex, overrideValue, groupIndex);
    }

    /**
     * 设置值映射（鱼蛋没有此功能）
     * 例如: {"OPEN" → "已开", "CLOSE" → "已关"}
     */
    public void setValueMapping(Map<String, String> mapping) {
        this.valueMapping = mapping;
    }

    public Map<String, String> getValueMapping() { return valueMapping; }

    @Override
    public PropertyParseResult parseProperty(String logLine) {
        if (logLine == null) return null;

        Matcher matcher = pattern.matcher(logLine);
        if (!matcher.find()) return null;

        String matchedValue;
        try {
            // 先尝试named group "value"
            matchedValue = matcher.group("value");
        } catch (IllegalArgumentException e) {
            // 如果没有named group，使用groupIndex
            matchedValue = matcher.group(groupIndex);
        }

        if (matchedValue == null || matchedValue.isEmpty()) return null;

        return createResultWithMapping(matchedValue, valueMapping);
    }
}
