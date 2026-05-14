package com.egogame.vehiclehailer.audio.brand;

import android.content.Context;

/**
 * 通用品牌适配器（兜底实现）
 *
 * 当用户选择的车型没有对应的专属品牌适配器时，
 * 使用此实现。所有品牌特有方法均返回默认值，
 * 车外喇叭控制走 AudioRouter 的通用模式（手动测试通道）。
 *
 * 对比鱼蛋：鱼蛋没有专门的通用兜底类，混在 OutSpeakerManager 中。
 * 我们显式分离通用逻辑，便于扩展。
 */
public class GenericBrandAdapter implements BrandAdapter {

    private static final String BRAND_NAME = "Generic";
    private boolean initialized = false;

    @Override
    public String getBrandName() {
        return BRAND_NAME;
    }

    @Override
    public boolean init(Context context) {
        initialized = true;
        return true;
    }

    @Override
    public void release() {
        initialized = false;
    }

    @Override
    public int getRecommendedOutsideChannelIndex() {
        return -1; // 使用通用默认值（ALL_CHANNEL_PROFILES[DEFAULT_CHANNEL_INDEX_OUTSIDE]）
    }

    @Override
    public int getRecommendedInsideChannelIndex() {
        return -1; // 使用通用默认值（ALL_CHANNEL_PROFILES[DEFAULT_CHANNEL_INDEX_INSIDE]）
    }

    @Override
    public boolean hasCustomOutspeakerControl() {
        return false; // 通用模式：没有品牌特有控制方式
    }

    @Override
    public boolean enableOutspeaker(Context context) {
        return false; // 不支持
    }

    @Override
    public boolean disableOutspeaker(Context context) {
        return false; // 不支持
    }

    @Override
    public String getDescription() {
        return "通用品牌适配器 — 无品牌特有逻辑，使用标准 AudioRouter 通道测试";
    }
}
