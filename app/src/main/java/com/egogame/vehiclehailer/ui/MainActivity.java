package com.egogame.vehiclehailer.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.egogame.vehiclehailer.R;
import com.egogame.vehiclehailer.VehicleHailerApp;
import com.egogame.vehiclehailer.audio.AudioRouter;
import com.egogame.vehiclehailer.engine.LogcatMonitor;
import com.egogame.vehiclehailer.engine.LogcatService;
import com.egogame.vehiclehailer.engine.TriggerRuleConfig;
import com.egogame.vehiclehailer.engine.VehicleEventTrigger;
import com.egogame.vehiclehailer.engine.VehicleStateManager;
import com.egogame.vehiclehailer.engine.VoicePlayer;

public class MainActivity extends AppCompatActivity {

    private FragmentManager fragmentManager;

    // 侧边栏导航按钮（XML中是LinearLayout，内含ImageView+TextView）
    private LinearLayout navVoice, navMonitor, navTrigger, navAudioLib, navSettings;
    private View[] navButtons;

    // 底部连接状态
    private TextView connectionStatus;

    // 记录当前选中的Fragment
    private Fragment currentFragment;
    private VoiceListFragment voiceListFragment;
    private MonitorFragment monitorFragment;
    private SettingsFragment settingsFragment;
    private AudioLibraryFragment audioLibraryFragment;
    private TriggerListFragment triggerListFragment;

    // 音频路由引擎
    private AudioRouter audioRouter;

    // 车辆事件联动引擎
    private VehicleEventTrigger vehicleEventTrigger;
    private TriggerRuleConfig triggerRuleConfig;

    // Logcat监听服务
    private LogcatMonitor logcatMonitor;
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化音频路由引擎
        audioRouter = new AudioRouter(this);

        // 初始化车辆事件联动引擎
        initVehicleTrigger();

        fragmentManager = getSupportFragmentManager();

        // 初始化连接状态
        connectionStatus = findViewById(R.id.connection_status);

        // 初始化侧边栏导航按钮
        navVoice = findViewById(R.id.nav_voice);
        navMonitor = findViewById(R.id.nav_monitor);
        navTrigger = findViewById(R.id.nav_trigger);
        navAudioLib = findViewById(R.id.nav_audio_library);
        navSettings = findViewById(R.id.nav_settings);
        navButtons = new View[]{navVoice, navMonitor, navTrigger, navAudioLib, navSettings};

        // 初始化Fragment
        voiceListFragment = new VoiceListFragment();
        monitorFragment = new MonitorFragment();
        settingsFragment = new SettingsFragment();
        audioLibraryFragment = new AudioLibraryFragment();
        triggerListFragment = new TriggerListFragment();

        // 默认显示语音列表
        currentFragment = voiceListFragment;
        fragmentManager.beginTransaction()
                .add(R.id.fragment_container, voiceListFragment, "voice")
                .add(R.id.fragment_container, monitorFragment, "monitor")
                .add(R.id.fragment_container, settingsFragment, "settings")
                .add(R.id.fragment_container, audioLibraryFragment, "audio_library")
                .add(R.id.fragment_container, triggerListFragment, "trigger")
                .hide(monitorFragment)
                .hide(settingsFragment)
                .hide(audioLibraryFragment)
                .hide(triggerListFragment)
                .commit();

        // 设置侧边栏导航点击
        navVoice.setOnClickListener(v -> switchToFragment(voiceListFragment, 0));
        navMonitor.setOnClickListener(v -> switchToFragment(monitorFragment, 1));
        navTrigger.setOnClickListener(v -> switchToFragment(triggerListFragment, 2));
        navAudioLib.setOnClickListener(v -> switchToFragment(audioLibraryFragment, 3));
        navSettings.setOnClickListener(v -> switchToFragment(settingsFragment, 4));

        // 默认选中第一个
        updateNavSelection(0);

        // 启动时弹出连接方式引导（仅首次）
        showConnectionDialogIfFirst();

        // 启动Logcat监听
        startLogcatMonitoring();
    }

    /**
     * 切换到指定Fragment并更新侧边栏选中状态
     */
    private void switchToFragment(Fragment target, int index) {
        if (target == currentFragment) return;
        fragmentManager.beginTransaction()
                .hide(currentFragment)
                .show(target)
                .commit();
        currentFragment = target;
        updateNavSelection(index);
    }

    /**
     * 更新侧边栏按钮的选中/未选中样式
     */
    private void updateNavSelection(int selectedIndex) {
        int accentColor = ContextCompat.getColor(this, R.color.primary_light);
        int grayColor = ContextCompat.getColor(this, R.color.gray_400);
        for (int i = 0; i < navButtons.length; i++) {
            LinearLayout navItem = (LinearLayout) navButtons[i];
            // 导航按钮内部：第0个子View是ImageView，第1个子View是TextView
            TextView tv = (TextView) navItem.getChildAt(1);
            if (i == selectedIndex) {
                tv.setTextColor(accentColor);
                navItem.setBackgroundResource(R.drawable.sidebar_item_selected);
            } else {
                tv.setTextColor(grayColor);
                navItem.setBackgroundResource(R.drawable.sidebar_item_normal);
            }
        }
    }

    /**
     * 更新侧边栏连接状态
     */
    public void updateConnectionStatus(boolean connected) {
        if (connectionStatus == null) return;
        isConnected = connected;
        runOnUiThread(() -> {
            if (connected) {
                connectionStatus.setText(R.string.status_connected);
                connectionStatus.setTextColor(0xFF22C55E);
            } else {
                connectionStatus.setText(R.string.status_offline);
                connectionStatus.setTextColor(0xFFEF4444);
            }
        });
    }

    /**
     * 初始化车辆事件联动引擎
     */
    private void initVehicleTrigger() {
        VehicleHailerApp app = VehicleHailerApp.getInstance();

        VoicePlayer voicePlayer = app.getVoicePlayer();

        vehicleEventTrigger = new VehicleEventTrigger(voicePlayer);
        vehicleEventTrigger.setVoiceMap(app.getConfigLoader().getVoiceItems());

        triggerRuleConfig = new TriggerRuleConfig(this, app.getConfigLoader());
        triggerRuleConfig.loadDefaults();
        vehicleEventTrigger.addRules(triggerRuleConfig.getSystemRules());

        VehicleStateManager stateManager = app.getVehicleStateManager();
        stateManager.addListener(vehicleEventTrigger);

        Log.d("MainActivity", "车辆事件联动引擎初始化完成，共 "
                + triggerRuleConfig.getSystemRules().size() + " 条默认规则");
    }

    /**
     * 启动Logcat监听线程
     */
    private void startLogcatMonitoring() {
        VehicleHailerApp app = VehicleHailerApp.getInstance();
        logcatMonitor = new LogcatMonitor(app.getVehicleStateManager());
        logcatMonitor.setOnLogMatchedListener(matchedLine -> {
            runOnUiThread(() -> {
                if (!isConnected) {
                    isConnected = true;
                    updateConnectionStatus(true);
                    if (vehicleEventTrigger != null) {
                        vehicleEventTrigger.setMasterEnabled(true);
                    }
                    Toast.makeText(this, R.string.car_signal_connected, Toast.LENGTH_SHORT).show();
                }
                if (monitorFragment != null && monitorFragment.isVisible()) {
                    monitorFragment.onVehicleStateChanged();
                }
            });
        });
        logcatMonitor.start();

        LogcatService.start(this);
    }

    // ==================== 公开访问方法 ====================

    public AudioRouter getAudioRouter() {
        return audioRouter;
    }

    public VehicleEventTrigger getVehicleEventTrigger() {
        return vehicleEventTrigger;
    }

    public TriggerRuleConfig getTriggerRuleConfig() {
        return triggerRuleConfig;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 将权限结果广播给当前可见的 Fragment（SettingsFragment 可能正在请求录音权限）
        Fragment activeFragment = currentFragment;
        if (activeFragment != null) {
            activeFragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 将悬浮窗权限结果转发给当前 Fragment
        Fragment activeFragment = currentFragment;
        if (activeFragment != null) {
            activeFragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void showConnectionDialogIfFirst() {
        SharedPreferences prefs = getSharedPreferences("vehicle_hailer_prefs", Context.MODE_PRIVATE);
        boolean dialogShown = prefs.getBoolean("connection_dialog_shown", false);
        if (dialogShown) return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.connection_dialog_title)
                .setMessage(R.string.connection_dialog_message)
                .setPositiveButton(R.string.connection_dialog_obd, (dialog, which) -> {
                    Toast.makeText(this, R.string.connection_obd_buy, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.connection_dialog_logcat, (dialog, which) -> {
                    Toast.makeText(this, R.string.connection_logcat_guide, Toast.LENGTH_LONG).show();
                })
                .setNeutralButton(R.string.dialog_never_show, (dialog, which) -> {})
                .setOnDismissListener(dialog -> {
                    prefs.edit().putBoolean("connection_dialog_shown", true).apply();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logcatMonitor != null) {
            logcatMonitor.stop();
        }
        if (vehicleEventTrigger != null) {
            vehicleEventTrigger.release();
        }
        if (audioRouter != null) {
            audioRouter.release();
        }
        LogcatService.stop(this);
    }
}
