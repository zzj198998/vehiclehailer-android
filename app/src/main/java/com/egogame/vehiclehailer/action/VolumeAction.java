package com.egogame.vehiclehailer.action;

/**
 * 音量控制动作 — 鱼蛋没有此独立Action
 * 支持调节媒体/通话/闹钟等不同音频流的音量
 */
public class VolumeAction extends VehicleActionBase {

    /** 调节方式 */
    public enum VolumeMode {
        SET,        // 设为指定值
        INCREASE,   // 增加
        DECREASE    // 减少
    }

    /** 音频流类型 */
    public enum StreamType {
        MUSIC,      // 媒体音量
        VOICE_CALL, // 通话音量
        ALARM,      // 闹钟音量
        NOTIFICATION, // 通知音量
        SYSTEM      // 系统音量
    }

    private VolumeMode mode = VolumeMode.SET;
    private StreamType streamType = StreamType.MUSIC;
    private int value = 50;             // SET模式的目标值 0-100
    private int step = 10;              // INCREASE/DECREASE模式的步长

    public VolumeAction() {
        super("音量控制");
    }

    public VolumeAction(StreamType streamType, int value) {
        super("音量控制");
        this.mode = VolumeMode.SET;
        this.streamType = streamType;
        this.value = Math.max(0, Math.min(100, value));
    }

    public VolumeAction(VolumeMode mode, StreamType streamType, int step) {
        super("音量控制");
        this.mode = mode;
        this.streamType = streamType;
        this.step = Math.max(1, step);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.VOLUME_CONTROL;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder("音量");
        switch (mode) {
            case SET:
                sb.append("设为").append(value).append("%");
                break;
            case INCREASE:
                sb.append("+").append(step);
                break;
            case DECREASE:
                sb.append("-").append(step);
                break;
        }
        sb.append(" (").append(streamType.name()).append(")");
        return sb.toString();
    }

    @Override
    public boolean execute() {
        return value >= 0 && value <= 100 && step >= 1;
    }

    // ---- Getter/Setter ----

    public VolumeMode getMode() { return mode; }
    public void setMode(VolumeMode mode) { this.mode = mode; }

    public StreamType getStreamType() { return streamType; }
    public void setStreamType(StreamType streamType) { this.streamType = streamType; }

    public int getValue() { return value; }
    public void setValue(int value) {
        this.value = Math.max(0, Math.min(100, value));
    }

    public int getStep() { return step; }
    public void setStep(int step) {
        this.step = Math.max(1, step);
    }
}
