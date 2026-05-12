package com.egogame.vehiclehailer.model;

/**
 * 目录分类
 */
public class Catalog {
    private String catalog;
    private String displayName;

    public Catalog(String catalog, String displayName) {
        this.catalog = catalog;
        this.displayName = displayName;
    }

    public String getCatalog() { return catalog; }
    public String getDisplayName() { return displayName; }
}
