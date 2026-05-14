package com.egogame.vehiclehailer.model;

/**
 * 车辆属性定义
 */
public class VehicleProperty {
    private String propertyName;
    private String displayName;
    private String category;
    private String catalog;
    private ControlType controlType;
    private String[] options;
    private String[] optionValues;
    private float minValue;
    private float maxValue;
    private float defaultValue;
    private String unit;
    private float step;
    private String unsupportedModelIds;

    public enum ControlType {
        BUTTON_GROUP, SLIDER
    }

    public VehicleProperty(String propertyName, String displayName, String category,
                           String catalog, ControlType controlType, String[] options,
                           String[] optionValues, float minValue, float maxValue,
                           float defaultValue, String unit, float step,
                           String unsupportedModelIds) {
        this.propertyName = propertyName;
        this.displayName = displayName;
        this.category = category;
        this.catalog = catalog;
        this.controlType = controlType;
        this.options = options;
        this.optionValues = optionValues;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = defaultValue;
        this.unit = unit;
        this.step = step;
        this.unsupportedModelIds = unsupportedModelIds;
    }

    public String getPropertyName() { return propertyName; }
    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }
    public String getCatalog() { return catalog; }
    public ControlType getControlType() { return controlType; }
    public String[] getOptions() { return options; }
    public String[] getOptionValues() { return optionValues; }
    public float getMinValue() { return minValue; }
    public float getMaxValue() { return maxValue; }
    public float getDefaultValue() { return defaultValue; }
    public String getUnit() { return unit; }
    public float getStep() { return step; }
    public boolean isSupportedForModel(int modelId) {
        if (unsupportedModelIds == null || unsupportedModelIds.isEmpty()) return true;
        String[] ids = unsupportedModelIds.split(";");
        for (String id : ids) {
            if (id.trim().equals(String.valueOf(modelId))) return false;
        }
        return true;
    }
}
