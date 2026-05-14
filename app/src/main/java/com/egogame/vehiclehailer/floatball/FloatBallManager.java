package com.egogame.vehiclehailer.floatball;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.egogame.vehiclehailer.R;

public class FloatBallManager {
    private static final String TAG = "FloatBallManager";
    private static FloatBallManager instance;
    private final Context context;
    private WindowManager windowManager;
    private View floatBallView;
    private WindowManager.LayoutParams params;
    private boolean isShowing = false;
    private boolean isRecording = false;
    private OnFloatBallListener listener;
    private float initialTouchX, initialTouchY;
    private float initialX, initialY;
    private boolean isDragging = false;

    public interface OnFloatBallListener {
        void onSingleClick();
        void onDoubleClick();
        void onLongPress();
    }

    private FloatBallManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized FloatBallManager getInstance(Context context) {
        if (instance == null) {
            instance = new FloatBallManager(context);
        }
        return instance;
    }

    public void setListener(OnFloatBallListener listener) {
        this.listener = listener;
    }

    public boolean checkOverlayPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(activity)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, requestCode);
                return false;
            }
        }
        return true;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void setRecordingStatus(boolean recording) {
        this.isRecording = recording;
        if (floatBallView != null) {
            View indicator = floatBallView.findViewById(R.id.view_recording_indicator);
            if (indicator != null) {
                indicator.setVisibility(recording ? View.VISIBLE : View.GONE);
            }
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void show() {
        if (isShowing) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return;
        }

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        floatBallView = LayoutInflater.from(context).inflate(R.layout.float_ball_view, null);

        int size = dpToPx(60);
        params = new WindowManager.LayoutParams(
                size, size,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dpToPx(20);
        params.y = dpToPx(100);

        floatBallView.setOnTouchListener(new View.OnTouchListener() {
            private long lastClickTime = 0;
            private static final int DOUBLE_CLICK_TIME = 300;
            private static final int LONG_PRESS_TIME = 600;
            private boolean isLongPressed = false;
            private final Runnable longPressRunnable = () -> {
                isLongPressed = true;
                if (listener != null) listener.onLongPress();
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isDragging = false;
                        isLongPressed = false;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        initialX = params.x;
                        initialY = params.y;
                        v.postDelayed(longPressRunnable, LONG_PRESS_TIME);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                            v.removeCallbacks(longPressRunnable);
                            params.x = (int) (initialX + dx);
                            params.y = (int) (initialY + dy);
                            if (windowManager != null && floatBallView != null) {
                                windowManager.updateViewLayout(floatBallView, params);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        v.removeCallbacks(longPressRunnable);
                        if (!isDragging && !isLongPressed) {
                            long now = System.currentTimeMillis();
                            if (now - lastClickTime < DOUBLE_CLICK_TIME) {
                                // 双击
                                if (listener != null) listener.onDoubleClick();
                                lastClickTime = 0;
                            } else {
                                lastClickTime = now;
                                // 单击
                                if (listener != null) listener.onSingleClick();
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(floatBallView, params);
            isShowing = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void hide() {
        if (!isShowing || windowManager == null || floatBallView == null) return;
        try {
            windowManager.removeView(floatBallView);
        } catch (Exception e) {
            e.printStackTrace();
        }
        isShowing = false;
        floatBallView = null;
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
