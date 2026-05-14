package com.egogame.vehiclehailer.audio.brand;

import android.content.Context;
import android.util.Log;

import com.egogame.vehiclehailer.audio.AudioRouter;
import com.egogame.vehiclehailer.audio.AudioRouter.SoundSource;
import com.egogame.vehiclehailer.model.CarModel;

/**
 * 品牌通道路由层
 *
 * 桥接 ①品牌适配器层 和 ③通用播放引擎（AudioRouter）
 * 负责：
 * - 当品牌适配器激活时，自动设置推荐通道配置
 * - 将品牌适配器的通道预设映射到 AudioRouter 的 ALL_CHANNEL_PROFILES
 * - 用户切换车型时自动更新通道配置
 *
 * 对比鱼蛋：鱼蛋没有此分层，品牌和通用逻辑混在一起。
 * 我们通过此层实现品牌预设与通用测试的无缝衔接。
 */
public class BrandChannelRouter {

    private static final String TAG = "BrandChannelRouter";

    private final BrandRegistry brandRegistry;
    private final AudioRouter audioRouter;
    private Context context;
    private boolean initialized = false;

    public BrandChannelRouter(BrandRegistry brandRegistry, AudioRouter audioRouter) {
        this.brandRegistry = brandRegistry;
        this.audioRouter = audioRouter;
    }

    /**
     * 初始化
     */
    public void init(Context context) {
        this.context = context.getApplicationContext();
        initialized = true;
        Log.d(TAG, "品牌通道路由层初始化完成");
    }

    /**
     * 根据车型自动设置推荐通道配置
     * 在用户选择/切换车型时调用
     *
     * @param carModel 当前选择的车型
     */
    public void applyRecommendedChannels(CarModel carModel) {
        if (!initialized || audioRouter == null) return;

        // 首先通过车型匹配品牌适配器
        BrandAdapter adapter = brandRegistry.matchByCarModel(carModel);
        if (adapter == null) {
            Log.w(TAG, "未匹配到品牌适配器，跳过推荐通道设置");
            return;
        }

        // 获取品牌推荐的通道索引
        int outsideIndex = adapter.getRecommendedOutsideChannelIndex();
        int insideIndex = adapter.getRecommendedInsideChannelIndex();

        Log.d(TAG, "品牌 [" + adapter.getBrandName() + "] 推荐通道配置: "
                + "车外索引=" + outsideIndex + ", 车内索引=" + insideIndex);

        // 设置车外相关声音源的推荐通道
        if (outsideIndex >= 0) {
            // 车外喊话（若配置为车外）
            if (audioRouter.getRouteConfig(SoundSource.HAILING) != null) {
                audioRouter.setRouteWithProfile(SoundSource.HAILING, outsideIndex);
            }
            // 锁车解锁（通常在车外播放）
            if (audioRouter.getRouteConfig(SoundSource.LOCK_SOUND) != null) {
                audioRouter.setRouteWithProfile(SoundSource.LOCK_SOUND, outsideIndex);
            }
            // 模拟声浪（固定车外）
            if (audioRouter.getRouteConfig(SoundSource.SOUNDWAVE_SIM) != null) {
                audioRouter.setRouteWithProfile(SoundSource.SOUNDWAVE_SIM, outsideIndex);
            }
        }

        // 设置车内相关声音源的推荐通道
        if (insideIndex >= 0) {
            // 开关门音效（通常在车内播放）
            if (audioRouter.getRouteConfig(SoundSource.DOOR_SOUND) != null) {
                audioRouter.setRouteWithProfile(SoundSource.DOOR_SOUND, insideIndex);
            }
            // 自定义音效
            if (audioRouter.getRouteConfig(SoundSource.CUSTOM_AUDIO) != null) {
                audioRouter.setRouteWithProfile(SoundSource.CUSTOM_AUDIO, insideIndex);
            }
            // 车内喊话
            if (audioRouter.getRouteConfig(SoundSource.HAILING) != null && outsideIndex < 0) {
                audioRouter.setRouteWithProfile(SoundSource.HAILING, insideIndex);
            }
        }

        // 如果有品牌自定义喇叭控制，在启动/停止车外播放时额外调用
        if (adapter.hasCustomOutspeakerControl()) {
            Log.d(TAG, "品牌 [" + adapter.getBrandName()
                    + "] 有自定义车外喇叭控制，已在 BrandRegistry 中激活");
        }

        Log.d(TAG, "品牌 [" + adapter.getBrandName() + "] 推荐通道已应用");
    }

    /**
     * 手动强制应用当前激活的品牌适配器的推荐通道
     * 在用户手动选择品牌后调用
     */
    public void applyCurrentBrandChannels() {
        BrandAdapter adapter = brandRegistry.getActiveAdapter();
        if (adapter == null) return;

        String brandName = adapter.getBrandName();
        int outsideIndex = adapter.getRecommendedOutsideChannelIndex();
        int insideIndex = adapter.getRecommendedInsideChannelIndex();

        Log.d(TAG, "手动应用品牌 [" + brandName + "] 推荐通道: "
                + "车外索引=" + outsideIndex + ", 车内索引=" + insideIndex);

        // 同 applyRecommendedChannels 逻辑但无车型匹配
        if (outsideIndex >= 0 && audioRouter != null) {
            if (audioRouter.getRouteConfig(SoundSource.HAILING) != null) {
                audioRouter.setRouteWithProfile(SoundSource.HAILING, outsideIndex);
            }
            if (audioRouter.getRouteConfig(SoundSource.LOCK_SOUND) != null) {
                audioRouter.setRouteWithProfile(SoundSource.LOCK_SOUND, outsideIndex);
            }
            if (audioRouter.getRouteConfig(SoundSource.SOUNDWAVE_SIM) != null) {
                audioRouter.setRouteWithProfile(SoundSource.SOUNDWAVE_SIM, outsideIndex);
            }
        }
    }

    /**
     * 获取当前品牌适配器的描述信息（用于设置界面展示）
     */
    public String getCurrentBrandDescription() {
        BrandAdapter adapter = brandRegistry.getActiveAdapter();
        return adapter != null ? adapter.getDescription() : "未选择品牌";
    }

    /**
     * 释放资源
     */
    public void release() {
        if (brandRegistry != null) {
            brandRegistry.release();
        }
        initialized = false;
        Log.d(TAG, "品牌通道路由层已释放");
    }
}
