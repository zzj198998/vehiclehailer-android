package com.egogame.vehiclehailer.action;

/**
 * 播放音频动作
 * 支持播放指定音频文件/资源，可设置通道和音量
 * 对应鱼蛋的 PlayCardAction，但改用类型安全字段
 */
public class PlayAudioAction extends VehicleActionBase {

    private String audioPath;       // 音频文件路径
    private int rawResId = -1;      // 资源ID（-1表示不使用资源）
    private int volume = 80;        // 音量 0-100
    private String channelName;     // 通道名称（如"车内(媒体)"）

    public PlayAudioAction() {
        super("播放音频");
    }

    public PlayAudioAction(String audioPath, int volume) {
        super("播放音频");
        this.audioPath = audioPath;
        this.volume = volume;
    }

    public PlayAudioAction(int rawResId, int volume) {
        super("播放音频");
        this.rawResId = rawResId;
        this.volume = volume;
    }

    @Override
    public ActionType getActionType() {
        return ActionType.PLAY_AUDIO;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder("播放");
        if (rawResId != -1) {
            sb.append("资源ID=").append(rawResId);
        } else if (audioPath != null) {
            sb.append("文件=").append(audioPath);
        } else {
            sb.append("(未指定)");
        }
        sb.append(" 音量=").append(volume).append("%");
        if (channelName != null) sb.append(" 通道=").append(channelName);
        return sb.toString();
    }

    @Override
    public boolean execute() {
        // 实际播放由外部AudioRouter驱动
        // 此处仅验证参数合法性
        if (rawResId == -1 && (audioPath == null || audioPath.isEmpty())) {
            return false;
        }
        return true;
    }

    // ---- Getter/Setter ----

    public String getAudioPath() { return audioPath; }
    public void setAudioPath(String audioPath) { this.audioPath = audioPath; }

    public int getRawResId() { return rawResId; }
    public void setRawResId(int rawResId) { this.rawResId = rawResId; }

    public boolean hasRawResId() { return rawResId != -1; }

    public int getVolume() { return volume; }
    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
    }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
}
