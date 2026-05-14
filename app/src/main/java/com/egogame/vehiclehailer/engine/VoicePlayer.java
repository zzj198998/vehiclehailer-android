package com.egogame.vehiclehailer.engine;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.Log;

import com.egogame.vehiclehailer.model.VoiceItem;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 语音播放引擎
 * 支持车内/车外声道切换、SoundPool预加载、并发播放
 */
public class VoicePlayer {
    private static final String TAG = "VoicePlayer";

    public enum Channel {
        INSIDE,
        OUTSIDE
    }

    private final Context context;
    private SoundPool soundPool;
    private final Map<Integer, Integer> soundIds = new HashMap<>();
    private MediaPlayer mediaPlayer;
    private int currentStreamType = AudioManager.STREAM_MUSIC;
    private Channel currentChannel = Channel.INSIDE;

    // 声道映射
    public static final int CHANNEL_INTERIOR = AudioManager.STREAM_MUSIC;  // 车内
    public static final int CHANNEL_EXTERIOR = AudioManager.STREAM_VOICE_CALL;  // 车外

    public VoicePlayer(Context context) {
        this.context = context;

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(attrs)
                .build();
    }

    /**
     * 预加载语音到SoundPool
     */
    public void preloadVoice(VoiceItem item) {
        String assetPath = "system_voice/" + item.getPath();
        try {
            android.content.res.AssetFileDescriptor afd =
                    context.getAssets().openFd(assetPath);
            int soundId = soundPool.load(afd, 1);
            soundIds.put(item.getId(), soundId);
            Log.d(TAG, "预加载语音: " + item.getTitle());
        } catch (IOException e) {
            Log.w(TAG, "预加载失败: " + assetPath, e);
        }
    }

    /**
     * 设置当前通道（车内/车外）
     */
    public void setChannel(Channel channel) {
        this.currentChannel = channel;
        Log.d(TAG, "声道切换至: " + (channel == Channel.INSIDE ? "车内" : "车外"));
    }

    /**
     * 获取当前通道
     */
    public Channel getChannel() {
        return currentChannel;
    }

    /**
     * 播放语音（自动判断声道）
     * @param item 语音条目
     * @param isExterior 是否车外播放（true=车外喇叭，false=车内扬声器）
     */
    public void play(VoiceItem item, boolean isExterior) {
        int streamType = isExterior ? CHANNEL_EXTERIOR : CHANNEL_INTERIOR;
        play(item, streamType);
    }

    /**
     * 播放语音（指定声道类型）
     */
    public void play(VoiceItem item, int streamType) {
        Integer soundId = soundIds.get(item.getId());
        if (soundId != null && soundId > 0) {
            // SoundPool方式播放（预加载过的）
            float volume = (streamType == CHANNEL_EXTERIOR) ? 1.0f : 0.8f;
            soundPool.play(soundId, volume, volume, 1, 0, 1.0f);
            Log.d(TAG, "SoundPool播放: " + item.getTitle() + " 声道="
                    + (streamType == CHANNEL_EXTERIOR ? "车外" : "车内"));
        } else {
            // 回退到MediaPlayer
            playWithMediaPlayer(item, streamType);
        }
        item.setPlaying(true);
    }

    private void playWithMediaPlayer(VoiceItem item, int streamType) {
        stopCurrentMediaPlayer();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(streamType);

        try {
            String assetPath = "system_voice/" + item.getPath();
            android.content.res.AssetFileDescriptor afd =
                    context.getAssets().openFd(assetPath);
            mediaPlayer.setDataSource(afd.getFileDescriptor(),
                    afd.getStartOffset(), afd.getLength());
            mediaPlayer.prepare();
            mediaPlayer.start();

            mediaPlayer.setOnCompletionListener(mp -> {
                item.setPlaying(false);
                Log.d(TAG, "播放完成: " + item.getTitle());
            });

            Log.d(TAG, "MediaPlayer播放: " + item.getTitle() + " 声道="
                    + (streamType == CHANNEL_EXTERIOR ? "车外" : "车内"));
        } catch (IOException e) {
            Log.e(TAG, "播放失败: " + item.getTitle(), e);
        }
    }

    private void stopCurrentMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    /**
     * 停止指定语音
     */
    public void stop(VoiceItem item) {
        Integer soundId = soundIds.get(item.getId());
        if (soundId != null) {
            soundPool.stop(soundId);
        }
        stopCurrentMediaPlayer();
        item.setPlaying(false);
    }

    /**
     * 停止所有播放
     */
    public void stopAll() {
        soundPool.autoPause();
        stopCurrentMediaPlayer();
    }

    /**
     * 释放资源
     */
    public void release() {
        stopAll();
        soundPool.release();
        soundPool = null;
        soundIds.clear();
    }
}
