package com.egogame.vehiclehailer.action;

import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动作工厂注册表（可选模块）
 *
 * 用于根据类型名动态创建 Action 实例。
 * 仅在用户使用「高级联动」功能时才会用到。
 * 简单模式下（只播一个音频）完全不需要此模块。
 *
 * 价值：
 * - 联动规则可存为JSON/数据库，通过工厂动态解析执行
 * - 新增Action类型时只需注册，不修改现有代码
 *
 * 对比鱼蛋：鱼蛋硬编码 new Action，不支持配置化创建。
 */
public class ActionFactory {

    private static final String TAG = "ActionFactory";

    /** 创建器接口：根据参数Map创建Action */
    public interface ActionCreator {
        Action create(Map<String, Object> params);
    }

    // 注册表：类型名 → 创建器
    private static final Map<String, ActionCreator> creators = new LinkedHashMap<>();

    static {
        // 注册内置Action类型
        register("PlayAudio", params -> {
            // 需要 context 和具体参数从 params 提取
            // 实际使用时由调用者提供 context
            return null; // 占位，实际使用时会传入 context
        });
        register("Delay", params -> null);
        register("SpeakText", params -> null);
        register("SendIntent", params -> null);
        register("StopAudio", params -> null);
        register("VolumeControl", params -> null);
        register("ShowToast", params -> null);
    }

    /**
     * 注册一个Action类型
     * @param typeName 类型名（需与 ActionType 枚举名一致）
     * @param creator  创建器
     */
    public static void register(String typeName, ActionCreator creator) {
        if (typeName != null && creator != null) {
            creators.put(typeName, creator);
            Log.d(TAG, "注册Action类型: " + typeName);
        }
    }

    /**
     * 检查是否已注册某类型
     */
    public static boolean isRegistered(String typeName) {
        return creators.containsKey(typeName);
    }

    /**
     * 获取所有已注册的Action类型名（供UI下拉选择）
     */
    public static String[] getRegisteredTypes() {
        return creators.keySet().toArray(new String[0]);
    }

    /**
     * 清除所有注册（用于测试）
     */
    public static void clear() {
        creators.clear();
    }
}
