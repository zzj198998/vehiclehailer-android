package com.egogame.vehiclehailer.model;

/**
 * 语音条目
 */
public class VoiceItem {
    public enum VoiceTab {
        CUSTOM("FunBox_VoiceTabCustom", "自定义音效"),
        SYSTEM("FunBox_VoiceTabSystem", "系统音效");

        private final String raw;
        private final String display;

        VoiceTab(String raw, String display) {
            this.raw = raw;
            this.display = display;
        }

        public String getRaw() { return raw; }
        public String getDisplay() { return display; }

        public static VoiceTab fromRaw(String raw) {
            for (VoiceTab tab : values()) {
                if (tab.raw.equals(raw)) return tab;
            }
            return CUSTOM;
        }
    }

    private int id;
    private String title;
    private String path;
    private VoiceTab tab;
    private boolean isPlaying;

    public VoiceItem(int id, String title, String path, VoiceTab tab) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.tab = tab;
        this.isPlaying = false;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getName() { return title; }
    public String getPath() { return path; }
    public String getFilePath() { return path; }
    public VoiceTab getTab() { return tab; }
    public boolean isPlaying() { return isPlaying; }
    public void setPlaying(boolean playing) { isPlaying = playing; }
}
