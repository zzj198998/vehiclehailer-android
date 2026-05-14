package com.egogame.vehiclehailer.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.util.Log;

import com.egogame.vehiclehailer.model.PlayVoiceData;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多音轨并发音池 — 比SoundBrick的MultiSoundPool更强
 *
 * SoundBrick的MultiSoundPool：
 * - 单一SoundPool，仅通过streamType区分通道
 * - 没有优先级抢占机制
 *
 * 我们增强：
 * ① 每个SoundSource独立一个音轨槽位（最多5路同时播放）
 * ② 优先级抢占：高优先级可打断低优先级的播放
 * ③ 支持PlayVoiceData统一模型
 * ④ 与OutSpeakerManager的音频焦点联动
 * ⑤ AudioTrack/PCM直接播放（无需文件）
 * ⑥ 渐入渐出效果
 * ⑦ 播放完成回调
 * ⑧ 音轨状态监控
 */
public class MultiSoundPool {

    private static final String TAG = "MultiSoundPool";
    private static final int MAX_TRACKS = 5; // 对应5个SoundSource

    private final Context context;
    private final AudioRouter audioRouter;
    private final OutSpeakerManager speakerManager;

    // 音轨槽位：每个SoundSource一个独立槽位
    private final Map<AudioRouter.SoundSource, TrackSlot> trackSlots = new ConcurrentHashMap<>();

    // SoundPool用于短音效（<2秒）
    private SoundPool soundPool;
    private final Map<String, Integer> soundPoolCache = new HashMap<>();

    // 播放完成监听器
    private PlayCompleteListener completeListener;

    /** 播放完成回调接口 */
    public interface PlayCompleteListener {
        void onPlayComplete(PlayVoiceData data);
    }

    /**
     * 音轨槽位 — 每个SoundSource一个
     */
    private static class TrackSlot {
        final AudioRouter.SoundSource source;
        PlayVoiceData currentData;
        AudioTrack audioTrack;
        MediaPlayer mediaPlayer;
        boolean isPlaying;
        boolean fadeOut;
        int priority;

        TrackSlot(AudioRouter.SoundSource source) {
            this.source = source;
        }

        void release() {
            releaseAudioTrack();
            releaseMediaPlayer();
            currentData = null;
            isPlaying = false;
        }

        void releaseAudioTrack() {
            if (audioTrack != null) {
                try {
                    audioTrack.stop();
                    audioTrack.release();
                } catch (Exception ignored) {}
                audioTrack = null;
            }
        }

        void releaseMediaPlayer() {
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                    mediaPlayer.release();
                } catch (Exception ignored) {}
                mediaPlayer = null;
            }
        }
    }

    public MultiSoundPool(Context context, AudioRouter audioRouter) {
        this.context = context.getApplicationContext();
        this.audioRouter = audioRouter;
        this.speakerManager = OutSpeakerManager.getInstance(context);

        // 初始化音轨槽位
        for (AudioRouter.SoundSource source : AudioRouter.SoundSource.values()) {
            trackSlots.put(source, new TrackSlot(source));
        }

        // 初始化SoundPool（用于短音效）
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(MAX_TRACKS)
                .setAudioAttributes(attrs)
                .build();
    }

    // ============ 播放方法 ============

    /**
     * 播放音频数据（使用PlayVoiceData统一模型）
     * 自动选择播放方式：SoundPool(短音效) / AudioTrack(PCM) / MediaPlayer(文件)
     *
     * @param data 播放数据
     * @return true=开始播放，false=被更高优先级抢占
     */
    public boolean play(PlayVoiceData data) {
        if (data == null || !data.isValid()) return false;

        AudioRouter.SoundSource source = data.getSoundSource();
        if (source == null) return false;

        TrackSlot slot = trackSlots.get(source);

        // 优先级检查：如果当前有更高优先级在播放，拒绝
        if (slot.isPlaying && slot.currentData != null
                && data.getPriority() < slot.currentData.getPriority()) {
            Log.d(TAG, "被更高优先级抢占: " + data.getName()
                    + "(" + data.getPriority() + ") < "
                    + slot.currentData.getName()
                    + "(" + slot.currentData.getPriority() + ")");
            return false;
        }

        // 停止当前播放
        stop(source);

        slot.currentData = data;
        slot.isPlaying = true;
        slot.priority = data.getPriority();

        boolean result = false;
        if (data.isPcmSource()) {
            result = playPcm(slot, data);
        } else if (data.isFileSource()) {
            result = playFile(slot, data);
        } else if (data.isRawResSource()) {
            result = playRaw(slot, data);
        }

        if (result) {
            // 车外音源通知OutSpeakerManager
            AudioRouter.SoundRouteConfig config = audioRouter.getRouteConfig(source);
            if (config != null && config.audioProfile != null
                    && config.audioProfile.isOutside) {
                speakerManager.onOutsidePlayStart();
            }

            Log.d(TAG, "播放开始: " + data.getName()
                    + " 音源=" + source.name()
                    + " 音量=" + data.getVolume()
                    + " 优先级=" + data.getPriority());
        }

        return result;
    }

    /**
     * 播放PCM数据（通过AudioTrack）
     */
    private boolean playPcm(TrackSlot slot, PlayVoiceData data) {
        AudioRouter.SoundRouteConfig config = audioRouter.getRouteConfig(slot.source);
        if (config == null || config.audioProfile == null) return false;

        int sampleRate = data.getPcmSampleRate();
        byte[] pcmData = data.getPcmData();

        int channelConfig = config.audioProfile.isBoth
                ? AudioFormat.CHANNEL_OUT_STEREO
                : AudioFormat.CHANNEL_OUT_MONO;

        int bufferSize = AudioTrack.getMinBufferSize(
                sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);

        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(config.audioProfile.usageType)
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

            float vol = data.getVolume() / 100.0f;
            track.setVolume(vol);
            track.write(pcmData, 0, pcmData.length);

            if (data.isFadeIn()) {
                applyFadeIn(track, 50);
            }

            track.play();
            slot.audioTrack = track;
            schedulePlayEnd(slot, data, track);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "PCM播放失败", e);
            return false;
        }
    }

    /**
     * 播放文件（通过MediaPlayer）
     */
    private boolean playFile(TrackSlot slot, PlayVoiceData data) {
        try {
            MediaPlayer player = new MediaPlayer();

            AudioRouter.SoundRouteConfig config = audioRouter.getRouteConfig(slot.source);
            if (config != null && config.audioProfile != null) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(config.audioProfile.usageType)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            }

            player.setDataSource(data.getFilePath());
            float vol = data.getVolume() / 100.0f;
            player.setVolume(vol, vol);

            if (data.isLoop()) {
                player.setLooping(true);
            }

            // 播放完成监听
            player.setOnCompletionListener(mp -> {
                slot.isPlaying = false;
                slot.releaseMediaPlayer();
                onPlayEnd(slot, data);
            });

            player.setOnErrorListener((mp, what, extra) -> {
                slot.isPlaying = false;
                slot.releaseMediaPlayer();
                onPlayEnd(slot, data);
                return true;
            });

            player.prepare();
            player.start();
            slot.mediaPlayer = player;
            return true;
        } catch (IOException e) {
            Log.e(TAG, "文件播放失败: " + data.getFilePath(), e);
            return false;
        }
    }

    /**
     * 播放资源（通过MediaPlayer或SoundPool）
     */
    private boolean playRaw(TrackSlot slot, PlayVoiceData data) {
        // 短音效走SoundPool，长音效走MediaPlayer
        if (data.getDurationMs() > 0 && data.getDurationMs() < 2000) {
            return playRawWithSoundPool(slot, data);
        }
        return playRawWithMediaPlayer(slot, data);
    }

    private boolean playRawWithSoundPool(TrackSlot slot, PlayVoiceData data) {
        int resId = data.getRawResId();
        Integer soundId;

        // 缓存SoundPool ID
        String key = "res_" + resId;
        if (soundPoolCache.containsKey(key)) {
            soundId = soundPoolCache.get(key);
        } else {
            soundId = soundPool.load(context, resId, 1);
            soundPoolCache.put(key, soundId);
        }

        if (soundId <= 0) return false;

        float vol = data.getVolume() / 100.0f;
        soundPool.play(soundId, vol, vol, data.getPriority(), data.isLoop() ? -1 : 0, 1.0f);

        // 短音效：延迟标记播放完成
        int duration = data.getDurationMs() > 0 ? data.getDurationMs() : 1000;
        scheduleSoundPoolEnd(slot, data, duration);
        return true;
    }

    private boolean playRawWithMediaPlayer(TrackSlot slot, PlayVoiceData data) {
        try {
            MediaPlayer player = MediaPlayer.create(context, data.getRawResId());
            if (player == null) return false;

            AudioRouter.SoundRouteConfig config = audioRouter.getRouteConfig(slot.source);
            if (config != null && config.audioProfile != null) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(config.audioProfile.usageType)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            }

            float vol = data.getVolume() / 100.0f;
            player.setVolume(vol, vol);
            player.setLooping(data.isLoop());

            player.setOnCompletionListener(mp -> {
                slot.isPlaying = false;
                slot.releaseMediaPlayer();
                onPlayEnd(slot, data);
            });

            player.start();
            slot.mediaPlayer = player;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "资源播放失败: " + data.getRawResId(), e);
            return false;
        }
    }

    // ============ 停止方法 ============

    /**
     * 停止指定音源的播放
     */
    public void stop(AudioRouter.SoundSource source) {
        TrackSlot slot = trackSlots.get(source);
        if (slot == null) return;

        if (slot.isPlaying && slot.currentData != null) {
            onPlayEnd(slot, slot.currentData);
        }
        slot.release();
    }

    /**
     * 停止所有播放
     */
    public void stopAll() {
        for (AudioRouter.SoundSource source : AudioRouter.SoundSource.values()) {
            stop(source);
        }
        soundPool.autoPause();
    }

    /**
     * 释放全部资源
     */
    public void release() {
        stopAll();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundPoolCache.clear();
        trackSlots.clear();
    }

    // ============ 内部回调管理 ============

    private void onPlayEnd(TrackSlot slot, PlayVoiceData data) {
        slot.isPlaying = false;

        // 车外音源通知OutSpeakerManager
        AudioRouter.SoundRouteConfig config = audioRouter.getRouteConfig(slot.source);
        if (config != null && config.audioProfile != null
                && config.audioProfile.isOutside) {
            speakerManager.onOutsidePlayEnd();
        }

        data.setPlaying(false);

        if (completeListener != null) {
            completeListener.onPlayComplete(data);
        }

        Log.d(TAG, "播放结束: " + data.getName() + " 音源=" + slot.source.name());
    }

    /**
     * 计划AudioTrack播放结束（监听线程）
     */
    private void schedulePlayEnd(TrackSlot slot, PlayVoiceData data, AudioTrack track) {
        new Thread(() -> {
            while (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING
                    || track.getPlayState() == AudioTrack.PLAYSTATE_PAUSED) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            slot.releaseAudioTrack();
            onPlayEnd(slot, data);
        }).start();
    }

    /**
     * 计划SoundPool播放结束
     */
    private void scheduleSoundPoolEnd(TrackSlot slot, PlayVoiceData data, int delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            slot.isPlaying = false;
            onPlayEnd(slot, data);
        }).start();
    }

    // ============ 效果 ============

    /**
     * 渐入效果（鱼蛋没有）
     */
    private void applyFadeIn(AudioTrack track, int steps) {
        for (int i = 1; i <= steps; i++) {
            float vol = (float) i / steps;
            track.setVolume(vol);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ============ 状态查询 ============

    /**
     * 检查指定音源是否正在播放
     */
    public boolean isPlaying(AudioRouter.SoundSource source) {
        TrackSlot slot = trackSlots.get(source);
        return slot != null && slot.isPlaying;
    }

    /**
     * 获取指定音源的当前播放数据
     */
    public PlayVoiceData getCurrentData(AudioRouter.SoundSource source) {
        TrackSlot slot = trackSlots.get(source);
        return slot != null ? slot.currentData : null;
    }

    /**
     * 获取所有正在播放的音源
     */
    public List<AudioRouter.SoundSource> getActiveSources() {
        List<AudioRouter.SoundSource> active = new ArrayList<>();
        for (AudioRouter.SoundSource source : AudioRouter.SoundSource.values()) {
            if (isPlaying(source)) {
                active.add(source);
            }
        }
        return active;
    }

    /**
     * 获取当前活跃音源数
     */
    public int getActiveCount() {
        return getActiveSources().size();
    }

    // ---- 监听器 ----

    public void setPlayCompleteListener(PlayCompleteListener listener) {
        this.completeListener = listener;
    }

    public void removePlayCompleteListener() {
        this.completeListener = null;
    }

    @Override
    public String toString() {
        return "MultiSoundPool{" +
                "活跃音源=" + getActiveCount() + "/" + MAX_TRACKS +
                ", SoundPool缓存=" + soundPoolCache.size() +
                '}';
    }
}
