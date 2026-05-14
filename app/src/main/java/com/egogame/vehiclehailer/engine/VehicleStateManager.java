package com.egogame.vehiclehailer.engine;

import android.util.Log;

import com.egogame.vehiclehailer.model.PropertyReg;
import com.egogame.vehiclehailer.model.VehicleProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 车辆状态管理器
 * 核心：维护所有车辆属性的当前值，基于logcat日志更新状态
 */
public class VehicleStateManager {
    private static final String TAG = "VehicleStateManager";

    private final ConfigLoader configLoader;
    // propertyName -> currentValue (字符串形式存储，便于传递)
    private final ConcurrentHashMap<String, String> stateMap = new ConcurrentHashMap<>();
    // propertyName -> 编译后的正则表达式Pattern缓存
    private final Map<String, Pattern> patternCache = new HashMap<>();

    // 状态变化监听器（支持多个 - 线程安全）
    private final List<OnPropertyChangeListener> listeners = new CopyOnWriteArrayList<>();

    public interface OnPropertyChangeListener {
        void onPropertyChanged(String propertyName, String oldValue, String newValue);
    }

    public VehicleStateManager(ConfigLoader configLoader) {
        this.configLoader = configLoader;
        // 初始化所有属性的默认值
        initDefaults();
    }

    /**
     * 添加属性变化监听器
     */
    public void addListener(OnPropertyChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除属性变化监听器
     */
    public void removeListener(OnPropertyChangeListener listener) {
        listeners.remove(listener);
    }

    private void initDefaults() {
        List<VehicleProperty> props = configLoader.getVehicleProperties();
        for (VehicleProperty p : props) {
            stateMap.put(p.getPropertyName(), formatDefaultValue(p));
        }
    }

    private String formatDefaultValue(VehicleProperty p) {
        if (p.getControlType() == VehicleProperty.ControlType.BUTTON_GROUP) {
            // 选项按钮：找到默认值对应的显示文本
            for (int i = 0; i < p.getOptionValues().length; i++) {
                if (p.getOptionValues()[i].equals(String.valueOf((int)p.getDefaultValue()))) {
                    if (i < p.getOptions().length) return p.getOptions()[i];
                }
            }
            return p.getOptions().length > 0 ? p.getOptions()[0] : String.valueOf((int)p.getDefaultValue());
        } else {
            return String.valueOf(p.getDefaultValue());
        }
    }

    /**
     * 获取属性值
     */
    public String getPropertyValue(String propertyName) {
        String val = stateMap.get(propertyName);
        return val != null ? val : "0";
    }

    /**
     * 手动设置属性值（用于UI调试/模拟模式）
     */
    public void setPropertyValue(String propertyName, String value) {
        String oldValue = stateMap.get(propertyName);
        stateMap.put(propertyName, value);
        if (!value.equals(oldValue)) {
            for (OnPropertyChangeListener l : listeners) {
                l.onPropertyChanged(propertyName, oldValue, value);
            }
        }
    }

    /**
     * 获取所有属性状态的快照
     */
    public Map<String, String> getStateSnapshot() {
        return new HashMap<>(stateMap);
    }

    /**
     * 处理一条logcat日志行，尝试匹配并更新属性
     * @return 如果匹配到了某个属性并更新，返回属性名+新值；否则返回
     */
    public PropertyMatchResult processLogLine(String logLine) {
        List<PropertyReg> regs = configLoader.getCurrentPropertyRegs();
        for (PropertyReg reg : regs) {
            if (!reg.isLogMonitorEnabled()) continue;

            // 检查标签匹配
            if (!logLine.contains(reg.getLogcatTags())) continue;

            // 编译并执行正则
            Pattern pattern = patternCache.get(reg.getPropertyName());
            if (pattern == null) {
                try {
                    pattern = Pattern.compile(reg.getRegex());
                    patternCache.put(reg.getPropertyName(), pattern);
                } catch (Exception e) {
                    Log.w(TAG, "正则编译失败: " + reg.getRegex(), e);
                    continue;
                }
            }

            Matcher matcher = pattern.matcher(logLine);
            if (matcher.find()) {
                String rawValue = matcher.group(1);
                String mappedValue = reg.mapValue(rawValue);

                String oldValue = stateMap.get(reg.getPropertyName());
                stateMap.put(reg.getPropertyName(), mappedValue);

                if (!mappedValue.equals(oldValue)) {
                    for (OnPropertyChangeListener l : listeners) {
                        l.onPropertyChanged(reg.getPropertyName(), oldValue, mappedValue);
                    }
                }

                // 同时更新UI展示值
                VehicleProperty prop = configLoader.findPropertyByName(reg.getPropertyName());
                String displayValue = convertToDisplayValue(prop, mappedValue);

                return new PropertyMatchResult(reg.getPropertyName(), mappedValue, displayValue, logLine);
            }
        }
        return null;
    }

    /**
     * 将存储值转为界面显示值
     */
    private String convertToDisplayValue(VehicleProperty prop, String value) {
        if (prop == null) return value;
        if (prop.getControlType() == VehicleProperty.ControlType.BUTTON_GROUP) {
            for (int i = 0; i < prop.getOptionValues().length; i++) {
                if (prop.getOptionValues()[i].equals(value)) {
                    if (i < prop.getOptions().length) return prop.getOptions()[i];
                }
            }
        }
        return value;
    }

    /**
     * 匹配结果
     */
    public static class PropertyMatchResult {
        public final String propertyName;
        public final String rawValue;
        public final String displayValue;
        public final String sourceLogLine;

        public PropertyMatchResult(String propertyName, String rawValue,
                                   String displayValue, String sourceLogLine) {
            this.propertyName = propertyName;
            this.rawValue = rawValue;
            this.displayValue = displayValue;
            this.sourceLogLine = sourceLogLine;
        }
    }
}
