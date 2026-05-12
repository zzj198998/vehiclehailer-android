package com.egogame.vehiclehailer.model;

/**
 * 车型配置
 * 包含车型基本信息、adb授权包名、授权命令等
 */
public class CarModel {
    private final int id;
    private final String displayName;
    private final String modelName;
    private final String packageName;
    private final String adbCommand;
    private final String regFileName;

    public CarModel(int id, String displayName, String modelName,
                    String packageName, String adbCommand, String regFileName) {
        this.id = id;
        this.displayName = displayName;
        this.modelName = modelName;
        this.packageName = packageName;
        this.adbCommand = adbCommand;
        this.regFileName = regFileName;
    }

    public int getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getModelName() { return modelName; }
    public String getPackageName() { return packageName; }
    public String getAdbCommand() { return adbCommand; }
    public String getRegFileName() { return regFileName; }

    /**
     * 获取adb授权命令（带包名）
     */
    public String getFormattedAdbCommand() {
        if (packageName == null || packageName.isEmpty()) {
            return adbCommand;
        }
        return "adb shell pm grant " + packageName + " android.permission.READ_LOGS";
    }
}
