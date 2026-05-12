package com.egogame.vehiclehailer.engine;

import android.content.Context;
import android.util.Log;

import com.egogame.vehiclehailer.model.CarModel;
import com.egogame.vehiclehailer.model.PropertyReg;
import com.egogame.vehiclehailer.model.VehicleProperty;
import com.egogame.vehiclehailer.model.VoiceItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 联动规则配置管理器
 * 根据车型和属性配置，生成默认的联动规则（TriggerRule）。
 * 同时支持保存/加载用户自定义规则（通过 SharedPreferences）。
 */
public class TriggerRuleConfig {

    private static final String TAG = "TriggerRuleConfig";

    private final Context context;
    private final ConfigLoader configLoader;
    private final List<TriggerRule> systemRules = new ArrayList<>();
    private boolean loaded = false;

    /**
     * 车门相关属性的标准规则模板
     * <p>
     * 这个 Map 定义了每个车门属性变化应该触发哪个音频 id
     * 对于深蓝/启源等车型，我们使用 voice.csv 中的标准语音
     * <p>
     * key = propertyName, value = {oldValue, newValue, voiceId, isExterior}
     */
    private static final Map<String, String[][]> DOOR_RULE_TEMPLATES = new HashMap<>();

    static {
        // 车门开启 → 播放
        DOOR_RULE_TEMPLATES.put("doorLeft", new String[][]{
                {"0", "1", "2001", "true"},    // 锁→开 (左前门)
        });
        DOOR_RULE_TEMPLATES.put("doorRight", new String[][]{
                {"0", "1", "2001", "true"},
        });
        DOOR_RULE_TEMPLATES.put("doorLeftRear", new String[][]{
                {"0", "1", "2002", "true"},    // 注意车辆
        });
        DOOR_RULE_TEMPLATES.put("doorRightRear", new String[][]{
                {"0", "1", "2002", "true"},
        });
        DOOR_RULE_TEMPLATES.put("trunk", new String[][]{
                {"0", "1", "2017", "true"},    // 后备箱开启
                {"1", "0", "2018", "true"},    // 后备箱关闭
        });

        // 锁车状态
        DOOR_RULE_TEMPLATES.put("lockStatus", new String[][]{
                {"0", "1", "1002", "false"},   // 解锁→锁车 → 请排队 (车内提示)
                {"1", "0", "1001", "false"},   // 锁→解锁 → 让让我吧
        });
    }

    public TriggerRuleConfig(Context context, ConfigLoader configLoader) {
        this.context = context;
        this.configLoader = configLoader;
    }

    /**
     * 为当前车型加载默认的联动规则
     */
    public void loadDefaults() {
        systemRules.clear();

        CarModel currentModel = configLoader.getCurrentCarModel();
        if (currentModel == null) {
            Log.w(TAG, "未设置当前车型，无法加载默认规则");
            return;
        }

        String modelName = currentModel.getModelName();
        List<PropertyReg> regs = configLoader.getCurrentPropertyRegs();
        List<VehicleProperty> properties = configLoader.getVehicleProperties();

        Log.d(TAG, "为车型 [" + modelName + "] 生成默认联动规则，共 " + regs.size() + " 个属性注册");

        // 为每个属性注册生成默认规则
        for (PropertyReg reg : regs) {
            String propName = reg.getPropertyName();
            String[][] templates = DOOR_RULE_TEMPLATES.get(propName);
            if (templates == null) continue;

            for (String[] template : templates) {
                String oldValue = template[0];
                String newValue = template[1];
                int voiceId = Integer.parseInt(template[2]);
                boolean isExterior = Boolean.parseBoolean(template[3]);

                // 检查voiceId是否存在于voice.csv中
                VoiceItem targetVoice = findVoiceById(voiceId);
                if (targetVoice == null) {
                    Log.d(TAG, "跳过规则: 音频ID " + voiceId + " 不存在于当前配置");
                    continue;
                }

                TriggerRule rule = new TriggerRule(
                        propName,
                        oldValue,
                        newValue,
                        voiceId,
                        isExterior,
                        0,       // 无延迟
                        true     // 默认启用
                );
                systemRules.add(rule);
                Log.d(TAG, "  添加规则: " + propName + " " + oldValue + "→" + newValue +
                        " 播放#" + voiceId + "(" + targetVoice.getTitle() + ")");
            }
        }

        loaded = true;
        Log.d(TAG, "共生成 " + systemRules.size() + " 条默认联动规则");
    }

    /**
     * 查找voiceId对应的VoiceItem
     */
    private VoiceItem findVoiceById(int voiceId) {
        for (VoiceItem item : configLoader.getVoiceItems()) {
            if (item.getId() == voiceId) return item;
        }
        return null;
    }

    // ==================== 公共访问方法 ====================

    public List<TriggerRule> getSystemRules() {
        return new ArrayList<>(systemRules);
    }

    public boolean isLoaded() {
        return loaded;
    }

    /**
     * 为指定车型加载所有的 property_reg 配置并生成默认规则
     * 供UI选择车型时调用
     */
    public List<TriggerRule> generateRulesForModel(int carModelId) {
        configLoader.setCurrentCarModelId(carModelId);
        loadDefaults();
        return getSystemRules();
    }

    /**
     * 重置所有规则
     */
    public void reset() {
        systemRules.clear();
        loaded = false;
        Log.d(TAG, "规则已重置");
    }
}
