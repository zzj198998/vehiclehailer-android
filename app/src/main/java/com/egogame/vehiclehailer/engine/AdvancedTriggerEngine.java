package com.egogame.vehiclehailer.engine;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.egogame.vehiclehailer.action.Action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高级联动引擎（用户可选开关启用）
 *
 * 完全独立于现有的 VehicleEventTrigger。
 * VehicleEventTrigger 处理简单规则（属性→一个音频），
 * AdvancedTriggerEngine 处理高级规则（条件组→动作序列）。
 *
 * 用户开关位置：联动规则编辑界面 → "启用高级联动"复选框
 * - 未勾选：使用现有 TriggerRule（一对一简单模式）
 * - 勾选后：使用 AdvancedTriggerRule（多条件+多动作组合模式）
 *
 * 使用方式（由 UI 调用）：
 *   AdvancedTriggerEngine engine = new AdvancedTriggerEngine();
 *   engine.addRule(advancedRule);
 *   engine.onPropertyChanged(propName, oldVal, newVal); // 手动传入属性变化
 */
public class AdvancedTriggerEngine {

    private static final String TAG = "AdvancedTriggerEngine";

    // 所有高级规则
    private final List<AdvancedTriggerRule> advancedRules = new ArrayList<>();

    // 车辆当前状态快照（属性名→当前值，供条件组求值用）
    private final ConcurrentHashMap<String, String> stateSnapshot = new ConcurrentHashMap<>();

    // 总开关（用户控制）
    private boolean masterEnabled = false;

    // 防抖
    private final ConcurrentHashMap<String, Long> debounceMap = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_MS = 500;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 接收属性变化事件
     * 由 UI 层或状态管理器调用
     */
    public void onPropertyChanged(String propertyName, String oldValue, String newValue) {
        if (!masterEnabled) return;

        // 更新状态快照
        stateSnapshot.put(propertyName, newValue);

        // 防抖
        long now = System.currentTimeMillis();
        Long last = debounceMap.get(propertyName);
        if (last != null && (now - last) < DEBOUNCE_MS) return;
        debounceMap.put(propertyName, now);

        // 遍历所有启用的高级规则
        for (AdvancedTriggerRule rule : advancedRules) {
            if (!rule.isEnabled()) continue;

            // 用状态快照评估条件组
            if (rule.evaluate(stateSnapshot)) {
                // 条件满足，执行动作序列
                executeActions(rule);
            }
        }
    }

    /**
     * 批量设置状态快照（用于初始化/恢复）
     */
    public void setStateSnapshot(Map<String, String> snapshot) {
        stateSnapshot.clear();
        if (snapshot != null) {
            stateSnapshot.putAll(snapshot);
        }
    }

    // ---- 规则管理 ----

    /**
     * 添加一条高级联动规则
     */
    public void addRule(AdvancedTriggerRule rule) {
        if (rule != null) {
            advancedRules.add(rule);
            Log.d(TAG, "添加高级规则: " + rule.getRuleName());
        }
    }

    /**
     * 批量添加高级规则
     */
    public void addRules(List<AdvancedTriggerRule> newRules) {
        if (newRules != null) {
            advancedRules.addAll(newRules);
            Log.d(TAG, "批量添加 " + newRules.size() + " 条高级规则");
        }
    }

    /**
     * 移除指定规则
     */
    public boolean removeRule(String ruleName) {
        return advancedRules.removeIf(r -> ruleName.equals(r.getRuleName()));
    }

    /**
     * 清除所有高级规则
     */
    public void clearRules() {
        advancedRules.clear();
        Log.d(TAG, "所有高级规则已清除");
    }

    /**
     * 获取所有高级规则
     */
    public List<AdvancedTriggerRule> getRules() {
        return new ArrayList<>(advancedRules);
    }

    /**
     * 获取高级规则数量
     */
    public int getRuleCount() {
        return advancedRules.size();
    }

    // ---- 总开关 ----

    /**
     * 启用/禁用高级联动引擎
     */
    public void setMasterEnabled(boolean enabled) {
        this.masterEnabled = enabled;
        Log.d(TAG, (enabled ? "开启" : "关闭") + "高级联动引擎");
    }

    public boolean isMasterEnabled() {
        return masterEnabled;
    }

    // ---- 动作执行 ----

    /**
     * 执行规则的动作序列
     */
    private void executeActions(AdvancedTriggerRule rule) {
        Log.d(TAG, "触发高级规则: " + rule.getRuleName()
                + ", 动作数=" + rule.getActionCount()
                + ", 模式=" + rule.getActionMode());

        if (rule.getActionCount() == 0) return;

        switch (rule.getActionMode()) {
            case SEQUENTIAL:
                executeSequential(rule);
                break;
            case PARALLEL:
                executeParallel(rule);
                break;
        }
    }

    /**
     * 顺序执行：每个Action依次执行
     */
    private void executeSequential(AdvancedTriggerRule rule) {
        // 用Handler实现顺序执行，每完成一个执行下一个
        final int[] index = {0};
        final List<Action> actions = rule.getActions();

        Runnable nextAction = new Runnable() {
            @Override
            public void run() {
                if (index[0] >= actions.size()) return;
                Action action = actions.get(index[0]++);
                if (action != null) {
                    Log.d(TAG, "顺序执行 [" + index[0] + "/" + actions.size()
                            + "] " + action.getDescription());
                    action.execute();
                }
                // 继续执行下一个
                mainHandler.postDelayed(this, 100); // 100ms间隔
            }
        };
        mainHandler.post(nextAction);
    }

    /**
     * 并行执行：所有Action同时启动
     */
    private void executeParallel(AdvancedTriggerRule rule) {
        for (Action action : rule.getActions()) {
            if (action != null) {
                Log.d(TAG, "并行执行: " + action.getDescription());
                action.execute();
            }
        }
    }

    // ---- 资源管理 ----

    /**
     * 释放资源
     */
    public void release() {
        advancedRules.clear();
        stateSnapshot.clear();
        debounceMap.clear();
        mainHandler.removeCallbacksAndMessages(null);
        masterEnabled = false;
        Log.d(TAG, "高级联动引擎已释放");
    }
}
