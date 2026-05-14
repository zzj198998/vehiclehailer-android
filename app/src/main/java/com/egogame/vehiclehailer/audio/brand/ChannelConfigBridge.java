package com.egogame.vehiclehailer.audio.brand;

import com.egogame.vehiclehailer.audio.AudioRouter;
import com.egogame.vehiclehailer.audio.AudioRouter.ChannelConfigPersistence;
import com.egogame.vehiclehailer.audio.AudioRouter.SoundSource;

/**
 * BrandChannelConfigStore 到 AudioRouter.ChannelConfigPersistence 的桥接适配器
 *
 * 使 BrandChannelConfigStore 可以作为 AudioRouter 的持久化实现注入，
 * 实现自动推荐 + 手动测试保存 + 车型隔离恢复的完整链路。
 *
 * 使用流程：
 * 1. 用户选择车型 → BrandChannelRouter.applyRecommendedChannels() 推荐预设
 * 2. AudioRouter.setConfigPersistence(bridge) → 自动加载已保存的用户配置覆盖推荐
 * 3. 用户手动测试通道 → AudioRouter.setRouteWithProfile() → saveConfig() → bridge.save()
 *    → BrandChannelConfigStore.saveSingleChannel() → 持久化到本地
 * 4. 下次启动或切换车型 → bridge.load() → 自动恢复用户上次测试的结果
 */
public class ChannelConfigBridge implements ChannelConfigPersistence {

    private final BrandChannelConfigStore store;
    private int currentModelId = -1;

    public ChannelConfigBridge(BrandChannelConfigStore store) {
        this.store = store;
    }

    /**
     * 设置当前车型ID（在车型切换时更新）
     */
    public void setCurrentModelId(int modelId) {
        this.currentModelId = modelId;
    }

    /**
     * 加载已保存的通道配置
     * 如果有用户手动测试并保存的配置，覆盖当前内存中的配置
     */
    @Override
    public boolean load(AudioRouter router) {
        if (currentModelId < 0 || store == null) return false;
        return store.loadChannelsForModel(router, currentModelId);
    }

    /**
     * 保存单个声音源的通道配置（用户手动测试后触发）
     */
    @Override
    public void save(AudioRouter router, SoundSource source) {
        if (currentModelId < 0 || store == null) return;

        com.egogame.vehiclehailer.audio.AudioRouter.SoundRouteConfig config =
                router.getRouteConfig(source);
        if (config == null || config.audioProfile == null) return;

        store.saveSingleChannel(currentModelId, source,
                config.audioProfile, config.volume);
    }
}
