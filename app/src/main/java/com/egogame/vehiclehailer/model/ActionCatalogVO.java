package com.egogame.vehiclehailer.model;

/**
 * 动作目录分类映射VO（对应鱼蛋的 ActionCatalogVO）
 *
 * 用于将动作的 catalog 标识映射为可读的显示名称。
 * 例如：catalog="door" → displayName="车门"
 *
 * 鱼蛋 ActionCatalogVO 字段：
 * - catalog / displayName
 *
 * 我们增强：
 * - 增加 description 描述说明
 */
public class ActionCatalogVO {

    private String catalog;         // 目录标识，如 "door"、"lock"、"window"
    private String displayName;     // 显示名称，如 "车门"、"锁车"、"车窗"
    private String description;     // 描述说明【新增】

    /**
     * 无参构造（用于JSON反序列化）
     */
    public ActionCatalogVO() {}

    /**
     * 全参构造
     */
    public ActionCatalogVO(String catalog, String displayName, String description) {
        this.catalog = catalog;
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 简易构造
     */
    public ActionCatalogVO(String catalog, String displayName) {
        this(catalog, displayName, null);
    }

    // ---- Getter/Setter ----

    public String getCatalog() { return catalog; }
    public void setCatalog(String catalog) { this.catalog = catalog; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "ActionCatalogVO{" +
                "catalog='" + catalog + '\'' +
                ", displayName='" + displayName + '\'' +
                '}';
    }
}
