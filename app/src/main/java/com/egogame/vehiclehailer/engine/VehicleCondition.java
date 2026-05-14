package com.egogame.vehiclehailer.engine;

import java.util.regex.Pattern;

/**
 * 车辆条件模型 — 比鱼蛋更强
 *
 * 鱼蛋：6种ConditionType × 3种ValueType
 * 我们：10种ConditionType × 4种ValueType + 支持正则 + 支持嵌套条件组
 */
public class VehicleCondition {

    /** 条件类型（比鱼蛋多4种：CONTAINS/NOT_CONTAINS/MATCHES_REGEX/BETWEEN） */
    public enum ConditionType {
        EQUALS,             // 等于
        NOT_EQUALS,         // 不等于
        GREATER_THAN,       // 大于（数值）
        LESS_THAN,          // 小于（数值）
        GREATER_EQUAL,      // 大于等于（数值）
        LESS_EQUAL,         // 小于等于（数值）
        CONTAINS,           // 包含子串（鱼蛋没有）
        NOT_CONTAINS,       // 不包含子串（鱼蛋没有）
        MATCHES_REGEX,      // 正则匹配（鱼蛋没有）
        BETWEEN             // 在范围内 min <= x <= max（鱼蛋没有）
    }

    /** 值类型（比鱼蛋多1种：BOOLEAN） */
    public enum ValueType {
        INT,
        FLOAT,
        STRING,
        BOOLEAN     // 布尔类型（鱼蛋没有）
    }

    private String propertyName;    // 属性名
    private ConditionType type;     // 条件类型
    private ValueType valueType;    // 值类型
    private String compareValue;    // 比较值（字符串存储，解析时按valueType转换）
    private String compareValueMax; // BETWEEN模式的上限值
    private boolean caseSensitive = true; // 字符串比较是否区分大小写

    // 缓存编译好的正则
    private transient Pattern cachedPattern;

    public VehicleCondition() {}

    public VehicleCondition(String propertyName, ConditionType type, ValueType valueType, String compareValue) {
        this.propertyName = propertyName;
        this.type = type;
        this.valueType = valueType;
        this.compareValue = compareValue;
    }

    public VehicleCondition(String propertyName, ConditionType type, ValueType valueType,
                            String compareValue, String compareValueMax) {
        this(propertyName, type, valueType, compareValue);
        this.compareValueMax = compareValueMax;
    }

    /**
     * 检查给定值是否满足条件 — 鱼蛋有同名方法但被混淆
     */
    public boolean check(String actualValueStr) {
        if (actualValueStr == null) {
            return type == ConditionType.NOT_EQUALS;
        }

        // 特殊处理：MATCHES_REGEX 和 CONTAINS/NOT_CONTAINS 只对STRING有效
        if (type == ConditionType.MATCHES_REGEX) {
            return checkRegex(actualValueStr);
        }
        if (type == ConditionType.CONTAINS) {
            return caseSensitive
                    ? actualValueStr.contains(compareValue)
                    : actualValueStr.toLowerCase().contains(compareValue.toLowerCase());
        }
        if (type == ConditionType.NOT_CONTAINS) {
            return !(caseSensitive
                    ? actualValueStr.contains(compareValue)
                    : actualValueStr.toLowerCase().contains(compareValue.toLowerCase()));
        }

        // 数值比较
        switch (valueType) {
            case INT:
                return checkInt(actualValueStr);
            case FLOAT:
                return checkFloat(actualValueStr);
            case BOOLEAN:
                return checkBoolean(actualValueStr);
            case STRING:
            default:
                return checkString(actualValueStr);
        }
    }

    private boolean checkInt(String actualValueStr) {
        try {
            int actual = Integer.parseInt(actualValueStr.trim());
            int expected = Integer.parseInt(compareValue.trim());
            return compareInt(actual, expected);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean checkFloat(String actualValueStr) {
        try {
            double actual = Double.parseDouble(actualValueStr.trim());
            double expected = Double.parseDouble(compareValue.trim());
            return compareDouble(actual, expected);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean checkBoolean(String actualValueStr) {
        if (type != ConditionType.EQUALS && type != ConditionType.NOT_EQUALS) {
            return false; // 布尔只支持等于/不等于
        }
        boolean actual = "true".equalsIgnoreCase(actualValueStr.trim())
                || "1".equals(actualValueStr.trim());
        boolean expected = "true".equalsIgnoreCase(compareValue.trim())
                || "1".equals(compareValue.trim());
        return type == ConditionType.EQUALS ? (actual == expected) : (actual != expected);
    }

    private boolean checkString(String actualValueStr) {
        int cmp = caseSensitive
                ? actualValueStr.compareTo(compareValue)
                : actualValueStr.compareToIgnoreCase(compareValue);
        switch (type) {
            case EQUALS:
                return caseSensitive ? actualValueStr.equals(compareValue)
                        : actualValueStr.equalsIgnoreCase(compareValue);
            case NOT_EQUALS:
                return caseSensitive ? !actualValueStr.equals(compareValue)
                        : !actualValueStr.equalsIgnoreCase(compareValue);
            case GREATER_THAN:
                return cmp > 0;
            case LESS_THAN:
                return cmp < 0;
            case GREATER_EQUAL:
                return cmp >= 0;
            case LESS_EQUAL:
                return cmp <= 0;
            case BETWEEN:
                int cmpMax = caseSensitive
                        ? actualValueStr.compareTo(compareValueMax)
                        : actualValueStr.compareToIgnoreCase(compareValueMax);
                return cmp >= 0 && cmpMax <= 0;
            default:
                return false;
        }
    }

    private boolean compareInt(int actual, int expected) {
        switch (type) {
            case EQUALS:        return actual == expected;
            case NOT_EQUALS:    return actual != expected;
            case GREATER_THAN:  return actual > expected;
            case LESS_THAN:     return actual < expected;
            case GREATER_EQUAL: return actual >= expected;
            case LESS_EQUAL:    return actual <= expected;
            case BETWEEN:
                if (compareValueMax == null) return actual == expected;
                int max = Integer.parseInt(compareValueMax.trim());
                return actual >= expected && actual <= max;
            default:
                return false;
        }
    }

    private boolean compareDouble(double actual, double expected) {
        final double EPSILON = 0.0001;
        switch (type) {
            case EQUALS:        return Math.abs(actual - expected) < EPSILON;
            case NOT_EQUALS:    return Math.abs(actual - expected) >= EPSILON;
            case GREATER_THAN:  return actual > expected + EPSILON;
            case LESS_THAN:     return actual < expected - EPSILON;
            case GREATER_EQUAL: return actual >= expected - EPSILON;
            case LESS_EQUAL:    return actual <= expected + EPSILON;
            case BETWEEN:
                if (compareValueMax == null) return Math.abs(actual - expected) < EPSILON;
                double max = Double.parseDouble(compareValueMax.trim());
                return actual >= expected - EPSILON && actual <= max + EPSILON;
            default:
                return false;
        }
    }

    private boolean checkRegex(String actualValueStr) {
        try {
            if (cachedPattern == null) {
                cachedPattern = Pattern.compile(compareValue);
            }
            return cachedPattern.matcher(actualValueStr).matches();
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Getter/Setter ----

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public ConditionType getType() { return type; }
    public void setType(ConditionType type) { this.type = type; }

    public ValueType getValueType() { return valueType; }
    public void setValueType(ValueType valueType) { this.valueType = valueType; }

    public String getCompareValue() { return compareValue; }
    public void setCompareValue(String compareValue) { this.compareValue = compareValue; }

    public String getCompareValueMax() { return compareValueMax; }
    public void setCompareValueMax(String compareValueMax) { this.compareValueMax = compareValueMax; }

    public boolean isCaseSensitive() { return caseSensitive; }
    public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(propertyName);
        sb.append(" ").append(type.name());
        sb.append(" ").append(compareValue);
        if (compareValueMax != null) {
            sb.append(" ~ ").append(compareValueMax);
        }
        sb.append(" (").append(valueType.name()).append(")");
        return sb.toString();
    }
}
