package com.egogame.vehiclehailer.audio.brand;

import android.content.Context;

import com.egogame.vehiclehailer.audio.AudioRouter.AudioChannelProfile;

/**
 * 车机品牌适配器接口
 *
 * 每个车机品牌（深蓝、比亚迪、吉利等）可以有自己的适配器实现，
 * 用于控制车外喇叭切换、音频通道路由等品牌特有的逻辑。
 *
 * 架构层级：① 品牌适配器层
 * ② 品牌路由层  → BrandChannelRouter
 * ③ 通用兜底层 → AudioRouter（已有）
 *
 * 对比鱼蛋：鱼蛋只有 DeepalBoxVoiceHelper 硬编码一个品牌，没有接口抽象。
 * 我们通过此接口实现品牌适配器可插拔，支持任意品牌扩展。
 */
public interface BrandAdapter {

    /**
     * @return 品牌标识名称，例如 "Deepal"、"BYD"、"Geely"
     */
    String getBrandName();

    /**
     * 初始化适配器
     * 在品牌匹配成功、应用启动时调用
     * @param context 应用上下文
     * @return true 初始化成功，false 失败
     */
    boolean init(Context context);

    /**
     * 释放适配器资源
     */
    void release();

    /**
     * 获取该品牌推荐的音频通道配置文件在 ALL_CHANNEL_PROFILES 中的索引
     * 用于在品牌首次匹配时自动设置推荐的车外通道
     *
     * @return 推荐的车外通道配置索引，-1 表示使用通用默认值
     */
    int getRecommendedOutsideChannelIndex();

    /**
     * 获取该品牌推荐的音频通道配置文件在 ALL_CHANNEL_PROFILES 中的索引
     * 用于在品牌首次匹配时自动设置推荐的车内通道
     *
     * @return 推荐的车内通道配置索引，-1 表示使用通用默认值
     */
    int getRecommendedInsideChannelIndex();

    /**
     * 该品牌是否有特殊的车外喇叭切换逻辑
     * 返回 true 表示有品牌特有的 Service/Intent 控制方式
     * 此时 AudioRouter 的通用播放可能会被品牌特有的方式覆盖
     */
    boolean hasCustomOutspeakerControl();

    /**
     * 执行车外喇叭的开启操作
     * 仅当 hasCustomOutspeakerControl() 返回 true 时才会被调用
     *
     * @param context 应用上下文
     * @return 操作是否成功
     */
    boolean enableOutspeaker(Context context);

    /**
     * 执行车外喇叭的关闭操作
     * 仅当 hasCustomOutspeakerControl() 返回 true 时才会被调用
     *
     * @param context 应用上下文
     * @return 操作是否成功
     */
    boolean disableOutspeaker(Context context);

    /**
     * 获取适配器的描述信息（用于调试和日志）
     */
    String getDescription();
}
