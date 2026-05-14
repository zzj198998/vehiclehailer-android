package com.egogame.vehiclehailer.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多声道独立音频路由引擎
 * 每种声音源可独立配置：
 * - 播放通道（车内/车外/双通道）
 * - 音量（0-100%）
 * - 音频增强（降噪/人声增强开/关）
 * - 模拟声浪固定车外不可更改
 */
public class AudioRouter {

    private static final String TAG = "AudioRouter";

    // 声音源类型
    public enum SoundSource {
        HAILING,        // 车外喊话
        DOOR_SOUND,     // 开关门音效
        LOCK_SOUND,     // 锁车解锁
        SOUNDWAVE_SIM,  // 模拟声浪（固定车外）
        CUSTOM_AUDIO    // 自定义音效
    }

    // 声道通道（旧的枚举保留用于向后兼容，但实际播放用 AudioChannelProfile）
    public enum SpeakerChannel {
        INSIDE,     // 车内
        OUTSIDE,    // 车外
        BOTH        // 双通道
    }

    /**
     * 音频通道配置文件：用户可测试所有可能的 Android 音频通道组合
     * 不同车型的物理喇叭映射不同，用户通过试听选择正确的选项
     */
    public static class AudioChannelProfile {
        public final String displayName;    // 给用户看的名称
        public final int streamType;         // AudioManager.STREAM_*
        public final int usageType;          // AudioAttributes.USAGE_*
        public final boolean isOutside;      // 是否为车外通道（用于逻辑判断）
        public final boolean isBoth;         // 是否为双通道

        public AudioChannelProfile(String displayName, int streamType, int usageType,
                                   boolean isOutside, boolean isBoth) {
            this.displayName = displayName;
            this.streamType = streamType;
            this.usageType = usageType;
            this.isOutside = isOutside;
            this.isBoth = isBoth;
        }

        /**
         * 获取该配置对应的旧版 SpeakerChannel（兼容老代码）
         */
        public SpeakerChannel toSpeakerChannel() {
            if (isBoth) return SpeakerChannel.BOTH;
            if (isOutside) return SpeakerChannel.OUTSIDE;
            return SpeakerChannel.INSIDE;
        }
    }

    /**
     * 所有可测试的音频通道配置文件
     * 涵盖了 Android 系统中可能映射到不同物理声道的主要通道组合
     */
    public static final AudioChannelProfile[] ALL_CHANNEL_PROFILES = {
        // 车内通道：通常映射到车内音响
        new AudioChannelProfile("车内(媒体)",    AudioManager.STREAM_MUSIC,       AudioAttributes.USAGE_MEDIA,              false, false),
        // 车外通道：可能对应外置喇叭/喊话器，不同车型映射的通道不同
        new AudioChannelProfile("车外(通话)",    AudioManager.STREAM_VOICE_CALL, AudioAttributes.USAGE_VOICE_COMMUNICATION, true,  false),
        new AudioChannelProfile("车外(闹钟)",    AudioManager.STREAM_ALARM,      AudioAttributes.USAGE_ALARM,              true,  false),
        new AudioChannelProfile("车外(通知)",    AudioManager.STREAM_NOTIFICATION,AudioAttributes.USAGE_NOTIFICATION,       true,  false),
        new AudioChannelProfile("车外(铃声)",    AudioManager.STREAM_RING,       AudioAttributes.USAGE_NOTIFICATION,        true,  false),
        new AudioChannelProfile("车外(系统)",    AudioManager.STREAM_SYSTEM,     AudioAttributes.USAGE_GAME,                true,  false),
        new AudioChannelProfile("车外(辅助)",    AudioManager.STREAM_ACCESSIBILITY, AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY, true, false),
        // 双通道：车内车外同时播放
        new AudioChannelProfile("双通道(媒体)",  AudioManager.STREAM_MUSIC,       AudioAttributes.USAGE_MEDIA,              false, true),
        new AudioChannelProfile("双通道(通话)",  AudioManager.STREAM_VOICE_CALL, AudioAttributes.USAGE_VOICE_COMMUNICATION, false, true),
    };

    /**
     * 根据名称查找通道配置文件（用于恢复用户设置）
     */
    public static AudioChannelProfile findProfileByName(String displayName) {
        for (AudioChannelProfile p : ALL_CHANNEL_PROFILES) {
            if (p.displayName.equals(displayName)) return p;
        }
        return ALL_CHANNEL_PROFILES[0]; // 默认返回"车内(媒体)"
    }

    /**
     * 默认的车内通道配置文件索引
     */
    public static final int DEFAULT_CHANNEL_INDEX_INSIDE = 0;   // 车内(媒体)
    public static final int DEFAULT_CHANNEL_INDEX_OUTSIDE = 1;  // 车外(通话)

    // 声音源路由配置
    public static class SoundRouteConfig {
        public SpeakerChannel channel;
        public int volume;          // 0-100
        public boolean noiseReduction;
        public boolean voiceEnhance;
        public boolean fixedOutside; // true表示不可更改通道
        public AudioChannelProfile audioProfile; // 当前选中的通道配置文件

        public SoundRouteConfig(SpeakerChannel channel, int volume,
                                boolean noiseReduction, boolean voiceEnhance,
                                boolean fixedOutside) {
            this.channel = channel;
            this.volume = volume;
            this.noiseReduction = noiseReduction;
            this.voiceEnhance = voiceEnhance;
            this.fixedOutside = fixedOutside;
            // 默认根据 channel 选择对应的配置文件
            this.audioProfile = (channel == SpeakerChannel.OUTSIDE)
                    ? ALL_CHANNEL_PROFILES[DEFAULT_CHANNEL_INDEX_OUTSIDE]
                    : ALL_CHANNEL_PROFILES[DEFAULT_CHANNEL_INDEX_INSIDE];
        }

        public SoundRouteConfig copy() {
            SoundRouteConfig copy = new SoundRouteConfig(channel, volume, noiseReduction, voiceEnhance, fixedOutside);
            copy.audioProfile = this.audioProfile;
            return copy;
        }

        /**
         * 设置音频通道配置文件，同时更新 channel 字段保持兼容
         */
        public void setAudioProfile(AudioChannelProfile profile) {
            this.audioProfile = profile;
            this.channel = profile.toSpeakerChannel();
        }
    }

    private final Context context;
    private final AudioManager audioManager;
    private final Map<SoundSource, SoundRouteConfig> routeConfigs;
    private final Map<SoundSource, AudioTrack> activeTracks;
    private MediaPlayer activeMediaPlayer;
    private SoundSource activeMediaSource;

    public AudioRouter(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.routeConfigs = new ConcurrentHashMap<>();
        this.activeTracks = new ConcurrentHashMap<>();

        // 初始化默认配置
        initDefaultConfigs();
    }

    private void initDefaultConfigs() {
        // 喊话：默认车内，音量80%
        routeConfigs.put(SoundSource.HAILING, new SoundRouteConfig(
                SpeakerChannel.INSIDE, 80, false, true, false));

        // 开关门音效：默认车内，音量60%
        routeConfigs.put(SoundSource.DOOR_SOUND, new SoundRouteConfig(
                SpeakerChannel.INSIDE, 60, false, false, false));

        // 锁车解锁：默认车外，音量70%
        routeConfigs.put(SoundSource.LOCK_SOUND, new SoundRouteConfig(
                SpeakerChannel.OUTSIDE, 70, false, false, false));

        // 模拟声浪：固定车外，音量50%，不可更改通道
        routeConfigs.put(SoundSource.SOUNDWAVE_SIM, new SoundRouteConfig(
                SpeakerChannel.OUTSIDE, 50, false, false, true));

        // 自定义音效：默认车内，音量60%
        routeConfigs.put(SoundSource.CUSTOM_AUDIO, new SoundRouteConfig(
                SpeakerChannel.INSIDE, 60, false, false, false));
    }

    /**
     * 获取指定声音源的当前路由配置（返回副本，修改不影响内部）
     */
    public SoundRouteConfig getRouteConfig(SoundSource source) {
        SoundRouteConfig config = routeConfigs.get(source);
        return config != null ? config.copy() : null;
    }

    /**
     * 设置指定声音源的路由配置
     * 如果该声音源标记为fixedOutside，则忽略channel参数
     */
    public void setRouteConfig(SoundSource source, SpeakerChannel channel, int volume,
                               boolean noiseReduction, boolean voiceEnhance) {
        SoundRouteConfig config = routeConfigs.get(source);
        if (config == null) return;

        if (config.fixedOutside) {
            // 固定车外的声音源，强制使用车外通道
            channel = SpeakerChannel.OUTSIDE;
        }

        config.channel = channel;
        config.volume = Math.max(0, Math.min(100, volume));
        config.noiseReduction = noiseReduction;
        config.voiceEnhance = voiceEnhance;

        Log.d(TAG, "路由配置更新: " + source.name()
                + " 通道=" + channel.name()
                + " 音量=" + config.volume
                + " 降噪=" + noiseReduction
                + " 人声增强=" + voiceEnhance);

        saveConfig(source);
    }

    /**
     * 简化版：只设置通道
     */
    public void setRoute(SoundSource source, SpeakerChannel channel) {
        SoundRouteConfig config = routeConfigs.get(source);
        if (config == null) return;
        setRouteConfig(source, channel, config.volume, config.noiseReduction, config.voiceEnhance);
    }

    /**
     * 通过音频通道配置文件设置通道（带试听测试音）
     * @param source 声音源
     * @param profileIndex ALL_CHANNEL_PROFILES 数组中的索引
     */
    public void setRouteWithProfile(SoundSource source, int profileIndex) {
        if (profileIndex < 0 || profileIndex >= ALL_CHANNEL_PROFILES.length) return;
        SoundRouteConfig config = routeConfigs.get(source);
        if (config == null) return;
        AudioChannelProfile profile = ALL_CHANNEL_PROFILES[profileIndex];
        config.setAudioProfile(profile);
        Log.d(TAG, "通道配置更新: " + source.name() + " → " + profile.displayName
                + " (stream=" + profile.streamType + ", usage=" + profile.usageType + ")");
        saveConfig(source);
        // 自动播放测试音
        playTestTone(source);
    }

    /**
     * 简化版：只设置音量
     */
    public void setVolume(SoundSource source, int volume) {
        SoundRouteConfig config = routeConfigs.get(source);
        if (config == null) return;
        setRouteConfig(source, config.channel, volume, config.noiseReduction, config.voiceEnhance);
    }

    /**
     * 播放测试音（200ms "叮" 声），用于用户判断当前通道是哪个物理喇叭在发声
     */
    public void playTestTone(SoundSource source) {
        SoundRouteConfig config = routeConfigs.get(source);
        if (config == null || config.audioProfile == null) return;
        if (config.fixedOutside && config.audioProfile.displayName.startsWith("车外(通话)")) {
            return; // 固定车外已有默认配置
        }
        // 生成 800Hz 正弦波测试音，200ms，16位PCM
        int sampleRate = 44100;
        int durationMs = 200;
        int numSamples = sampleRate * durationMs / 1000;
        byte[] toneData = new byte[numSamples * 2];
        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * 800.0 * i / sampleRate;
            short sample = (short) (Short.MAX_VALUE * 0.6 * Math.sin(angle));
            toneData[i * 2] = (byte) (sample & 0xff);
            toneData[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }
        playPcmWithProfile(source, toneData, sampleRate, config.audioProfile);
        Log.d(TAG, "测试音播放: " + source.name() + " 通道=" + config.audioProfile.displayName);
    }

    /**
     * 使用指定的音频通道配置播放 PCM 数据
     */
    private void playPcmWithProfile(SoundSource source, byte[] pcmData, int sampleRate, AudioChannelProfile profile) {
        stop(source);

        int channelConfig = (profile.isBoth)
                ? AudioFormat.CHANNEL_OUT_STEREO
                : AudioFormat.CHANNEL_OUT_MONO;

        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);

        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(profile.usageType)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();

        float volume = 0.8f; // 测试音使用80%音量
        track.setVolume(volume);
        track.write(pcmData, 0, pcmData.length);
        track.play();

        activeTracks.put(source, track);
    }

    /**
     * 播放PCM音频数据（用于TTS喊话等动态生成的音频）
     */
    public void playPcm(SoundSource source, byte[] pcmData, int sampleRate) {
        SoundRouteConfig config = routeConfigs.get(source);
        if (config == null) return;
        if (config.audioProfile != null) {
            playPcmWithProfile(source, pcmData, sampleRate, config.audioProfile);
            return;
        }
        // 降级到旧版逻辑
        stop(source);

        int channelConfig = (config.channel == SpeakerChannel.BOTH)
                ? AudioFormat.CHANNEL_OUT_STEREO
                : AudioFormat.CHANNEL_OUT_MONO;

        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);

        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(getUsageType(config.channel))
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();

        float volume = config.volume / 100.0f;
        track.setVolume(volume);
        track.write(pcmData, 0, pcmData.length);
        track.play();

        activeTracks.put(source, track);
        Log.d(TAG, "PCM播放: " + source.name() + " 通道=" + config.channel.name() + " 音量=" + config.volume);
    }

    /**
     * 播放音频文件
     */
    public void playFile(SoundSource source, String filePath) {
        stop(source);

        SoundRouteConfig config = routeConfigs.get(source);
        if (config == null) return;

        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(getUsageType(config.channel))
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());

            player.setDataSource(filePath);
            player.setVolume(config.volume / 100.0f, config.volume / 100.0f);
            player.setOnCompletionListener(mp -> {
                mp.release();
                activeMediaPlayer = null;
                activeMediaSource = null;
            });
            player.prepare();
            player.start();

            activeMediaPlayer = player;
            activeMediaSource = source;
            Log.d(TAG, "文件播放: " + source.name() + " 文件=" + filePath + " 通道=" + config.channel.name());
        } catch (IOException e) {
            Log.e(TAG, "播放文件失败: " + filePath, e);
        }
    }

    /**
     * 播放资源文件
     */
    public void playRaw(SoundSource source, int rawResId) {
        stop(source);

        SoundRouteConfig config = routeConfigs.get(source);
        if (config == null) return;

        try {
            MediaPlayer player = MediaPlayer.create(context, rawResId);
            if (player == null) return;

            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(getUsageType(config.channel))
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());

            player.setVolume(config.volume / 100.0f, config.volume / 100.0f);
            player.setOnCompletionListener(mp -> {
                mp.release();
                activeMediaPlayer = null;
                activeMediaSource = null;
            });
            player.start();

            activeMediaPlayer = player;
            activeMediaSource = source;
        } catch (Exception e) {
            Log.e(TAG, "播放资源失败", e);
        }
    }

    /**
     * 停止指定声音源的播放
     */
    public void stop(SoundSource source) {
        AudioTrack track = activeTracks.remove(source);
        if (track != null) {
            try {
                track.stop();
                track.release();
            } catch (Exception e) {
                Log.e(TAG, "停止AudioTrack失败", e);
            }
        }

        if (activeMediaSource == source && activeMediaPlayer != null) {
            try {
                activeMediaPlayer.stop();
                activeMediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "停止MediaPlayer失败", e);
            }
            activeMediaPlayer = null;
            activeMediaSource = null;
        }
    }

    /**
     * 停止所有播放
     */
    public void stopAll() {
        for (SoundSource source : activeTracks.keySet()) {
            stop(source);
        }
        if (activeMediaPlayer != null) {
            try {
                activeMediaPlayer.stop();
                activeMediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "停止所有播放失败", e);
            }
            activeMediaPlayer = null;
            activeMediaSource = null;
        }
    }

    /**
     * 释放所有资源
     */
    public void release() {
        stopAll();
        routeConfigs.clear();
    }

    /**
     * 检查指定声音源是否正在播放
     */
    public boolean isPlaying(SoundSource source) {
        AudioTrack track = activeTracks.get(source);
        if (track != null && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            return true;
        }
        return activeMediaSource == source && activeMediaPlayer != null && activeMediaPlayer.isPlaying();
    }

    // ---- 内部辅助 ----

    private int getStreamType(SpeakerChannel channel) {
        if (channel == SpeakerChannel.OUTSIDE) {
            return AudioManager.STREAM_MUSIC;
        }
        return AudioManager.STREAM_MUSIC;
    }

    private int getUsageType(SpeakerChannel channel) {
        if (channel == SpeakerChannel.OUTSIDE || channel == SpeakerChannel.BOTH) {
            return AudioAttributes.USAGE_MEDIA;
        }
        return AudioAttributes.USAGE_MEDIA;
    }

    private void saveConfig(SoundSource source) {
        // 配置持久化由外部负责，当前仅保留内存中
    }

    /**
     * 获取所有声音源当前是否正在播放的映射
     */
    public Map<SoundSource, Boolean> getAllPlayingStates() {
        Map<SoundSource, Boolean> states = new HashMap<>();
        for (SoundSource source : SoundSource.values()) {
            states.put(source, isPlaying(source));
        }
        return states;
    }
}