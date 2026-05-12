package com.egogame.vehiclehailer;

import android.app.Application;
import android.util.Log;

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
