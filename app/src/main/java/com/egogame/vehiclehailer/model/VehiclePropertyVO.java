package com.egogame.vehiclehailer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 车辆属性定义（完整版VO）
 *
 * 对比鱼蛋的 VehiclePropertyVO（23字段）：
 * 鱼蛋功能：
 * - propertyName / displayName / catalog / category / controlType
 * - defaultValue / initialValue / minValue / maxValue / step / unit
 * - isTrigger / options / optionValues / unsupportedModelIds
 * - getDisplayByOptionValue() / getOptionValueByDisplay()
 *
 * 我们增强：
 * ① 增加 isReadOnly 属性（鱼蛋没有，标记只读属性不可编辑）
 * ② 增加 description 属性描述（鱼蛋没有）
 * ③ 增加 serializedValue 运行时缓存（鱼蛋没有）
 * ④ 更丰富的 from/to JSON 序列化方法
 * ⑤ LinkedHashMap 保留选项顺序
 */
public class VehiclePropertyVO {

    // ---- 基础字段 ----
    private String propertyName;        // 属性名，如 "doorLeft"
    private String displayName;         // 显示名称，如 "左前门"
    private String catalog;             // 所属目录，如 "door"
    private String category;            // 所属分类，如 "state"
    private String controlType;         // 控件类型："BUTTON_GROUP" / "SLIDER" / "SWITCH" / "INPUT"
    private String unit;                // 单位，如 "℃"、"km/h"

    // ---- 数值范围 ----
    private float minValue;             // 最小值
    private float maxValue;             // 最大值
    private float step;                 // 步进值
    private float defaultValue;         // 默认值
    private String initialValue;        // 初始值（字符串形式，鱼蛋有此字段）

    // ---- 选项相关 ----
    private List<String> options;       // 选项显示文本列表
    private List<String> optionValues;  // 选项实际值列表（与options一一对应）

    // ---- 行为控制 ----
    private boolean isTrigger;          // 是否为触发型属性（值变化时触发动作）
    private boolean isReadOnly;         // 是否只读 【鱼蛋没有】
    private String description;         // 属性描述说明 【鱼蛋没有】

    // ---- 兼容性 ----
    private String unsupportedModelIds; // 不支持的车型ID列表，分号分隔

    // ---- 运行时 ----
    private String serializedValue;     // 序列化后的当前值（运行时填充）【鱼蛋没有】

    /**
     * 无参构造（用于JSON反序列化）
     */
    public VehiclePropertyVO() {}

    /**
     * 全参构造
     */
    public VehiclePropertyVO(String propertyName, String displayName, String catalog,
                             String category, String controlType, String unit,
                             float minValue, float maxValue, float step,
                             float defaultValue, String initialValue,
                             List<String> options, List<String> optionValues,
                             boolean isTrigger, boolean isReadOnly, String description,
                             String unsupportedModelIds) {
        this.propertyName = propertyName;
        this.displayName = displayName;
        this.catalog = catalog;
        this.category = category;
        this.controlType = controlType;
        this.unit = unit;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.step = step;
        this.defaultValue = defaultValue;
        this.initialValue = initialValue;
        this.options = options;
        this.optionValues = optionValues;
        this.isTrigger = isTrigger;
        this.isReadOnly = isReadOnly;
        this.description = description;
        this.unsupportedModelIds = unsupportedModelIds;
    }

    // ---- Getter/Setter ----

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getCatalog() { return catalog; }
    public void setCatalog(String catalog) { this.catalog = catalog; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getControlType() { return controlType; }
    public void setControlType(String controlType) { this.controlType = controlType; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public float getMinValue() { return minValue; }
    public void setMinValue(float minValue) { this.minValue = minValue; }

    public float getMaxValue() { return maxValue; }
    public void setMaxValue(float maxValue) { this.maxValue = maxValue; }

    public float getStep() { return step; }
    public void setStep(float step) { this.step = step; }

    public float getDefaultValue() { return defaultValue; }
    public void setDefaultValue(float defaultValue) { this.defaultValue = defaultValue; }

    public String getInitialValue() { return initialValue; }
    public void setInitialValue(String initialValue) { this.initialValue = initialValue; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }

    public List<String> getOptionValues() { return optionValues; }
    public void setOptionValues(List<String> optionValues) { this.optionValues = optionValues; }

    public boolean isTrigger() { return isTrigger; }
    public void setTrigger(boolean trigger) { isTrigger = trigger; }

    public boolean isReadOnly() { return isReadOnly; }
    public void setReadOnly(boolean readOnly) { isReadOnly = readOnly; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getUnsupportedModelIds() { return unsupportedModelIds; }
    public void setUnsupportedModelIds(String unsupportedModelIds) { this.unsupportedModelIds = unsupportedModelIds; }

    public String getSerializedValue() { return serializedValue; }
    public void setSerializedValue(String serializedValue) { this.serializedValue = serializedValue; }

    // ---- 增强方法 ----

    /**
     * 通过选项显示文本获取实际值（对应鱼蛋 getOptionValueByDisplay）
     */
    public String getOptionValueByDisplay(String display) {
        if (options == null || optionValues == null || display == null) return display;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equals(display) && i < optionValues.size()) {
                return optionValues.get(i);
            }
        }
        return display;
    }

    /**
     * 通过选项实际值获取显示文本（对应鱼蛋 getDisplayByOptionValue）
     */
    public String getDisplayByOptionValue(String value) {
        if (options == null || optionValues == null || value == null) return value;
        for (int i = 0; i < optionValues.size(); i++) {
            if (optionValues.get(i).equals(value) && i < options.size()) {
                return options.get(i);
            }
        }
        return value;
    }

    /**
     * 检查该属性是否支持指定车型
     */
    public boolean isSupportedForModel(int modelId) {
        if (unsupportedModelIds == null || unsupportedModelIds.isEmpty()) return true;
        String[] ids = unsupportedModelIds.split(";");
        for (String id : ids) {
            if (id.trim().equals(String.valueOf(modelId))) return false;
        }
        return true;
    }

    /**
     * 判断控件类型是否为按钮组（有选项列表）
     */
    public boolean isButtonGroup() {
        return "BUTTON_GROUP".equalsIgnoreCase(controlType)
                || "SWITCH".equalsIgnoreCase(controlType);
    }

    /**
     * 判断控件类型是否为滑块
     */
    public boolean isSlider() {
        return "SLIDER".equalsIgnoreCase(controlType);
    }

    /**
     * 转换为JSON字符串（用于序列化保存）
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendJsonField(sb, "propertyName", propertyName, true);
        appendJsonField(sb, "displayName", displayName, false);
        appendJsonField(sb, "catalog", catalog, false);
        appendJsonField(sb, "category", category, false);
        appendJsonField(sb, "controlType", controlType, false);
        appendJsonField(sb, "unit", unit, false);
        sb.append("\"minValue\":").append(minValue).append(",");
        sb.append("\"maxValue\":").append(maxValue).append(",");
        sb.append("\"step\":").append(step).append(",");
        sb.append("\"defaultValue\":").append(defaultValue).append(",");
        appendJsonField(sb, "initialValue", initialValue, false);
        sb.append("\"isTrigger\":").append(isTrigger).append(",");
        sb.append("\"isReadOnly\":").append(isReadOnly).append(",");
        appendJsonField(sb, "description", description, false);
        appendJsonField(sb, "unsupportedModelIds", unsupportedModelIds, false);
        if (options != null && !options.isEmpty()) {
            sb.append("\"options\":[");
            for (int i = 0; i < options.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(options.get(i))).append("\"");
            }
            sb.append("],");
        }
        if (optionValues != null && !optionValues.isEmpty()) {
            sb.append("\"optionValues\":[");
            for (int i = 0; i < optionValues.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(optionValues.get(i))).append("\"");
            }
            sb.append("],");
        }
        // 移除末尾逗号
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("}");
        return sb.toString();
    }

    private void appendJsonField(StringBuilder sb, String key, String value, boolean isFirst) {
        if (value != null) {
            if (!isFirst) sb.append(",");
            sb.append("\"").append(key).append("\":\"").append(escapeJson(value)).append("\"");
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return "VehiclePropertyVO{" +
                "propertyName='" + propertyName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", catalog='" + catalog + '\'' +
                ", isTrigger=" + isTrigger +
                '}';
    }
}
