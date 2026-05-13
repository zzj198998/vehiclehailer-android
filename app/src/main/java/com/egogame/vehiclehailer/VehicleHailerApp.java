package com.egogame.vehiclehailer;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;
import android.view.ContextThemeWrapper;

import com.egogame.vehiclehailer.engine.ConfigLoader;
import com.egogame.vehiclehailer.engine.VehicleStateManager;
import com.egogame.vehiclehailer.engine.VoicePlayer;

/**
 * 车辆喊话器Application
 * 全局初始化配置加载器、状态管理器和声音播放引擎
 */
public class VehicleHailerApp extends Application {

    private static final String TAG = "VehicleHailerApp";

    private static VehicleHailerApp instance;

    private ConfigLoader configLoader;
    private VehicleStateManager vehicleStateManager;
    private VoicePlayer voicePlayer;

    /**
     * 全局主题注入：覆写attachBaseContext，确保所有基于此Application创建的Context
     * 都强制使用MaterialComponents主题，防止OPPO/小米/华为等厂商系统覆写主题导致Material组件崩溃
     */
    @Override
    protected void attachBaseContext(Context base) {
        // 用ContextThemeWrapper包裹原始context，强制使用我们的MaterialComponents主题
        Context themedContext = new ContextThemeWrapper(base, R.style.Theme_VehicleHailer);
        // 递归确保内部ContextWrapper也被包裹
        if (base instanceof ContextWrapper) {
            Context baseContext = ((ContextWrapper) base).getBaseContext();
            if (baseContext != null) {
                themedContext = new ContextThemeWrapper(baseContext, R.style.Theme_VehicleHailer);
            }
        }
        super.attachBaseContext(themedContext);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 初始化配置加载器
        configLoader = new ConfigLoader(this);
        try {
            configLoader.loadAll();
            Log.d(TAG, "配置加载完成，车型数：" + configLoader.getCarModels().size()
                    + "，语音数：" + configLoader.getVoiceItems().size()
                    + "，属性数：" + configLoader.getVehicleProperties().size());
        } catch (Exception e) {
            Log.e(TAG, "配置加载失败", e);
        }

        // 初始化车辆状态管理器
        vehicleStateManager = new VehicleStateManager(configLoader);

        // 初始化声音播放引擎
        voicePlayer = new VoicePlayer(this);
    }

    public static VehicleHailerApp getInstance() {
        return instance;
    }

    public ConfigLoader getConfigLoader() {
        return configLoader;
    }

    public VehicleStateManager getVehicleStateManager() {
        return vehicleStateManager;
    }

    public VoicePlayer getVoicePlayer() {
        return voicePlayer;
    }
}
