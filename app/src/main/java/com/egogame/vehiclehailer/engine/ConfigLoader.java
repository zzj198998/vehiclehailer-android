package com.egogame.vehiclehailer.engine;

import android.content.Context;
import android.util.Log;

import com.egogame.vehiclehailer.model.CarModel;
import com.egogame.vehiclehailer.model.Catalog;
import com.egogame.vehiclehailer.model.PropertyReg;
import com.egogame.vehiclehailer.model.VehicleProperty;
import com.egogame.vehiclehailer.model.VoiceItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置文件加载器 - 读取assets/configs/下的所有CSV配置
 */
public class ConfigLoader {
    private static final String TAG = "ConfigLoader";
    private static final String CONFIGS_DIR = "configs/";

    private final Context context;

    // 所有配置缓存
    private List<CarModel> carModels;
    private List<VoiceItem> voiceItems;
    private List<VehicleProperty> vehicleProperties;
    private List<Catalog> catalogs;
    private Map<String, List<PropertyReg>> propertyRegMap; // modelName -> list
    private Map<String, String> settings;
    private int currentCarModelId = 1; // 默认深蓝汽车

    public ConfigLoader(Context context) {
        this.context = context;
    }

    public void loadAll() {
        carModels = loadCarModels();
        voiceItems = loadVoiceItems();
        vehicleProperties = loadVehicleProperties();
        catalogs = loadCatalogs();
        propertyRegMap = new HashMap<>();
        for (CarModel model : carModels) {
            List<PropertyReg> regs = loadPropertyReg(model.getRegFileName());
            propertyRegMap.put(model.getModelName(), regs);
        }
        settings = loadSettings();
        Log.d(TAG, "所有配置加载完成: " + carModels.size() + " 车型, "
                + voiceItems.size() + " 语音, " + vehicleProperties.size() + " 属性");
    }

    // ============ 公共访问方法 ============

    public List<CarModel> getCarModels() { return carModels; }
    public List<VoiceItem> getVoiceItems() { return voiceItems; }
    public List<VehicleProperty> getVehicleProperties() { return vehicleProperties; }
    public List<Catalog> getCatalogs() { return catalogs; }
    public Map<String, String> getSettings() { return settings; }

    public int getCurrentCarModelId() { return currentCarModelId; }
    public void setCurrentCarModelId(int id) { this.currentCarModelId = id; }

    public CarModel getCurrentCarModel() {
        for (CarModel m : carModels) {
            if (m.getId() == currentCarModelId) return m;
        }
        return carModels.isEmpty() ? null : carModels.get(0);
    }

    public List<PropertyReg> getCurrentPropertyRegs() {
        CarModel model = getCurrentCarModel();
        if (model == null) return new ArrayList<>();
        List<PropertyReg> regs = propertyRegMap.get(model.getModelName());
        return regs != null ? regs : new ArrayList<>();
    }

    public List<VoiceItem> getVoiceItemsByTab(VoiceItem.VoiceTab tab) {
        List<VoiceItem> result = new ArrayList<>();
        for (VoiceItem v : voiceItems) {
            if (v.getTab() == tab) result.add(v);
        }
        return result;
    }

    public List<VehicleProperty> getPropertiesByCatalog(String catalog) {
        List<VehicleProperty> result = new ArrayList<>();
        for (VehicleProperty p : vehicleProperties) {
            if (p.getCatalog().equals(catalog) && p.isSupportedForModel(currentCarModelId)) {
                result.add(p);
            }
        }
        return result;
    }

    // ============ CSV 解析器 ============

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

    private List<String> readLines(String fileName) {
        List<String> lines = new ArrayList<>();
        try {
            InputStream is = context.getAssets().open(CONFIGS_DIR + fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    // 去除行末的\r
                    if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                    lines.add(line);
                }
            }
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "读取文件失败: " + fileName, e);
        }
        return lines;
    }

    // ============ 各配置加载方法 ============

    private List<CarModel> loadCarModels() {
        List<CarModel> list = new ArrayList<>();
        List<String> lines = readLines("car_model.csv");
        boolean headerSkipped = false;
        for (String line : lines) {
            if (!headerSkipped) { headerSkipped = true; continue; }
            String[] fields = parseCsvLine(line);
            // 新格式: id, display_name, model_name, package_name, adb_command, reg_file_name
            // 旧格式兼容: id, model_name, reg_file_name (3字段)
            if (fields.length >= 6) {
                try {
                    int id = Integer.parseInt(fields[0]);
                    String displayName = fields[1];
                    String modelName = fields[2];
                    String packageName = fields[3];
                    String adbCommand = fields[4];
                    String regFileName = fields[5];
                    list.add(new CarModel(id, displayName, modelName,
                            packageName, adbCommand, regFileName));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "解析car_model行失败: " + line);
                }
            } else if (fields.length >= 3) {
                // 旧格式兼容：使用旧构造函数
                try {
                    int id = Integer.parseInt(fields[0]);
                    list.add(new CarModel(id, fields[1], fields[1], "", "", fields[2]));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "解析car_model行失败: " + line);
                }
            }
        }
        return list;
    }

    private List<VoiceItem> loadVoiceItems() {
        List<VoiceItem> list = new ArrayList<>();
        List<String> lines = readLines("voice.csv");
        boolean headerSkipped = false;
        for (String line : lines) {
            if (!headerSkipped) { headerSkipped = true; continue; }
            String[] fields = parseCsvLine(line);
            if (fields.length >= 4) {
                try {
                    int id = Integer.parseInt(fields[0]);
                    VoiceItem.VoiceTab tab = VoiceItem.VoiceTab.fromRaw(fields[3]);
                    list.add(new VoiceItem(id, fields[1], fields[2], tab));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "解析voice行失败: " + line);
                }
            }
        }
        return list;
    }

    private List<VehicleProperty> loadVehicleProperties() {
        List<VehicleProperty> list = new ArrayList<>();
        List<String> lines = readLines("vehicle_property.csv");
        boolean headerSkipped = false;
        for (String line : lines) {
            if (!headerSkipped) { headerSkipped = true; continue; }
            String[] fields = parseCsvLine(line);
            if (fields.length >= 13) {
                try {
                    String propertyName = fields[0];
                    String displayName = fields[1];
                    String category = fields[2];
                    String catalog = fields[3];
                    VehicleProperty.ControlType controlType =
                            "SLIDER".equals(fields[4]) ? VehicleProperty.ControlType.SLIDER
                                    : VehicleProperty.ControlType.BUTTON_GROUP;
                    String[] options = fields[5].isEmpty() ? new String[0] : fields[5].split(";");
                    String[] optionValues = fields[6].isEmpty() ? new String[0] : fields[6].split(";");
                    float minVal = fields[7].isEmpty() ? 0 : Float.parseFloat(fields[7]);
                    float maxVal = fields[8].isEmpty() ? 0 : Float.parseFloat(fields[8]);
                    float defaultVal = fields[9].isEmpty() ? 0 : Float.parseFloat(fields[9]);
                    String unit = fields[10];
                    float step = fields[11].isEmpty() ? 1 : Float.parseFloat(fields[11]);
                    String unsupportedIds = fields.length > 12 ? fields[12] : "";
                    list.add(new VehicleProperty(propertyName, displayName, category, catalog,
                            controlType, options, optionValues, minVal, maxVal,
                            defaultVal, unit, step, unsupportedIds));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "解析vehicle_property行失败: " + line);
                }
            }
        }
        return list;
    }

    private List<Catalog> loadCatalogs() {
        List<Catalog> list = new ArrayList<>();
        List<String> lines = readLines("catalog.csv");
        boolean headerSkipped = false;
        for (String line : lines) {
            if (!headerSkipped) { headerSkipped = true; continue; }
            String[] fields = parseCsvLine(line);
            if (fields.length >= 2) {
                list.add(new Catalog(fields[0], fields[1]));
            }
        }
        return list;
    }

    private List<PropertyReg> loadPropertyReg(String fileName) {
        List<PropertyReg> list = new ArrayList<>();
        List<String> lines = readLines(fileName);
        boolean headerSkipped = false;
        for (String line : lines) {
            if (!headerSkipped) { headerSkipped = true; continue; }
            String[] fields = parseCsvLine(line);
            if (fields.length >= 6) {
                try {
                    int id = Integer.parseInt(fields[0]);
                    String valueMapping = fields.length > 5 ? fields[5] : "";
                    list.add(new PropertyReg(id, fields[1], fields[2], fields[3], fields[4], valueMapping));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "解析property_reg行失败: " + line);
                }
            }
        }
        return list;
    }

    private Map<String, String> loadSettings() {
        Map<String, String> map = new HashMap<>();
        List<String> lines = readLines("setting.txt");
        for (String line : lines) {
            String[] parts = line.split(",", 3);
            if (parts.length >= 2) {
                map.put(parts[0].trim(), parts[1].trim());
            }
        }
        return map;
    }

    public VehicleProperty findPropertyByName(String propertyName) {
        for (VehicleProperty p : vehicleProperties) {
            if (p.getPropertyName().equals(propertyName)) return p;
        }
        return null;
    }
}
