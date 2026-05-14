package com.egogame.vehiclehailer.action;

/**
 * 车辆动作基类 — 比鱼蛋更强的Action体系
 *
 * 鱼蛋只有3种ActionType（PLAY_CARD/DELAY/STOP_AUDIO），
 * 且使用jsonData字符串传递参数，类型不安全。
 *
 * 我们使用泛型+类型安全字段，比鱼蛋：
 * ① 动作类型更多（9种 vs 3种）
 * ② 参数类型安全（直接字段 vs jsonData字符串）
 * ③ 支持执行状态回调
 * ④ 支持执行结果返回
 */
public abstract class VehicleActionBase {

    /** 动作执行状态 */
    public enum ActionStatus {
        PENDING,    // 待执行
        RUNNING,    // 执行中
        SUCCESS,    // 执行成功
        FAILED      // 执行失败
    }

    /** 动作类型枚举（比鱼蛋的3种多出6种） */
    public enum ActionType {
        PLAY_AUDIO,         // 播放音频（鱼蛋的PLAY_CARD升级版）
        STOP_AUDIO,         // 停止音频
        DELAY,              // 延迟等待
        SPEAK_TEXT,         // TTS语音播报（鱼蛋没有）
        VOLUME_CONTROL,     // 音量控制（鱼蛋没有）
        SHOW_TOAST,         // 显示提示（鱼蛋没有）
        SEND_INTENT,        // 发送Intent（鱼蛋没有）
        SWITCH_MODE,        // 切换驾驶模式（鱼蛋没有）
        POWER_OFF           // 一键断电（鱼蛋没有）
    }

    private String actionName;
    private ActionStatus status = ActionStatus.PENDING;
    private ActionCallback callback;

    /** 动作执行回调接口 */
    public interface ActionCallback {
        void onActionStart(VehicleActionBase action);
        void onActionComplete(VehicleActionBase action, boolean success);
    }

    public VehicleActionBase(String actionName) {
        this.actionName = actionName;
    }

    /** 获取动作类型 */
    public abstract ActionType getActionType();

    /** 获取动作描述 */
    public abstract String getDescription();

    /** 执行动作（子类实现具体逻辑） */
    public abstract boolean execute();

    // ---- 通用方法 ----

    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }

    public ActionStatus getStatus() { return status; }

    public void setCallback(ActionCallback callback) { this.callback = callback; }

    /** 带回调的执行包装 */
    public boolean executeWithCallback() {
        status = ActionStatus.RUNNING;
        if (callback != null) callback.onActionStart(this);

        boolean result = execute();

        status = result ? ActionStatus.SUCCESS : ActionStatus.FAILED;
        if (callback != null) callback.onActionComplete(this, result);
        return result;
    }

    @Override
    public String toString() {
        return "[" + getActionType().name() + "] " + getDescription()
                + " (状态: " + status.name() + ")";
    }
}
