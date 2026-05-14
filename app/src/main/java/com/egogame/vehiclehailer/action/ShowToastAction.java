package com.egogame.vehiclehailer.action;

/**
 * 显示Toast提示动作 — 鱼蛋没有此独立Action
 * 用于在界面上短暂显示一条消息
 */
public class ShowToastAction extends VehicleActionBase {

    /** Toast显示时长 */
    public enum ToastDuration {
        SHORT,  // 短时显示（约2秒）
        LONG    // 长时显示（约3.5秒）
    }

    private String message;             // 提示消息
    private ToastDuration duration = ToastDuration.SHORT;
    private boolean isWarning = false;  // 是否警告样式（鱼蛋没有）

    public ShowToastAction() {
        super("显示提示");
    }

    public ShowToastAction(String message) {
        super("显示提示");
        this.message = message;
    }

    public ShowToastAction(String message, ToastDuration duration) {
        super("显示提示");
        this.message = message;
        this.duration = duration;
    }

    @Override
    public ActionType getActionType() {
        return ActionType.SHOW_TOAST;
    }

    @Override
    public String getDescription() {
        String preview = message;
        if (preview != null && preview.length() > 30) {
            preview = preview.substring(0, 30) + "...";
        }
        return "显示提示: \"" + preview + "\""
                + (duration == ToastDuration.LONG ? " (长时)" : "")
                + (isWarning ? " ⚠️" : "");
    }

    @Override
    public boolean execute() {
        return message != null && !message.trim().isEmpty();
    }

    // ---- Getter/Setter ----

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public ToastDuration getDuration() { return duration; }
    public void setDuration(ToastDuration duration) { this.duration = duration; }

    public boolean isWarning() { return isWarning; }
    public void setWarning(boolean warning) { isWarning = warning; }
}
