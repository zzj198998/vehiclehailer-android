package com.egogame.vehiclehailer.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 条件组检查器 — 鱼蛋没有此独立类
 *
 * 支持多个条件通过 AND/OR/NOT 组合求值，
 * 直接与 VehicleStateManager 配合使用。
 *
 * 用法：
 *   VehicleConditionChecker checker = new VehicleConditionChecker(operator);
 *   checker.addCondition(cond1);
 *   checker.addCondition(cond2);
 *   boolean result = checker.evaluate(stateMap);  // 从车辆状态映射中取值
 */
public class VehicleConditionChecker {

    private VehicleConditionOperator operator = VehicleConditionOperator.AND;
    private final List<VehicleCondition> conditions = new ArrayList<>();
    private boolean negate = false; // 整体取反（配合NOT运算符）

    public VehicleConditionChecker() {}

    public VehicleConditionChecker(VehicleConditionOperator operator) {
        this.operator = operator;
    }

    public VehicleConditionChecker(VehicleConditionOperator operator, boolean negate) {
        this.operator = operator;
        this.negate = negate;
    }

    // ---- 条件管理 ----

    public void addCondition(VehicleCondition condition) {
        if (condition != null) {
            conditions.add(condition);
        }
    }

    public void addConditions(List<VehicleCondition> conditions) {
        if (conditions != null) {
            this.conditions.addAll(conditions);
        }
    }

    public void removeCondition(VehicleCondition condition) {
        conditions.remove(condition);
    }

    public void clearConditions() {
        conditions.clear();
    }

    public List<VehicleCondition> getConditions() {
        return new ArrayList<>(conditions);
    }

    public int getConditionCount() {
        return conditions.size();
    }

    /**
     * 评估所有条件 — 从 Map 中取值检查
     * @param stateMap 车辆状态映射（属性名 → 当前值）
     * @return 是否满足所有条件
     */
    public boolean evaluate(Map<String, String> stateMap) {
        if (conditions.isEmpty()) {
            return true; // 无条件默认通过
        }

        boolean result;
        switch (operator) {
            case AND:
                result = evaluateAnd(stateMap);
                break;
            case OR:
                result = evaluateOr(stateMap);
                break;
            case NOT:
                // NOT模式下只检查第一个条件
                result = conditions.isEmpty() || !evaluateSingle(stateMap, conditions.get(0));
                break;
            default:
                result = evaluateAnd(stateMap);
        }

        return negate ? !result : result;
    }

    /**
     * 评估所有条件 — 直接传入值（由调用者解析属性名）
     * @param propertyValue 属性名对应的当前值
     * @return 是否满足所有条件
     */
    public boolean evaluate(String propertyValue) {
        if (conditions.isEmpty()) {
            return true;
        }

        boolean result;
        switch (operator) {
            case AND:
                result = conditions.stream().allMatch(c -> c.check(propertyValue));
                break;
            case OR:
                result = conditions.stream().anyMatch(c -> c.check(propertyValue));
                break;
            case NOT:
                result = conditions.isEmpty() || !conditions.get(0).check(propertyValue);
                break;
            default:
                result = conditions.stream().allMatch(c -> c.check(propertyValue));
        }

        return negate ? !result : result;
    }

    private boolean evaluateAnd(Map<String, String> stateMap) {
        for (VehicleCondition condition : conditions) {
            if (!evaluateSingle(stateMap, condition)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateOr(Map<String, String> stateMap) {
        for (VehicleCondition condition : conditions) {
            if (evaluateSingle(stateMap, condition)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateSingle(Map<String, String> stateMap, VehicleCondition condition) {
        String propertyName = condition.getPropertyName();
        String actualValue = stateMap.get(propertyName);
        return condition.check(actualValue);
    }

    // ---- 设置 ----

    public VehicleConditionOperator getOperator() { return operator; }
    public void setOperator(VehicleConditionOperator operator) { this.operator = operator; }

    public boolean isNegate() { return negate; }
    public void setNegate(boolean negate) { this.negate = negate; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (negate) sb.append("NOT ");
        sb.append("(").append(operator.name()).append(") [");
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(conditions.get(i).toString());
        }
        sb.append("]");
        return sb.toString();
    }
}
