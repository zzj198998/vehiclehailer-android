package com.egogame.vehiclehailer.model;

import com.egogame.vehiclehailer.audio.AudioRouter;

/**
 * 统一音频数据模型 — 比鱼蛋的PlayVoiceData更强
 *
 * 鱼蛋 PlayVoiceData 字段：
 *   channel(String), deviceId, isLoop, isOutside,
 *   musicPath, name, tag, volume, streamType(3), focusType(1)
 *
 * 我们增强：
 * ① 使用 AudioChannelProfile 替代简单的 streamType 数字
 * ② 支持 SoundSource 枚举（与 AudioRouter 联动）
 * ③ 新增 durationMs/priority/fadeIn/fadeOut
 * ④ 支持 PCM 数据直接播放（鱼蛋不支持）
 * ⑤ 支持资源ID和文件路径双模式
 */
public class PlayVoiceData {

    // ---- 基础标识 ----

    private String id;              // 唯一标识
    private String name;            // 名称
    private String tag;             // 标签（用于按tag停止）

    // ---- 音频来源（三选一） ----

    private String filePath;        // 音频文件路径
    private int rawResId = -1;      // 资源文件ID (-1表示不使用)
    private byte[] pcmData;         // PCM原始音频数据
    private int pcmSampleRate = 44100; // PCM采样率

    // ---- 路由参数 ----

    private AudioRouter.SoundSource soundSource;    // 音源类型
    private int volume = 80;        // 音量 0-100
    private boolean isLoop = false; // 是否循环播放
    private boolean fadeIn = false; // 是否渐入（鱼蛋没有）
    private boolean fadeOut = false; // 是否渐出（鱼蛋没有）

    // ---- 高级参数 ----

    private int priority = 0;       // 优先级（越高越优先，鱼蛋没有）
    private int durationMs = -1;    // 播放时长限制（-1不限，鱼蛋没有）
    private long delayBeforePlay = 0; // 播放前延迟（ms，鱼蛋没有）

    // ---- 运行时状态 ----

    private transient long createdAt;   // 创建时间戳
    private transient boolean isPlaying;

    public PlayVoiceData() {
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 完整构造函数
     */
    public PlayVoiceData(String id, String name, String filePath,
                         AudioRouter.SoundSource soundSource, int volume) {
        this();
        this.id = id;
        this.name = name;
        this.filePath = filePath;
        this.soundSource = soundSource;
        this.volume = volume;
    }

    /**
     * 从资源ID构造
     */
    public PlayVoiceData(String id, String name, int rawResId,
                         AudioRouter.SoundSource soundSource, int volume) {
        this();
        this.id = id;
        this.name = name;
        this.rawResId = rawResId;
        this.soundSource = soundSource;
        this.volume = volume;
    }

    /**
     * 从PCM数据构造
     */
    public PlayVoiceData(String id, String name, byte[] pcmData, int sampleRate,
                         AudioRouter.SoundSource soundSource, int volume) {
        this();
        this.id = id;
        this.name = name;
        this.pcmData = pcmData;
        this.pcmSampleRate = sampleRate;
        this.soundSource = soundSource;
        this.volume = volume;
    }

    // ---- 判断音频来源类型 ----

    public boolean isFileSource() { return filePath != null && !filePath.isEmpty(); }
    public boolean isRawResSource() { return rawResId != -1; }
    public boolean isPcmSource() { return pcmData != null && pcmData.length > 0; }

    /**
     * 检查参数是否有效
     */
    public boolean isValid() {
        return isFileSource() || isRawResSource() || isPcmSource();
    }

    /**
     * 获取音频数据的简要描述
     */
    public String getSourceDescription() {
        if (isFileSource()) return "文件:" + filePath;
        if (isRawResSource()) return "资源:" + rawResId;
        if (isPcmSource()) return "PCM(" + pcmData.length + "B@" + pcmSampleRate + "Hz)";
        return "未知";
    }

    // ---- Getter/Setter ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public int getRawResId() { return rawResId; }
    public void setRawResId(int rawResId) { this.rawResId = rawResId; }

    public byte[] getPcmData() { return pcmData; }
    public void setPcmData(byte[] pcmData) { this.pcmData = pcmData; }

    public int getPcmSampleRate() { return pcmSampleRate; }
    public void setPcmSampleRate(int pcmSampleRate) {
        this.pcmSampleRate = Math.max(8000, pcmSampleRate);
    }

    public AudioRouter.SoundSource getSoundSource() { return soundSource; }
    public void setSoundSource(AudioRouter.SoundSource soundSource) {
        this.soundSource = soundSource;
    }

    public int getVolume() { return volume; }
    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
    }

    public boolean isLoop() { return isLoop; }
    public void setLoop(boolean loop) { isLoop = loop; }

    public boolean isFadeIn() { return fadeIn; }
    public void setFadeIn(boolean fadeIn) { this.fadeIn = fadeIn; }

    public boolean isFadeOut() { return fadeOut; }
    public void setFadeOut(boolean fadeOut) { this.fadeOut = fadeOut; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public int getDurationMs() { return durationMs; }
    public void setDurationMs(int durationMs) { this.durationMs = durationMs; }

    public long getDelayBeforePlay() { return delayBeforePlay; }
    public void setDelayBeforePlay(long delayBeforePlay) {
        this.delayBeforePlay = Math.max(0, delayBeforePlay);
    }

    public long getCreatedAt() { return createdAt; }
    public boolean isPlaying() { return isPlaying; }
    public void setPlaying(boolean playing) { isPlaying = playing; }

    @Override
    public String toString() {
        return "PlayVoiceData{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", tag='" + tag + '\'' +
                ", source=" + getSourceDescription() +
                ", volume=" + volume +
                ", loop=" + isLoop +
                ", priority=" + priority +
                ", durationMs=" + durationMs +
                '}';
    }
}
