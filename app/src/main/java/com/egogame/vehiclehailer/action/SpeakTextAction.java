package com.egogame.vehiclehailer.action;

/**
 * TTS语音播报动作 — 鱼蛋没有此独立Action
 * 通过TTS引擎将文字转为语音播报
 */
public class SpeakTextAction extends VehicleActionBase {

    private String text;            // 播报文本
    private String language = "zh"; // 语言
    private float speechRate = 1.0f; // 语速 0.5-2.0
    private float pitch = 1.0f;     // 音调 0.5-2.0
    private int volume = 80;        // 音量 0-100

    public SpeakTextAction() {
        super("语音播报");
    }

    public SpeakTextAction(String text) {
        super("语音播报");
        this.text = text;
    }

    public SpeakTextAction(String text, int volume) {
        super("语音播报");
        this.text = text;
        this.volume = volume;
    }

    @Override
    public ActionType getActionType() {
        return ActionType.SPEAK_TEXT;
    }

    @Override
    public String getDescription() {
        String preview = text;
        if (preview != null && preview.length() > 20) {
            preview = preview.substring(0, 20) + "...";
        }
        return "语音播报: \"" + preview + "\" 音量=" + volume + "%"
                + " 语速=" + speechRate + " 音调=" + pitch;
    }

    @Override
    public boolean execute() {
        return text != null && !text.trim().isEmpty();
    }

    // ---- Getter/Setter ----

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public float getSpeechRate() { return speechRate; }
    public void setSpeechRate(float speechRate) {
        this.speechRate = Math.max(0.5f, Math.min(2.0f, speechRate));
    }

    public float getPitch() { return pitch; }
    public void setPitch(float pitch) {
        this.pitch = Math.max(0.5f, Math.min(2.0f, pitch));
    }

    public int getVolume() { return volume; }
    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
    }
}
