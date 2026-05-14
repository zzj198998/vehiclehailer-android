package com.egogame.vehiclehailer.config;

import android.content.Context;
import android.util.Log;

import com.egogame.vehiclehailer.model.PropertyReg;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 属性注册配置系统 — 比鱼蛋的VehiclePropertyCfg更强
 *
 * 鱼蛋：仅从JSON加载，不支持运行时修改
 * 我们：
 * ① 支持从assets JSON文件加载初始配置
 * ② 支持运行时增删改属性注册
 * ③ 支持持久化到应用内部存储
 * ④ 支持从旧版CSV迁移
 * ⑤ 单例模式 + 事件通知
 */
public class PropertyRegCfg {

    private static final String TAG = "PropertyRegCfg";
    private static final String CONFIG_DIR = "configs";
    private static final String SAVED_FILE = "property_reg_saved.json";

    private static volatile PropertyRegCfg instance;

    private final Context context;
    private final File savedFile;

    // 当前车型的属性注册列表
    private List<PropertyReg> currentRegs = new ArrayList<>();
    // 当前车型名
    private String currentModelName = "default";
    // 所有车型的完整配置
    private final Map<String, List<PropertyReg>> allModelRegs = new HashMap<>();

    /** 配置变化监听器 */
    public interface OnConfigChangeListener {
        void onPropertyRegChanged(String modelName, List<PropertyReg> regs);
    }
    private OnConfigChangeListener changeListener;

    private PropertyRegCfg(Context context) {
        this.context = context.getApplicationContext();
        this.savedFile = new File(context.getFilesDir(), CONFIG_DIR + File.separator + SAVED_FILE);
        // 确保目录存在
        File dir = savedFile.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
    }

    public static PropertyRegCfg getInstance(Context context) {
        if (instance == null) {
            synchronized (PropertyRegCfg.class) {
                if (instance == null) {
                    instance = new PropertyRegCfg(context);
                }
            }
        }
        return instance;
    }

    // ============ 初始化 ============

    /**
     * 从旧版 CSV 导入（兼容现有ConfigLoader）
     */
    public void importFromCsv(String modelName, List<PropertyReg> csvRegs) {
        if (csvRegs != null) {
            allModelRegs.put(modelName, new ArrayList<>(csvRegs));
        }
        // 如果当前车型匹配，更新当前列表
        if (modelName.equals(currentModelName)) {
            currentRegs = new ArrayList<>(csvRegs != null ? csvRegs : new ArrayList<>());
        }
        Log.d(TAG, "从CSV导入: " + modelName + " 共" + (csvRegs != null ? csvRegs.size() : 0) + "条");
    }

    /**
     * 从assets JSON文件加载（初始配置）
     */
    public boolean loadFromAssets(String fileName) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open(CONFIG_DIR + "/" + fileName), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            parseJsonConfig(sb.toString());
            Log.d(TAG, "从assets加载JSON配置成功: " + fileName);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "从assets加载JSON失败: " + fileName, e);
            return false;
        }
    }

    /**
     * 从持久化文件加载（运行时修改过的配置）
     */
    public boolean loadSaved() {
        if (!savedFile.exists()) return false;
        try {
            FileInputStream fis = new FileInputStream(savedFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            fis.close();
            parseJsonConfig(sb.toString());
            Log.d(TAG, "从持久化文件加载配置成功");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "从持久化文件加载失败", e);
            return false;
        }
    }

    /**
     * 保存当前配置到持久化文件
     */
    public boolean save() {
        try {
            JSONObject root = new JSONObject();
            root.put("currentModel", currentModelName);

            JSONObject models = new JSONObject();
            for (Map.Entry<String, List<PropertyReg>> entry : allModelRegs.entrySet()) {
                JSONArray regsArray = new JSONArray();
                for (PropertyReg reg : entry.getValue()) {
                    JSONObject regObj = new JSONObject();
                    regObj.put("id", reg.getId());
                    regObj.put("propertyName", reg.getPropertyName());
                    regObj.put("logcatTags", reg.getLogcatTags() != null ? reg.getLogcatTags() : "");
                    regObj.put("regex", reg.getRegex() != null ? reg.getRegex() : "");
                    regObj.put("wrapperClassName", reg.getWrapperClassName() != null ? reg.getWrapperClassName() : "");
                    regObj.put("valueMapping", reg.getValueMapping() != null ? reg.getValueMapping() : "");
                    regsArray.put(regObj);
                }
                models.put(entry.getKey(), regsArray);
            }
            root.put("models", models);

            FileOutputStream fos = new FileOutputStream(savedFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            writer.write(root.toString(2));
            writer.flush();
            writer.close();
            fos.close();

            Log.d(TAG, "配置保存成功: " + savedFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "配置保存失败", e);
            return false;
        }
    }

    private void parseJsonConfig(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("currentModel")) {
                currentModelName = root.getString("currentModel");
            }
            if (root.has("models")) {
                JSONObject models = root.getJSONObject("models");
                for (String modelName : models.keySet()) {
                    JSONArray regsArray = models.getJSONArray(modelName);
                    List<PropertyReg> regs = new ArrayList<>();
                    for (int i = 0; i < regsArray.length(); i++) {
                        JSONObject regObj = regsArray.getJSONObject(i);
                        PropertyReg reg = new PropertyReg(
                                regObj.optInt("id", i + 1),
                                regObj.optString("propertyName", ""),
                                regObj.optString("logcatTags", ""),
                                regObj.optString("regex", ""),
                                regObj.optString("wrapperClassName", ""),
                                regObj.optString("valueMapping", "")
                        );
                        regs.add(reg);
                    }
                    allModelRegs.put(modelName, regs);
                }
            }
            // 更新当前车型配置
            List<PropertyReg> modelRegs = allModelRegs.get(currentModelName);
            if (modelRegs != null) {
                currentRegs = new ArrayList<>(modelRegs);
            }
        } catch (Exception e) {
            Log.e(TAG, "解析JSON配置失败", e);
        }
    }

    // ============ 增删改查 ============

    /**
     * 获取当前车型的所有属性注册
     */
    public List<PropertyReg> getCurrentRegs() {
        return new ArrayList<>(currentRegs);
    }

    /**
     * 获取指定车型的所有属性注册
     */
    public List<PropertyReg> getRegsForModel(String modelName) {
        List<PropertyReg> regs = allModelRegs.get(modelName);
        return regs != null ? new ArrayList<>(regs) : new ArrayList<>();
    }

    /**
     * 添加属性注册
     */
    public PropertyReg addReg(String propertyName, String logcatTags,
                              String regex, String wrapperClassName, String valueMapping) {
        // 自动生成ID
        int maxId = 0;
        for (PropertyReg reg : currentRegs) {
            if (reg.getId() > maxId) maxId = reg.getId();
        }
        PropertyReg newReg = new PropertyReg(maxId + 1, propertyName, logcatTags,
                regex, wrapperClassName, valueMapping);
        currentRegs.add(newReg);
        allModelRegs.put(currentModelName, new ArrayList<>(currentRegs));
        notifyChange();
        Log.d(TAG, "添加属性注册: " + propertyName);
        return newReg;
    }

    /**
     * 更新属性注册
     */
    public boolean updateReg(int id, String propertyName, String logcatTags,
                             String regex, String wrapperClassName, String valueMapping) {
        for (int i = 0; i < currentRegs.size(); i++) {
            if (currentRegs.get(i).getId() == id) {
                currentRegs.set(i, new PropertyReg(id, propertyName, logcatTags,
                        regex, wrapperClassName, valueMapping));
                allModelRegs.put(currentModelName, new ArrayList<>(currentRegs));
                notifyChange();
                Log.d(TAG, "更新属性注册: id=" + id + " name=" + propertyName);
                return true;
            }
        }
        return false;
    }

    /**
     * 删除属性注册
     */
    public boolean removeReg(int id) {
        for (int i = 0; i < currentRegs.size(); i++) {
            if (currentRegs.get(i).getId() == id) {
                currentRegs.remove(i);
                allModelRegs.put(currentModelName, new ArrayList<>(currentRegs));
                notifyChange();
                Log.d(TAG, "删除属性注册: id=" + id);
                return true;
            }
        }
        return false;
    }

    /**
     * 根据属性名查找注册
     */
    public PropertyReg findByPropertyName(String propertyName) {
        for (PropertyReg reg : currentRegs) {
            if (reg.getPropertyName().equals(propertyName)) {
                return reg;
            }
        }
        return null;
    }

    /**
     * 清空当前车型的所有注册
     */
    public void clearRegs() {
        currentRegs.clear();
        allModelRegs.put(currentModelName, new ArrayList<>());
        notifyChange();
        Log.d(TAG, "清空当前车型属性注册");
    }

    // ============ 车型管理 ============

    /**
     * 切换到指定车型
     */
    public void switchModel(String modelName) {
        this.currentModelName = modelName;
        List<PropertyReg> regs = allModelRegs.get(modelName);
        currentRegs = regs != null ? new ArrayList<>(regs) : new ArrayList<>();
        Log.d(TAG, "切换到车型: " + modelName + " 共" + currentRegs.size() + "条注册");
    }

    public String getCurrentModelName() { return currentModelName; }

    /**
     * 获取所有有配置的车型名
     */
    public List<String> getModelNames() {
        return new ArrayList<>(allModelRegs.keySet());
    }

    /**
     * 获取注册总数
     */
    public int getRegCount() {
        return currentRegs.size();
    }

    // ============ 事件通知 ============

    public void setOnConfigChangeListener(OnConfigChangeListener listener) {
        this.changeListener = listener;
    }

    public void removeOnConfigChangeListener() {
        this.changeListener = null;
    }

    private void notifyChange() {
        if (changeListener != null) {
            changeListener.onPropertyRegChanged(currentModelName, getCurrentRegs());
        }
    }
}
