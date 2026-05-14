package com.egogame.vehiclehailer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.egogame.vehiclehailer.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 录音服务 - 参考鱼蛋喊话 AudioRecordService 实现
 * 支持实时喊话和录音文件保存
 */
public class AudioRecordService extends Service {
    private static final String TAG = "AudioRecordService";
    private static final String CHANNEL_ID = "audio_record_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START_RECORDING = "com.egogame.vehiclehailer.START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "com.egogame.vehiclehailer.STOP_RECORDING";
    public static final String ACTION_START_REAL_TIME = "com.egogame.vehiclehailer.START_REAL_TIME";
    public static final String ACTION_STOP_REAL_TIME = "com.egogame.vehiclehailer.STOP_REAL_TIME";
    public static final String EXTRA_SAVE_PATH = "save_path";

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isRecording = false;
    private Thread recordingThread;
    private Thread playbackThread;
    private String saveFilePath;
    private volatile boolean isRealTimeMode = false;

    // 同步锁对象：防止多个线程同时操作录音/停止
    private final Object recordingLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("录音服务运行中"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (action == null) return START_STICKY;

        switch (action) {
            case ACTION_START_RECORDING:
                saveFilePath = intent.getStringExtra(EXTRA_SAVE_PATH);
                startRecording();
                break;
            case ACTION_STOP_RECORDING:
                stopRecording();
                break;
            case ACTION_START_REAL_TIME:
                isRealTimeMode = true;
                startRealTimeVoice();
                break;
            case ACTION_STOP_REAL_TIME:
                isRealTimeMode = false;
                stopRealTimeVoice();
                break;
        }
        return START_STICKY;
    }

    private void startRecording() {
        synchronized (recordingLock) {
            if (isRecording) return;
            isRecording = true;
        }

        int sampleRate = 44100;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        File outputFile = new File(saveFilePath != null ? saveFilePath :
                getExternalFilesDir(null) + "/recordings/voice_" + System.currentTimeMillis() + ".pcm");
        outputFile.getParentFile().mkdirs();

        recordingThread = new Thread(() -> {
            audioRecord.startRecording();
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[bufferSize];
                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        fos.write(buffer, 0, read);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "录音写入失败", e);
            }
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        });
        recordingThread.start();
        Log.d(TAG, "录音已开始，保存至: " + outputFile.getAbsolutePath());
    }

    private void stopRecording() {
        synchronized (recordingLock) {
            isRecording = false;
        }
        if (recordingThread != null) {
            try { recordingThread.join(1000); } catch (InterruptedException ignored) {}
            recordingThread = null;
        }
        Log.d(TAG, "录音已停止");
    }

    private void startRealTimeVoice() {
        synchronized (recordingLock) {
            if (isRealTimeMode) return;
            isRealTimeMode = true;
        }
        // 实时喊话：录音并立即播放到外放
        int sampleRate = 44100;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();

        audioRecord.startRecording();
        audioTrack.play();

        playbackThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            while (isRealTimeMode) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    audioTrack.write(buffer, 0, read);
                }
            }
        });
        playbackThread.start();
        Log.d(TAG, "实时喊话已开始");
    }

    private void stopRealTimeVoice() {
        synchronized (recordingLock) {
            isRealTimeMode = false;
        }
        if (playbackThread != null) {
            try { playbackThread.join(1000); } catch (InterruptedException ignored) {}
            playbackThread = null;
        }
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }
        Log.d(TAG, "实时喊话已停止");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "录音服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("无心车机喊话")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        stopRecording();
        stopRealTimeVoice();
        super.onDestroy();
    }
}
