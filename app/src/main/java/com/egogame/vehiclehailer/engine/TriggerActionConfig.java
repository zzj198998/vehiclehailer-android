package com.egogame.vehiclehailer.engine;

import android.content.Context;
import android.util.Log;

import com.egogame.vehiclehailer.model.VehicleProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 联动动作配置管理器
 * 解析 action_config.csv，提供动作类型查询和参数验证。
 * 动作类型包括：播放卡片、延迟、停止音频、开关空调、手势点击等30+种。
 */
public class TriggerActionConfig {

    private static final String TAG = "TriggerActionConfig";
    private static final String ACTION_CONFIG_FILE = "configs/action_config.csv";

    private final Context context;
    private final Map<String, ActionDef> actionMap = new HashMap<>();
    private final List<ActionDef> actionList = new ArrayList<>();
    private boolean loaded = false;

    public TriggerActionConfig(Context context) {
        this.context = context;
    }

    /**
     * 动作定义
     */
    public static class ActionDef {
        public final String actionType;
        public final String displayName;
        public final String description;
        public final String paramHint;
        public final String defaultParams;
        public final String catalog;
        public final String onlyModelIds;   // 专用车型ID（分号分隔）
        public final String excludeModelIds; // 排除车型ID

        public ActionDef(String actionType, String displayName, String description,
                         String paramHint, String defaultParams, String catalog,
                         String onlyModelIds, String excludeModelIds) {
            this.actionType = actionType;
            this.displayName = displayName;
            this.description = description;
            this.paramHint = paramHint;
            this.defaultParams = defaultParams;
            this.catalog = catalog;
            this.onlyModelIds = onlyModelIds;
            this.excludeModelIds = excludeModelIds;
        }

        /**
         * 判断该动作是否支持指定车型
         */
        public boolean isSupportedForModel(int carModelId) {
            String modelIdStr = String.valueOf(carModelId);
            // 检查排除列表
            if (!excludeModelIds.isEmpty()) {
                String[] excludeIds = excludeModelIds.split(";");
                for (String id : excludeIds) {
                    if (id.trim().equals(modelIdStr)) return false;
                }
            }
            // 检查专用列表
            if (!onlyModelIds.isEmpty()) {
                String[] onlyIds = onlyModelIds.split(";");
                for (String id : onlyIds) {
                    if (id.trim().equals(modelIdStr)) return true;
                }
                return false; // 有专用列表但当前车型不在内
            }
            return true; // 无限制
        }
    }

    /**
     * 加载动作配置
     */
    public void load() {
        actionMap.clear();
        actionList.clear();
        loaded = false;

        try (InputStream is = context.getAssets().open(ACTION_CONFIG_FILE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {

            String line;
            boolean headerSkipped = false;
            boolean commentSkipped = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 跳过表头（第1行）和注释行（第2行，以#开头）
                if (!headerSkipped) { headerSkipped = true; continue; }
                if (!commentSkipped) { commentSkipped = true; continue; }

                // 解析CSV行（简单解析，用逗号分割，处理引号）
                String[] fields = parseCsvLine(line);
                if (fields.length < 6) {
                    Log.w(TAG, "跳过不完整行: " + line);
                    continue;
                }

                String actionType = fields[0];
                String displayName = fields.length > 1 ? fields[1] : actionType;
                String description = fields.length > 2 ? fields[2] : "";
                String paramHint = fields.length > 3 ? fields[3] : "";
                String defaultParams = fields.length > 4 ? fields[4] : "{}";
                String catalog = fields.length > 5 ? fields[5] : "SYSTEM";
                String onlyModelIds = fields.length > 6 ? fields[6] : "";
                String excludeModelIds = fields.length > 7 ? fields[7] : "";

                ActionDef def = new ActionDef(actionType, displayName, description,
                        paramHint, defaultParams, catalog, onlyModelIds, excludeModelIds);
                actionMap.put(actionType, def);
                actionList.add(def);
            }

            loaded = true;
            Log.d(TAG, "加载了 " + actionList.size() + " 个动作定义");
        } catch (IOException e) {
            Log.e(TAG, "读取动作配置失败: " + ACTION_CONFIG_FILE, e);
        }
    }

    /**
     * 获取所有动作定义
     */
    public List<ActionDef> getAllActions() {
        return new ArrayList<>(actionList);
    }

    /**
     * 获取指定车型支持的动作列表
     */
    public List<ActionDef> getActionsForModel(int carModelId) {
        List<ActionDef> result = new ArrayList<>();
        for (ActionDef def : actionList) {
            if (def.isSupportedForModel(carModelId)) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * 获取指定目录的动作列表
     */
    public List<ActionDef> getActionsByCatalog(String catalog) {
        List<ActionDef> result = new ArrayList<>();
        for (ActionDef def : actionList) {
            if (def.catalog.equalsIgnoreCase(catalog)) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * 按动作类型查找
     */
    public ActionDef findAction(String actionType) {
        return actionMap.get(actionType);
    }

    /**
     * 解析CSV行（处理带引号的字段）
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString().trim());
        return fields.toArray(new String[0]);
    }
}
