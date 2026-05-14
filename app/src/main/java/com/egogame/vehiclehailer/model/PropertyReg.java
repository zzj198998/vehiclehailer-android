package com.egogame.vehiclehailer.model;

/**
 * 属性logcat匹配规则
 */
public class PropertyReg {
    private int id;
    private String propertyName;
    private String logcatTags;
    private String regex;
    private String wrapperClassName;
    private String valueMapping;

    public PropertyReg(int id, String propertyName, String logcatTags, String regex,
                       String wrapperClassName, String valueMapping) {
        this.id = id;
        this.propertyName = propertyName;
        this.logcatTags = logcatTags;
        this.regex = regex;
        this.wrapperClassName = wrapperClassName;
        this.valueMapping = valueMapping;
    }

    public int getId() { return id; }
    public String getPropertyName() { return propertyName; }
    public String getLogcatTags() { return logcatTags; }
    public String getRegex() { return regex; }
    public String getWrapperClassName() { return wrapperClassName; }
    public String getValueMapping() { return valueMapping; }

    public boolean isLogMonitorEnabled() {
        return logcatTags != null && !logcatTags.isEmpty();
    }

    /**
     * 对原始logcat提取的值应用值映射
     */
    public String mapValue(String rawValue) {
        if (valueMapping == null || valueMapping.isEmpty()) return rawValue;
        String[] mappings = valueMapping.split(";");
        for (String mapping : mappings) {
            String[] parts = mapping.split("=");
            if (parts.length == 2 && parts[0].equals(rawValue)) {
                return parts[1];
            }
        }
        return rawValue;
    }
}
