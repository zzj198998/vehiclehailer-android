package com.egogame.vehiclehailer.engine;

import com.egogame.vehiclehailer.action.Action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 高级联动规则（用户可选开关启用）
 *
 * 相比现有的 TriggerRule（一对一：一个属性→一个音频），
 * 高级规则支持：
 * - 条件组（VehicleConditionChecker，支持AND/OR/NOT + 多条件）
 * - 动作序列（多个Action按顺序执行）
 * - 动作执行模式（顺序/并行）
 *
 * 用户默认用简单模式（TriggerRule），
 * 在编辑规则时勾选「启用高级联动」后切换到此模式。
 */
public class AdvancedTriggerRule {

    /** 动作执行模式 */
    public enum ActionMode {
        SEQUENTIAL,  // 顺序执行（一个执行完再执行下一个）
        PARALLEL     // 并行执行（所有同时启动）
    }

    private String ruleName;                    // 规则名称（用户可自定义）
    private boolean enabled = true;             // 是否启用
    private VehicleConditionChecker conditionChecker; // 触发条件组
    private final List<Action> actions = new ArrayList<>(); // 动作序列
    private ActionMode actionMode = ActionMode.SEQUENTIAL; // 执行模式

    /**
     * 简易构造：仅指定规则名
     */
    public AdvancedTriggerRule(String ruleName) {
        this.ruleName = ruleName;
        this.conditionChecker = new VehicleConditionChecker();
    }

    /**
     * 完整构造
     */
    public AdvancedTriggerRule(String ruleName, VehicleConditionChecker conditionChecker,
                               List<Action> actions, ActionMode actionMode, boolean enabled) {
        this.ruleName = ruleName;
        this.conditionChecker = conditionChecker != null ? conditionChecker : new VehicleConditionChecker();
        if (actions != null) this.actions.addAll(actions);
        this.actionMode = actionMode != null ? actionMode : ActionMode.SEQUENTIAL;
        this.enabled = enabled;
    }

    // ---- Getter/Setter ----

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public VehicleConditionChecker getConditionChecker() { return conditionChecker; }
    public void setConditionChecker(VehicleConditionChecker conditionChecker) {
        this.conditionChecker = conditionChecker;
    }

    public ActionMode getActionMode() { return actionMode; }
    public void setActionMode(ActionMode actionMode) {
        this.actionMode = actionMode != null ? actionMode : ActionMode.SEQUENTIAL;
    }

    public List<Action> getActions() { return new ArrayList<>(actions); }
    public int getActionCount() { return actions.size(); }

    // ---- 动作管理 ----

    public void addAction(Action action) {
        if (action != null) actions.add(action);
    }

    public void addActions(List<Action> newActions) {
        if (newActions != null) actions.addAll(newActions);
    }

    public boolean removeAction(Action action) {
        return actions.remove(action);
    }

    public void clearActions() {
        actions.clear();
    }

    /**
     * 检查是否满足触发条件
     * @param stateMap 车辆当前所有属性状态
     * @return true 表示条件满足，应触发执行
     */
    public boolean evaluate(Map<String, String> stateMap) {
        if (!enabled) return false;
        return conditionChecker.evaluate(stateMap);
    }

    /**
     * 检查是否满足触发条件（直接传属性值）
     */
    public boolean evaluate(String propertyValue) {
        if (!enabled) return false;
        return conditionChecker.evaluate(propertyValue);
    }

    @Override
    public String toString() {
        return "AdvancedTriggerRule{" +
                "name='" + ruleName + '\'' +
                ", enabled=" + enabled +
                ", actions=" + actions.size() +
                ", mode=" + actionMode +
                ", conditions=" + conditionChecker.getConditionCount() +
                '}';
    }
}
