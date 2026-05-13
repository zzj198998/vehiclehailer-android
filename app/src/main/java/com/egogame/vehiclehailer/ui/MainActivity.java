package com.egogame.vehiclehailer.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

    /**
     * Activity层主题加固：防止厂商系统覆写主题导致Material组件崩溃
     * 与Application层的attachBaseContext形成双重保障
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        // 递归读取最底层的base context，用我们的MaterialComponents主题包裹
        Context base = newBase;
        while (base instanceof ContextWrapper) {
            Context inner = ((ContextWrapper) base).getBaseContext();
            if (inner == null || inner == base) break;
            base = inner;
        }
        super.attachBaseContext(new ContextThemeWrapper(base, R.style.Theme_VehicleHailer));
    }

    private FragmentManager fragmentManager;

    // 侧边栏导航按钮（LinearLayout容器，内部包含图标+文字）
    private View navVoice, navMonitor, navTrigger, navAudioLib, navSettings;
    private View[] navButtons;

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
    private boolean isMonitoring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化音频路由引擎
        audioRouter = new AudioRouter(this);

        // 初始化车辆事件联动引擎
        initVehicleTrigger();

        fragmentManager = getSupportFragmentManager();

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
        int grayColor = ContextCompat.getColor(this, R.color.text_hint);
        for (int i = 0; i < navButtons.length; i++) {
            View item = navButtons[i];
            // 从LinearLayout容器中找出图标TextView（第1个）和文字TextView（第2个）
            if (item instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) item;
                if (vg.getChildCount() >= 2) {
                    if (vg.getChildAt(0) instanceof TextView) {
                        ((TextView) vg.getChildAt(0)).setTextColor(i == selectedIndex ? accentColor : grayColor);
                    }
                    if (vg.getChildAt(1) instanceof TextView) {
                        ((TextView) vg.getChildAt(1)).setTextColor(i == selectedIndex ? accentColor : grayColor);
                    }
                }
            }
            item.setBackgroundResource(i == selectedIndex
                    ? R.drawable.sidebar_item_selected
                    : R.drawable.sidebar_item_normal);
        }
    }

    /**
     * 初始化车辆事件联动引擎
     */
    private void initVehicleTrigger() {
        VehicleHailerApp app = VehicleHailerApp.getInstance();

        // 获取VoicePlayer（从VehicleHailerApp）
        VoicePlayer voicePlayer = app.getVoicePlayer();

        // 创建事件触发引擎
        vehicleEventTrigger = new VehicleEventTrigger(voicePlayer);

        // 设置音频查找表（voiceId -> VoiceItem映射）
        vehicleEventTrigger.setVoiceMap(app.getConfigLoader().getVoiceItems());

        // 创建规则配置管理器
        triggerRuleConfig = new TriggerRuleConfig(this, app.getConfigLoader());

        // 加载当前车型的默认联动规则
        triggerRuleConfig.loadDefaults();

        // 将默认规则注入到触发引擎
        vehicleEventTrigger.addRules(triggerRuleConfig.getSystemRules());

        // 将触发引擎注册为VehicleStateManager的属性变化监听器
        VehicleStateManager stateManager = app.getVehicleStateManager();
        stateManager.setOnPropertyChangeListener(vehicleEventTrigger);

        android.util.Log.d("MainActivity", "车辆事件联动引擎初始化完成，共 "
                + triggerRuleConfig.getSystemRules().size() + " 条默认规则");
    }

    private void startLogcatMonitoring() {
        VehicleHailerApp app = VehicleHailerApp.getInstance();
        logcatMonitor = new LogcatMonitor(app.getVehicleStateManager());
        logcatMonitor.setOnLogMatchedListener(matchedLine -> {
            runOnUiThread(() -> {
                if (!isMonitoring) {
                    isMonitoring = true;
                    // 车辆信号首次连接成功时，确保联动引擎开启
                    if (vehicleEventTrigger != null) {
                        vehicleEventTrigger.setMasterEnabled(true);
                    }
                    Toast.makeText(this, "车辆信号已连接", Toast.LENGTH_SHORT).show();
                }
                // 通知MonitorFragment更新UI
                if (monitorFragment != null && monitorFragment.isVisible()) {
                    monitorFragment.onVehicleStateChanged();
                }
            });
        });
        logcatMonitor.start();

        // 同时启动前台Service保持监听
        LogcatService.start(this);
    }

    // ==================== 公开访问方法 ====================

    /**
     * 获取音频路由引擎实例（供Fragment调用）
     */
    public AudioRouter getAudioRouter() {
        return audioRouter;
    }

    /**
     * 获取车辆事件触发引擎（供Fragment调用）
     */
    public VehicleEventTrigger getVehicleEventTrigger() {
        return vehicleEventTrigger;
    }

    /**
     * 获取联动规则配置管理器（供Fragment调用）
     */
    public TriggerRuleConfig getTriggerRuleConfig() {
        return triggerRuleConfig;
    }

    private void showConnectionDialogIfFirst() {
        SharedPreferences prefs = getSharedPreferences("vehicle_hailer_prefs", Context.MODE_PRIVATE);
        boolean dialogShown = prefs.getBoolean("connection_dialog_shown", false);
        if (dialogShown) return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.connection_dialog_title)
                .setMessage(R.string.connection_dialog_message)
                .setPositiveButton(R.string.connection_dialog_obd, (dialog, which) -> {
                    Toast.makeText(this, "请购买ELM327蓝牙适配器插入OBD接口", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.connection_dialog_logcat, (dialog, which) -> {
                    Toast.makeText(this, "请到设置页查看并复制adb命令", Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("不再提示", (dialog, which) -> {})
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
        // 释放联动引擎
        if (vehicleEventTrigger != null) {
            vehicleEventTrigger.release();
        }
        // 释放音频路由引擎
        if (audioRouter != null) {
            audioRouter.release();
        }
        // 停止Service
        LogcatService.stop(this);
    }
}
