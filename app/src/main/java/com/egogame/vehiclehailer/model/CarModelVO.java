package com.egogame.vehiclehailer.model;

/**
 * 车型配置VO（对应鱼蛋的 CarModelVO）
 *
 * 鱼蛋 CarModelVO 字段：
 * - id / modelName / regFileName
 *
 * 我们增强：
 * - 增加 brandName 品牌名称字段（用于品牌注册表自动匹配）
 * - 增加 displayName 显示名称
 * - 增加 packageName 包名（用于品牌适配器匹配）
 * - 增加 adbCommand 授权命令
 */
public class CarModelVO {

    private int id;                 // 车型ID
    private String modelName;       // 车型名称（英文标识）
    private String displayName;     // 显示名称（中文）【新增】
    private String brandName;       // 品牌名称，如 "Deepal"、"BYD"【新增】
    private String packageName;     // 授权包名，用于匹配品牌适配器【新增】
    private String regFileName;     // 属性注册文件名
    private String adbCommand;      // adb授权命令【新增】

    /**
     * 无参构造（用于JSON反序列化）
     */
    public CarModelVO() {}

    /**
     * 全参构造
     */
    public CarModelVO(int id, String modelName, String displayName, String brandName,
                      String packageName, String regFileName, String adbCommand) {
        this.id = id;
        this.modelName = modelName;
        this.displayName = displayName;
        this.brandName = brandName;
        this.packageName = packageName;
        this.regFileName = regFileName;
        this.adbCommand = adbCommand;
    }

    /**
     * 从已有的 CarModel 转换（兼容旧对象）
     */
    public static CarModelVO fromCarModel(com.egogame.vehiclehailer.model.CarModel carModel) {
        if (carModel == null) return null;
        return new CarModelVO(
                carModel.getId(),
                carModel.getModelName(),
                carModel.getDisplayName(),
                null, // brandName — 需要从外部设置
                carModel.getPackageName(),
                carModel.getRegFileName(),
                carModel.getAdbCommand()
        );
    }

    // ---- Getter/Setter ----

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getRegFileName() { return regFileName; }
    public void setRegFileName(String regFileName) { this.regFileName = regFileName; }

    public String getAdbCommand() { return adbCommand; }
    public void setAdbCommand(String adbCommand) { this.adbCommand = adbCommand; }

    @Override
    public String toString() {
        return "CarModelVO{" +
                "id=" + id +
                ", modelName='" + modelName + '\'' +
                ", brandName='" + brandName + '\'' +
                '}';
    }
}
