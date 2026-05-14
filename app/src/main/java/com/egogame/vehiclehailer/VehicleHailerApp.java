package com.egogame.vehiclehailer;

import android.app.Application;
import android.util.Log;

import com.egogame.vehiclehailer.engine.AutoTriggerEngine;
import com.egogame.vehiclehailer.engine.ConfigLoader;
import com.egogame.vehiclehailer.engine.VehicleEventTrigger;
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
    private AutoTriggerEngine autoTriggerEngine;
    private VehicleEventTrigger vehicleEventTrigger;

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

        // 初始化自动触发引擎（监听车辆状态变化，自动播放对应语音）
        autoTriggerEngine = new AutoTriggerEngine(vehicleStateManager, voicePlayer);
        Log.d(TAG, "自动触发引擎已初始化");

        // 初始化车辆事件触发引擎（联动规则引擎）
        vehicleEventTrigger = new VehicleEventTrigger(voicePlayer);
        if (configLoader != null) {
            vehicleEventTrigger.setVoiceMap(configLoader.getVoiceItems());
        }
        // 注册到车辆状态管理器的属性变化监听链
        vehicleStateManager.addListener(autoTriggerEngine);
        vehicleStateManager.addListener(vehicleEventTrigger);
        Log.d(TAG, "车辆事件触发引擎已初始化并注册监听");
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
