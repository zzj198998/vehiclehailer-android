package com.egogame.vehiclehailer.engine;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.egogame.vehiclehailer.model.VoiceItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 车辆事件触发引擎
 * 监听 VehicleStateManager 的属性变化，根据配置的联动规则自动播放音频。
 * 
 * 核心逻辑：
 *   属性变化 → 遍历规则列表 → 匹配规则 → 延迟 → 通过VoicePlayer播放音频
 */
public class VehicleEventTrigger implements VehicleStateManager.OnPropertyChangeListener {

    private static final String TAG = "VehicleEventTrigger";

    private final VoicePlayer voicePlayer;
    private final Handler mainHandler;
    private final List<TriggerRule> rules = new ArrayList<>();

    // 防抖：记录每个属性上次触发的时间（毫秒），避免频繁重复触发
    private final ConcurrentHashMap<String, Long> lastTriggerTimeMap = new ConcurrentHashMap<>();
    private static final long DEFAULT_DEBOUNCE_MS = 500; // 默认防抖间隔500ms

    // 延迟任务管理：key=ruleId, value=是否已取消
    private final Map<String, Boolean> pendingDelays = new HashMap<>();

    // 音频查找表：voiceId -> VoiceItem（由外部设置）
    private final Map<Integer, VoiceItem> voiceMap = new HashMap<>();

    private boolean masterEnabled = true; // 总开关

    public VehicleEventTrigger(VoicePlayer voicePlayer) {
        this.voicePlayer = voicePlayer;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // ==================== 属性变化监听 ====================

    @Override
    public void onPropertyChanged(String propertyName, String oldValue, String newValue) {
        if (!masterEnabled) return;

        Log.d(TAG, "属性变化: " + propertyName + " " + oldValue + " → " + newValue);

        // 防抖检查：同一属性在DEBOUNCE_MS内不重复触发
        long now = System.currentTimeMillis();
        Long lastTriggerTime = lastTriggerTimeMap.get(propertyName);
        if (lastTriggerTime != null && (now - lastTriggerTime) < DEFAULT_DEBOUNCE_MS) {
            Log.d(TAG, "防抖跳过: " + propertyName + " 距离上次触发仅 " + (now - lastTriggerTime) + "ms");
            return;
        }
        lastTriggerTimeMap.put(propertyName, now);

        // 遍历所有启用的规则，查找匹配项
        for (TriggerRule rule : rules) {
            if (!rule.isEnabled()) continue;
            if (!rule.getPropertyName().equals(propertyName)) continue;

            // 检查 oldValue 匹配（如果 rule.oldValue 不为 null）
            if (rule.getOldValue() != null && !rule.getOldValue().equals(oldValue)) continue;
            // 检查 newValue 匹配
            if (!rule.getNewValue().equals(newValue)) continue;

            // 匹配成功！触发播放
            triggerPlay(rule);
        }
    }

    // ==================== 规则管理 ====================

    /**
     * 添加一条联动规则
     */
    public void addRule(TriggerRule rule) {
        rules.add(rule);
        Log.d(TAG, "添加规则: " + rule);
    }

    /**
     * 批量添加规则
     */
    public void addRules(List<TriggerRule> newRules) {
        rules.addAll(newRules);
        Log.d(TAG, "批量添加 " + newRules.size() + " 条规则");
    }

    /**
     * 移除指定规则
     */
    public boolean removeRule(int voiceId) {
        return rules.removeIf(r -> r.getVoiceId() == voiceId);
    }

    /**
     * 清除所有规则
     */
    public void clearRules() {
        rules.clear();
        pendingDelays.clear();
        Log.d(TAG, "所有规则已清除");
    }

    /**
     * 获取所有规则（只读副本）
     */
    public List<TriggerRule> getRules() {
        return new ArrayList<>(rules);
    }

    /**
     * 启用/禁用指定规则
     */
    public void setRuleEnabled(int voiceId, boolean enabled) {
        for (TriggerRule rule : rules) {
            if (rule.getVoiceId() == voiceId) {
                // TriggerRule 是不可变的，需要替换
                rules.set(rules.indexOf(rule), new TriggerRule(
                        rule.getPropertyName(), rule.getOldValue(), rule.getNewValue(),
                        rule.getVoiceId(), rule.isExterior(), rule.getDelayMs(), enabled));
                Log.d(TAG, "规则 " + voiceId + " 已" + (enabled ? "启用" : "禁用"));
                return;
            }
        }
    }

    // ==================== 总开关 ====================

    public void setMasterEnabled(boolean enabled) {
        this.masterEnabled = enabled;
        Log.d(TAG, (enabled ? "开启" : "关闭") + "自动联动触发");
    }

    public boolean isMasterEnabled() {
        return masterEnabled;
    }

    // ==================== 音频查找表 ====================

    /**
     * 设置音频查找表（voiceId -> VoiceItem）
     */
    public void setVoiceMap(List<VoiceItem> voiceItems) {
        voiceMap.clear();
        for (VoiceItem item : voiceItems) {
            voiceMap.put(item.getId(), item);
        }
    }

    // ==================== 内部触发逻辑 ====================

    private void triggerPlay(TriggerRule rule) {
        VoiceItem voiceItem = voiceMap.get(rule.getVoiceId());
        if (voiceItem == null) {
            Log.w(TAG, "未找到音频ID: " + rule.getVoiceId() + "，跳过触发");
            return;
        }

        if (rule.getDelayMs() > 0) {
            // 需要延迟播放
            String delayKey = rule.getPropertyName() + "_" + rule.getVoiceId();
            pendingDelays.put(delayKey, false); // 不取消

            Log.d(TAG, "延迟 " + rule.getDelayMs() + "ms 后播放: " + voiceItem.getTitle());
            mainHandler.postDelayed(() -> {
                if (Boolean.TRUE.equals(pendingDelays.get(delayKey))) {
                    Log.d(TAG, "延迟任务已取消: " + delayKey);
                    return;
                }
                pendingDelays.remove(delayKey);
                doPlay(voiceItem, rule.isExterior());
            }, rule.getDelayMs());
        } else {
            // 立即播放
            doPlay(voiceItem, rule.isExterior());
        }
    }

    private void doPlay(VoiceItem voiceItem, boolean isExterior) {
        // 确保音频已预加载
        voicePlayer.preloadVoice(voiceItem);
        // 设置声道
        voicePlayer.setChannel(isExterior ? VoicePlayer.Channel.OUTSIDE : VoicePlayer.Channel.INSIDE);
        // 播放
        voicePlayer.play(voiceItem, isExterior);
        Log.d(TAG, "联动触发播放: " + voiceItem.getTitle() + (isExterior ? " (车外)" : " (车内)"));
    }

    /**
     * 取消所有待处理的延迟任务
     */
    public void cancelAllPending() {
        for (String key : pendingDelays.keySet()) {
            pendingDelays.put(key, true);
        }
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "所有待处理延迟任务已取消");
    }

    /**
     * 释放资源
     */
    public void release() {
        cancelAllPending();
        rules.clear();
        voiceMap.clear();
        lastTriggerTimeMap.clear();
    }
}
