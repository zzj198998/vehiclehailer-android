package com.egogame.vehiclehailer.engine;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Logcat监听器
 * 后台线程持续监控logcat输出，解析车辆CAN总线信号
 */
public class LogcatMonitor {
    private static final String TAG = "LogcatMonitor";

    private final VehicleStateManager stateManager;
    private Thread monitorThread;
    private volatile boolean running = false;

    public interface OnLogMatchedListener {
        void onMatched(VehicleStateManager.PropertyMatchResult result);
    }

    private OnLogMatchedListener matchedListener;

    public LogcatMonitor(VehicleStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void setOnLogMatchedListener(OnLogMatchedListener listener) {
        this.matchedListener = listener;
    }

    /**
     * 启动logcat监听
     */
    public void start() {
        if (running) return;
        running = true;

        monitorThread = new Thread(() -> {
            try {
                // 启动logcat进程，过滤目标标签
                Process process = Runtime.getRuntime().exec(
                        new String[]{"logcat", "-v", "time", "-s", "Ps-Ver@230625"}
                );

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

                String line;
                while (running && (line = reader.readLine()) != null) {
                    VehicleStateManager.PropertyMatchResult result =
                            stateManager.processLogLine(line);
                    if (result != null && matchedListener != null) {
                        matchedListener.onMatched(result);
                    }
                }

                reader.close();
                process.destroy();
                Log.d(TAG, "logcat线程退出");
            } catch (Exception e) {
                if (running) {
                    Log.e(TAG, "logcat监听异常", e);
                }
            }
        }, "logcat-monitor");

        monitorThread.setDaemon(true);
        monitorThread.start();
        Log.d(TAG, "logcat监听已启动");
    }

    /**
     * 停止监听
     */
    public void stop() {
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread = null;
        }
        // 清理logcat进程
        try {
            Runtime.getRuntime().exec("logcat -c");
        } catch (Exception ignored) {}
        Log.d(TAG, "logcat监听已停止");
    }

    public boolean isRunning() { return running; }
}
