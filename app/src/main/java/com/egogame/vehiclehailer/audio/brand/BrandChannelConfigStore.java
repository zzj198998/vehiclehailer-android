package com.egogame.vehiclehailer.audio.brand;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.egogame.vehiclehailer.audio.AudioRouter;
import com.egogame.vehiclehailer.audio.AudioRouter.AudioChannelProfile;
import com.egogame.vehiclehailer.audio.AudioRouter.SoundSource;
import com.egogame.vehiclehailer.audio.AudioRouter.SoundRouteConfig;
import com.egogame.vehiclehailer.audio.AudioRouter.SpeakerChannel;

import java.util.HashMap;
import java.util.Map;

/**
 * 通道测试结果持久化存储
 *
 * 用户手动测试通道并确认后，将配置按车型保存到本地 SharedPreferences。
 * 切换车型或重启时自动恢复用户之前保存的配置。
 *
 * 存储结构：
 * SharedPreferences "channel_configs"
 * └── key: "model_{modelId}_{soundSourceName}_profileIndex"
 *     value: 在 ALL_CHANNEL_PROFILES 中的索引
 * └── key: "model_{modelId}_brand"  → 当前车型匹配的品牌名
 *
 * 对比鱼蛋：鱼蛋没有通道持久化功能，每次启动需要重新配置。
 * 我们实现车型隔离存储，切换车型自动加载对应配置。
 */
public class BrandChannelConfigStore {

    private static final String TAG = "ChannelConfigStore";
    private static final String PREFS_NAME = "channel_configs";
    private static final String KEY_BRAND = "model_%d_brand";

    private final SharedPreferences prefs;
    private int currentModelId = -1;
    private boolean loaded = false;

    public BrandChannelConfigStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 加载指定车型已保存的通道配置
     * 如果该车型有已保存的配置，则覆盖 AudioRouter 中的配置返回 true
     * 如果没有，返回 false（保持默认或自动推荐）
     *
     * @param audioRouter AudioRouter 实例
     * @param modelId     车型ID
     * @return true 有已保存的配置并已加载，false 无已保存配置
     */
    public boolean loadChannelsForModel(AudioRouter audioRouter, int modelId) {
        if (audioRouter == null) return false;

        this.currentModelId = modelId;
        boolean hasSaved = false;

        // 逐个声音源检查是否有已保存的配置
        for (SoundSource source : SoundSource.values()) {
            int savedIndex = getSavedProfileIndex(modelId, source);
            if (savedIndex >= 0) {
                // 有已保存的配置，恢复
                audioRouter.setRouteWithProfile(source, savedIndex);
                hasSaved = true;
                Log.d(TAG, "已恢复车型[" + modelId + "] " + source.name()
                        + " 通道配置索引=" + savedIndex);
            }
        }

        // 同时恢复已保存的音量设置
        for (SoundSource source : SoundSource.values()) {
            int savedVolume = getSavedVolume(modelId, source);
            if (savedVolume >= 0) {
                audioRouter.setVolume(source, savedVolume);
            }
        }

        loaded = hasSaved;
        if (hasSaved) {
            Log.d(TAG, "车型[" + modelId + "] 通道配置已从本地恢复");
        } else {
            Log.d(TAG, "车型[" + modelId + "] 无已保存的通道配置，将使用默认/推荐配置");
        }
        return hasSaved;
    }

    /**
     * 保存当前 AudioRouter 中所有声音源的通道配置到本地
     * 按车型隔离存储
     * 用户手动测试确认后调用
     *
     * @param audioRouter AudioRouter 实例
     * @param modelId     车型ID
     */
    public void saveChannelsForModel(AudioRouter audioRouter, int modelId) {
        if (audioRouter == null) return;

        SharedPreferences.Editor editor = prefs.edit();

        for (SoundSource source : SoundSource.values()) {
            SoundRouteConfig config = audioRouter.getRouteConfig(source);
            if (config == null || config.audioProfile == null) continue;

            // 保存通道配置文件索引
            int profileIndex = findProfileIndex(config.audioProfile);
            if (profileIndex >= 0) {
                editor.putInt(buildProfileKey(modelId, source), profileIndex);
            }

            // 保存音量
            editor.putInt(buildVolumeKey(modelId, source), config.volume);
        }

        editor.apply();
        this.currentModelId = modelId;
        this.loaded = true;

        Log.d(TAG, "车型[" + modelId + "] 通道配置已保存到本地");
    }

    /**
     * 保存单个声音源的通道配置（带测试音的版本专用）
     *
     * @param modelId  车型ID
     * @param source   声音源
     * @param profile  手动测试并选择的通道配置
     * @param volume   音量 0-100
     */
    public void saveSingleChannel(int modelId, SoundSource source,
                                  AudioChannelProfile profile, int volume) {
        SharedPreferences.Editor editor = prefs.edit();
        int profileIndex = findProfileIndex(profile);
        if (profileIndex >= 0) {
            editor.putInt(buildProfileKey(modelId, source), profileIndex);
        }
        editor.putInt(buildVolumeKey(modelId, source), volume);
        editor.apply();

        Log.d(TAG, "已保存 " + source.name() + " 通道="
                + (profile != null ? profile.displayName : "")
                + " 音量=" + volume + " (车型[" + modelId + "])");
    }

    /**
     * 保存当前车型匹配的品牌名
     */
    public void saveMatchedBrand(int modelId, String brandName) {
        if (TextUtils.isEmpty(brandName)) return;
        prefs.edit().putString(buildBrandKey(modelId), brandName).apply();
    }

    /**
     * 获取某车型已保存的品牌名
     */
    public String getMatchedBrand(int modelId) {
        return prefs.getString(buildBrandKey(modelId), null);
    }

    /**
     * 清除指定车型的所有已保存通道配置
     * 用户选择"重置通道"时调用
     */
    public void clearChannelsForModel(int modelId) {
        SharedPreferences.Editor editor = prefs.edit();
        for (SoundSource source : SoundSource.values()) {
            editor.remove(buildProfileKey(modelId, source));
            editor.remove(buildVolumeKey(modelId, source));
        }
        editor.remove(buildBrandKey(modelId));
        editor.apply();

        if (modelId == this.currentModelId) {
            this.loaded = false;
        }

        Log.d(TAG, "车型[" + modelId + "] 通道配置已清除");
    }

    /**
     * 清除所有车型的所有已保存配置
     */
    public void clearAll() {
        prefs.edit().clear().apply();
        this.loaded = false;
        Log.d(TAG, "所有通道配置已清除");
    }

    /**
     * 检查当前车型是否已有保存的配置
     */
    public boolean hasSavedConfig(int modelId) {
        for (SoundSource source : SoundSource.values()) {
            if (prefs.contains(buildProfileKey(modelId, source))) {
                return true;
            }
        }
        return false;
    }

    // ---- 内部关键方法 ----

    private int getSavedProfileIndex(int modelId, SoundSource source) {
        return prefs.getInt(buildProfileKey(modelId, source), -1);
    }

    private int getSavedVolume(int modelId, SoundSource source) {
        return prefs.getInt(buildVolumeKey(modelId, source), -1);
    }

    /**
     * 在 ALL_CHANNEL_PROFILES 中查找指定配置的索引
     */
    private int findProfileIndex(AudioChannelProfile profile) {
        if (profile == null) return -1;
        AudioChannelProfile[] profiles = AudioRouter.ALL_CHANNEL_PROFILES;
        for (int i = 0; i < profiles.length; i++) {
            if (profiles[i].displayName.equals(profile.displayName)) {
                return i;
            }
        }
        return -1;
    }

    // ---- Key构建 ----

    private String buildProfileKey(int modelId, SoundSource source) {
        return "model_" + modelId + "_" + source.name() + "_profileIndex";
    }

    private String buildVolumeKey(int modelId, SoundSource source) {
        return "model_" + modelId + "_" + source.name() + "_volume";
    }

    private String buildBrandKey(int modelId) {
        return "model_" + modelId + "_brand";
    }
}
