package com.egogame.vehiclehailer.engine;

/**
 * 联动规则
 * 定义 "当某个属性从某值变为某值时，触发播放指定的音频"
 */
public class TriggerRule {
    private final String propertyName;     // 监听的属性名，如 "doorLeft"
    private final String oldValue;          // 旧值（可为表示任意）
    private final String newValue;          // 新值（必须匹配才触发）
    private final int voiceId;              // 要播放的音频ID
    private final boolean isExterior;       // true=车外喇叭，false=车内扬声器
    private final int delayMs;              // 延迟播放（毫秒）
    private final boolean enabled;          // 是否启用

    public TriggerRule(String propertyName, String oldValue, String newValue,
                       int voiceId, boolean isExterior, int delayMs, boolean enabled) {
        this.propertyName = propertyName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.voiceId = voiceId;
        this.isExterior = isExterior;
        this.delayMs = delayMs;
        this.enabled = enabled;
    }

    public String getPropertyName() { return propertyName; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public int getVoiceId() { return voiceId; }
    public boolean isExterior() { return isExterior; }
    public int getDelayMs() { return delayMs; }
    public boolean isEnabled() { return enabled; }

    @Override
    public String toString() {
        return "TriggerRule{" + propertyName + ": " +
                (oldValue != null ? oldValue + "→" : "任意→") + newValue +
                " → 音频#" + voiceId +
                (isExterior ? " 车外" : " 车内") +
                (delayMs > 0 ? " 延迟" + delayMs + "ms" : "") +
                (enabled ? "" : " 禁用") + "}";
    }
}
