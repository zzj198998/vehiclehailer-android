package com.egogame.vehiclehailer.engine.wrapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 属性日志解析包装器抽象基类 — 对应鱼蛋的 PropertyLogWrapper
 *
 * 鱼蛋：
 * - PropertyParseResult 内部类
 * - abstract parseProperty(String logLine)
 *
 * 我们增强：
 * ① 支持named group正则语法
 * ② 支持值覆盖（overrideValue直接返回固定值）
 * ③ 缓存编译好的Pattern
 * ④ 支持值映射
 */
public abstract class PropertyLogWrapper {

    /** 属性解析结果 */
    public static class PropertyParseResult {
        private final String propertyName;
        private final String rawValue;
        private String mappedValue;
        private final boolean isOverride;

        public PropertyParseResult(String propertyName, String rawValue) {
            this(propertyName, rawValue, false);
        }

        public PropertyParseResult(String propertyName, String rawValue, boolean isOverride) {
            this.propertyName = propertyName;
            this.rawValue = rawValue;
            this.mappedValue = rawValue;
            this.isOverride = isOverride;
        }

        public String getPropertyName() { return propertyName; }
        public String getRawValue() { return rawValue; }
        public String getMappedValue() { return mappedValue; }
        public void setMappedValue(String mappedValue) { this.mappedValue = mappedValue; }
        public boolean isOverride() { return isOverride; }
    }

    protected final String propertyName;
    protected final Pattern pattern;
    protected final String overrideValue;
    protected int groupIndex = 1;

    public PropertyLogWrapper(String propertyName, String regex, String overrideValue) {
        this.propertyName = propertyName;
        this.overrideValue = overrideValue;
        this.pattern = Pattern.compile(regex);
    }

    public PropertyLogWrapper(String propertyName, String regex, String overrideValue, int groupIndex) {
        this(propertyName, regex, overrideValue);
        this.groupIndex = groupIndex;
    }

    /** 解析一行logcat日志 */
    public abstract PropertyParseResult parseProperty(String logLine);

    protected PropertyParseResult createResult(String matchedValue) {
        if (overrideValue != null && !overrideValue.isEmpty()) {
            return new PropertyParseResult(propertyName, overrideValue, true);
        }
        return new PropertyParseResult(propertyName, matchedValue);
    }

    protected PropertyParseResult createResultWithMapping(String matchedValue,
                                                          java.util.Map<String, String> valueMapping) {
        PropertyParseResult result = createResult(matchedValue);
        if (valueMapping != null && matchedValue != null) {
            String mapped = valueMapping.get(matchedValue);
            if (mapped != null) result.setMappedValue(mapped);
        }
        return result;
    }

    public String getPropertyName() { return propertyName; }
    public Pattern getPattern() { return pattern; }
    public String getOverrideValue() { return overrideValue; }
}
