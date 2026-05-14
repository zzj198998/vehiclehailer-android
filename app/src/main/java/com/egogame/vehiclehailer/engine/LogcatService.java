package com.egogame.vehiclehailer.engine;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.egogame.vehiclehailer.R;
import com.egogame.vehiclehailer.VehicleHailerApp;

/**
 * 前台服务，用于保持Logcat监听线程在后台运行
 * 通过通知栏常驻通知防止被系统杀死
 */
public class LogcatService extends Service {

    private static final String TAG = "LogcatService";
    private static final String CHANNEL_ID = "vehicle_logcat_channel";
    private static final int NOTIFICATION_ID = 1001;

    private LogcatMonitor logcatMonitor;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // 启动Logcat监听
        VehicleHailerApp app = VehicleHailerApp.getInstance();
        logcatMonitor = new LogcatMonitor(app.getVehicleStateManager());
        logcatMonitor.setOnLogMatchedListener(matchedLine -> {
            Log.d(TAG, "Logcat匹配: " + matchedLine);
        });
        logcatMonitor.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("车辆喊话器")
                .setContentText("正在监听车辆信号...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (logcatMonitor != null) {
            logcatMonitor.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "车辆信号监听",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持后台车辆信号监听服务运行");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, LogcatService.class);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, LogcatService.class);
        context.stopService(intent);
    }
}
