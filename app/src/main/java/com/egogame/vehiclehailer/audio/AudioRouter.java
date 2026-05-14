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

    // 声道通道
    public enum SpeakerChannel {
        INSIDE,     // 车内
        OUTSIDE,    // 车外
        BOTH        // 双通道
    }

    // 声音源路由配置
    public static class SoundRouteConfig {
        public SpeakerChannel channel;
        public int volume;          // 0-100
        public boolean noiseReduction;
        public boolean voiceEnhance;
        public boolean fixedOutside; // true表示不可更改通道

        public SoundRouteConfig(SpeakerChannel channel, int volume,
                                boolean noiseReduction, boolean voiceEnhance,
                                boolean fixedOutside) {
            this.channel = channel;
            this.volume = volume;
            this.noiseReduction = noiseReduction;
            this.voiceEnhance = voiceEnhance;
            this.fixedOutside = fixedOutside;
        }

        public SoundRouteConfig copy() {
            return new SoundRouteConfig(channel, volume, noiseReduction, voiceEnhance, fixedOutside);
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
     * 简化版：只设置音量
     */
    public void setVolume(SoundSource source, int volume) {
        SoundRouteConfig config = routeConfigs.get(source);
        if (config == null) return;
        setRouteConfig(source, config.channel, volume, config.noiseReduction, config.voiceEnhance);
    }

    /**
     * 播放PCM音频数据（用于TTS喊话等动态生成的音频）
     */
    public void playPcm(SoundSource source, byte[] pcmData, int sampleRate) {
        stop(source);

        SoundRouteConfig config = routeConfigs.get(source);
        if (config == null) return;

        int channelConfig = (config.channel == SpeakerChannel.BOTH)
                ? AudioFormat.CHANNEL_OUT_STEREO
                : AudioFormat.CHANNEL_OUT_MONO;

        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        int streamType = getStreamType(config.channel);

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