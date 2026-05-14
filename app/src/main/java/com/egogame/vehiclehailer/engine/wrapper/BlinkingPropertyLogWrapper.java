package com.egogame.vehiclehailer.engine.wrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 闪烁属性检测包装器 — 对应鱼蛋的 BlinkingPropertyLogWrapper
 *
 * 检测短时间内属性值反复变化的"闪烁"状态。
 * 例如：转向灯 signal=ON → OFF → ON（2秒内变化3次以上即为闪烁）
 *
 * 鱼蛋功能：
 * - ValueChange 记录（timestamp/value）
 * - sValueHistoryMap 全局历史
 * - windowDuration 检测时间窗口
 *
 * 我们增强：
 * ① 支持自定义闪烁阈值（变化次数）
 * ② 支持回调通知闪烁事件
 * ③ 支持自动清理过期记录
 * ④ 线程安全
 */
public class BlinkingPropertyLogWrapper extends PropertyLogWrapper {

    /** 闪烁检测结果 */
    public static class BlinkResult {
        public final String propertyName;
        public final int changeCount;
        public final long windowMs;
        public final List<ValueChange> changes;

        public BlinkResult(String propertyName, int changeCount,
                           long windowMs, List<ValueChange> changes) {
            this.propertyName = propertyName;
            this.changeCount = changeCount;
            this.windowMs = windowMs;
            this.changes = changes;
        }
    }

    /** 值变化记录 */
    public static class ValueChange {
        public final long timestamp;
        public final String value;

        public ValueChange(long timestamp, String value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }

    /** 闪烁事件监听器 */
    public interface OnBlinkDetectedListener {
        void onBlinkDetected(BlinkResult result);
    }

    // 每个属性名的历史记录
    private static final Map<String, List<ValueChange>> sValueHistoryMap = new HashMap<>();
    private static final Object sLock = new Object();

    private final long windowDuration;      // 检测时间窗口（ms）
    private final int minBlinkCount;        // 最少变化次数才算闪烁
    private OnBlinkDetectedListener blinkListener;

    /**
     * @param propertyName  属性名
     * @param regex         匹配正则
     * @param windowDuration 检测时间窗口（默认2000ms）
     * @param minBlinkCount  最低闪烁次数（默认3次）
     */
    public BlinkingPropertyLogWrapper(String propertyName, String regex,
                                      long windowDuration, int minBlinkCount) {
        super(propertyName, regex, null);
        this.windowDuration = windowDuration > 0 ? windowDuration : 2000;
        this.minBlinkCount = minBlinkCount > 0 ? minBlinkCount : 3;
    }

    public BlinkingPropertyLogWrapper(String propertyName, String regex) {
        this(propertyName, regex, 2000, 3);
    }

    /**
     * 设置闪烁检测监听器（鱼蛋没有）
     */
    public void setOnBlinkDetectedListener(OnBlinkDetectedListener listener) {
        this.blinkListener = listener;
    }

    @Override
    public PropertyParseResult parseProperty(String logLine) {
        if (logLine == null) return null;

        java.util.regex.Matcher matcher = pattern.matcher(logLine);
        if (!matcher.find()) return null;

        String matchedValue;
        try {
            matchedValue = matcher.group("value");
        } catch (IllegalArgumentException e) {
            matchedValue = matcher.group(groupIndex);
        }

        if (matchedValue == null || matchedValue.isEmpty()) return null;

        long now = System.currentTimeMillis();

        synchronized (sLock) {
            List<ValueChange> history = sValueHistoryMap.computeIfAbsent(
                    propertyName, k -> new ArrayList<>());

            // 清理过期记录（超过时间窗口的）
            long cutoff = now - windowDuration;
            history.removeIf(vc -> vc.timestamp < cutoff);

            // 添加新记录
            history.add(new ValueChange(now, matchedValue));

            // 检查闪烁：统计时间窗口内的有效变化次数
            int changeCount = countChanges(history);
            if (changeCount >= minBlinkCount) {
                BlinkResult result = new BlinkResult(
                        propertyName, changeCount, windowDuration,
                        new ArrayList<>(history));
                history.clear(); // 重置，避免重复触发

                if (blinkListener != null) {
                    blinkListener.onBlinkDetected(result);
                }

                // 返回一个特殊的闪烁标识值
                return new PropertyParseResult(propertyName, "BLINKING(" + changeCount + ")", false);
            }
        }

        return new PropertyParseResult(propertyName, matchedValue);
    }

    /**
     * 统计历史记录中的有效变化次数
     * 即：相邻记录值不同的次数
     */
    private int countChanges(List<ValueChange> history) {
        if (history.size() < 2) return 0;
        int changes = 0;
        String lastValue = history.get(0).value;
        for (int i = 1; i < history.size(); i++) {
            String currentValue = history.get(i).value;
            if (!currentValue.equals(lastValue)) {
                changes++;
                lastValue = currentValue;
            }
        }
        return changes;
    }

    /**
     * 获取指定属性的历史记录（用于调试）
     */
    public static List<ValueChange> getHistory(String propertyName) {
        synchronized (sLock) {
            List<ValueChange> history = sValueHistoryMap.get(propertyName);
            return history != null ? new ArrayList<>(history) : new ArrayList<>();
        }
    }

    /**
     * 手动触发闪烁重置（清除该属性的历史记录）
     */
    public static void resetHistory(String propertyName) {
        synchronized (sLock) {
            sValueHistoryMap.remove(propertyName);
        }
    }

    /**
     * 清除所有历史记录
     */
    public static void clearAllHistory() {
        synchronized (sLock) {
            sValueHistoryMap.clear();
        }
    }
}
