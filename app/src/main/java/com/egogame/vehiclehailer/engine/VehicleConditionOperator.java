package com.egogame.vehiclehailer.engine;

/**
 * 条件逻辑运算符
 * 对应鱼蛋的 VehicleConditionOperator（只有AND/OR）
 * 我们增加了 NOT，支持更灵活的嵌套逻辑
 */
public enum VehicleConditionOperator {
    AND,    // 所有子条件都必须满足
    OR,     // 任意一个子条件满足即可
    NOT     // 取反（仅对单一条件有效，鱼蛋没有）
}
