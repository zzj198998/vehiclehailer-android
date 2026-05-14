package com.egogame.vehiclehailer.action;

/**
 * 停止音频动作
 * 对应鱼蛋的 StopAudioAction，但支持更灵活的停止策略
 * 可以按tag停止特定音频，或停止所有
 */
public class StopAudioAction extends VehicleActionBase {

    /** 停止范围 */
    public enum StopScope {
        ALL,        // 停止所有音频
        BY_TAG,     // 停止指定tag的音频
        BY_SOURCE   // 停止指定音频源
    }

    private StopScope stopScope = StopScope.ALL;
    private String audioTag;        // 按tag停止时的tag值
    private String sourceName;      // 按音频源停止时的源名称
    private boolean fadeOut = false; // 是否渐出（鱼蛋没有）

    public StopAudioAction() {
        super("停止音频");
    }

    public StopAudioAction(String audioTag) {
        super("停止音频");
        this.stopScope = StopScope.BY_TAG;
        this.audioTag = audioTag;
    }

    public StopAudioAction(StopScope stopScope) {
        super("停止音频");
        this.stopScope = stopScope;
    }

    @Override
    public ActionType getActionType() {
        return ActionType.STOP_AUDIO;
    }

    @Override
    public String getDescription() {
        switch (stopScope) {
            case ALL:
                return "停止所有音频" + (fadeOut ? " (渐出)" : "");
            case BY_TAG:
                return "停止音频[tag=" + audioTag + "]" + (fadeOut ? " (渐出)" : "");
            case BY_SOURCE:
                return "停止音频源[" + sourceName + "]" + (fadeOut ? " (渐出)" : "");
            default:
                return "停止音频";
        }
    }

    @Override
    public boolean execute() {
        if (stopScope == StopScope.BY_TAG && (audioTag == null || audioTag.isEmpty())) {
            return false;
        }
        if (stopScope == StopScope.BY_SOURCE && (sourceName == null || sourceName.isEmpty())) {
            return false;
        }
        return true;
    }

    // ---- Getter/Setter ----

    public StopScope getStopScope() { return stopScope; }
    public void setStopScope(StopScope stopScope) { this.stopScope = stopScope; }

    public String getAudioTag() { return audioTag; }
    public void setAudioTag(String audioTag) { this.audioTag = audioTag; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public boolean isFadeOut() { return fadeOut; }
    public void setFadeOut(boolean fadeOut) { this.fadeOut = fadeOut; }
}
