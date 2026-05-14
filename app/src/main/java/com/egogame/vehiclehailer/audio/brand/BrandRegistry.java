package com.egogame.vehiclehailer.audio.brand;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.egogame.vehiclehailer.model.CarModel;

import java.util.HashMap;
import java.util.Map;

/**
 * 品牌适配器注册表
 *
 * 负责：
 * 1. 管理所有已注册的品牌适配器
 * 2. 根据 CarModel（车型配置）自动匹配对应品牌的适配器
 * 3. 未匹配到任何品牌时，返回 GenericBrandAdapter 作为兜底
 * 4. 支持运行时动态注册新品牌适配器
 *
 * 对比鱼蛋：鱼蛋没有品牌注册机制，DeepalBoxVoiceHelper 需要手动调用。
 * 我们的 BrandRegistry 自动匹配，用户无需关心品牌适配细节。
 */
public class BrandRegistry {

    private static final String TAG = "BrandRegistry";

    // 品牌标识 → 适配器 映射
    private final Map<String, BrandAdapter> adapterMap = new HashMap<>();

    // 品牌包名关键词 → 品牌标识 映射（用于通过 CarModel 的包名自动匹配）
    // 例如 "egogame.deepalbox" → "Deepal"
    private final Map<String, String> packageKeywordToBrand = new HashMap<>();

    // 当前激活的适配器
    private BrandAdapter activeAdapter;

    // 通用兜底适配器
    private final BrandAdapter genericAdapter = new GenericBrandAdapter();

    private Context context;
    private boolean initialized = false;

    /**
     * 初始化注册表
     * 在 Application.onCreate 或首次使用音频时调用
     */
    public void init(Context context) {
        this.context = context.getApplicationContext();
        // 注册内置的包名关键词映射（通过包名特征识别品牌）
        registerBrandPackageKeyword("egogame.deepalbox", "Deepal");
        registerBrandPackageKeyword("egogame.deepal", "Deepal");
        // 其他品牌预留：随着适配器增加而添加
        // registerBrandPackageKeyword("byd", "BYD");
        // registerBrandPackageKeyword("geely", "Geely");
        // registerBrandPackageKeyword("changan", "Changan");

        // 默认使用通用适配器
        setActiveAdapter(genericAdapter);
        genericAdapter.init(context);
        initialized = true;

        Log.d(TAG, "品牌注册表初始化完成，当前适配器: " + activeAdapter.getBrandName());
    }

    /**
     * 注册品牌包名关键词映射
     * 用于通过 CarModel 的包名自动匹配品牌适配器
     *
     * @param packageKeyword 包名中的特征关键词
     * @param brandName      品牌标识名称（必须与适配器的 getBrandName() 一致）
     */
    public void registerBrandPackageKeyword(String packageKeyword, String brandName) {
        if (!TextUtils.isEmpty(packageKeyword) && !TextUtils.isEmpty(brandName)) {
            packageKeywordToBrand.put(packageKeyword.toLowerCase(), brandName);
        }
    }

    /**
     * 注册一个品牌适配器
     * 适配器注册后，当匹配到对应品牌时会自动激活
     *
     * @param adapter 品牌适配器实例
     */
    public void registerAdapter(BrandAdapter adapter) {
        if (adapter == null) return;
        String brandName = adapter.getBrandName();
        if (TextUtils.isEmpty(brandName)) return;
        adapterMap.put(brandName, adapter);
        Log.d(TAG, "品牌适配器已注册: " + brandName);
    }

    /**
     * 根据 CarModel 自动匹配并激活品牌适配器
     * 优先匹配：包名关键词 → 精确品牌名 → 通用兜底
     *
     * @param carModel 当前选择的车型配置
     * @return 匹配到的品牌适配器（不会返回）
     */
    public BrandAdapter matchByCarModel(CarModel carModel) {
        if (carModel == null) {
            Log.d(TAG, "CarModel 为空，使用通用适配器");
            return setActiveAdapter(genericAdapter);
        }

        String packageName = carModel.getPackageName();
        if (!TextUtils.isEmpty(packageName)) {
            String lowerPkg = packageName.toLowerCase();

            // 通过包名关键词匹配
            for (Map.Entry<String, String> entry : packageKeywordToBrand.entrySet()) {
                if (lowerPkg.contains(entry.getKey())) {
                    String matchedBrand = entry.getValue();
                    BrandAdapter adapter = adapterMap.get(matchedBrand);
                    if (adapter != null) {
                        Log.d(TAG, "通过包名关键词匹配到品牌: " + matchedBrand
                                + " (包名: " + packageName + ")");
                        return setActiveAdapter(adapter);
                    }
                }
            }
        }

        // 精确品牌名匹配（如果 CarModel 的 displayName 包含品牌名）
        String displayName = carModel.getDisplayName();
        if (!TextUtils.isEmpty(displayName)) {
            for (BrandAdapter adapter : adapterMap.values()) {
                if (displayName.toLowerCase().contains(
                        adapter.getBrandName().toLowerCase())) {
                    Log.d(TAG, "通过显示名称匹配到品牌: " + adapter.getBrandName()
                            + " (显示名: " + displayName + ")");
                    return setActiveAdapter(adapter);
                }
            }
        }

        // 未匹配任何品牌，使用通用适配器
        Log.d(TAG, "未匹配到品牌适配器（包名: " + packageName
                + ", 车型: " + displayName + "），使用通用适配器");
        return setActiveAdapter(genericAdapter);
    }

    /**
     * 手动设置品牌适配器（用户可选择）
     *
     * @param brandName 品牌标识名称
     * @return 设置的适配器，如果未找到则返回通用适配器
     */
    public BrandAdapter setBrand(String brandName) {
        if (TextUtils.isEmpty(brandName)) {
            return setActiveAdapter(genericAdapter);
        }
        BrandAdapter adapter = adapterMap.get(brandName);
        if (adapter != null) {
            Log.d(TAG, "手动设置品牌适配器: " + brandName);
            return setActiveAdapter(adapter);
        }
        Log.w(TAG, "未找到品牌适配器: " + brandName + "，使用通用适配器");
        return setActiveAdapter(genericAdapter);
    }

    /**
     * 获取当前激活的品牌适配器
     */
    public BrandAdapter getActiveAdapter() {
        if (activeAdapter == null) {
            return genericAdapter;
        }
        return activeAdapter;
    }

    /**
     * 获取已注册的所有适配器名称列表（用于设置界面展示）
     */
    public String[] getRegisteredBrandNames() {
        return adapterMap.keySet().toArray(new String[0]);
    }

    /**
     * 切换当前适配器
     */
    private BrandAdapter setActiveAdapter(BrandAdapter adapter) {
        if (adapter == activeAdapter) return adapter;

        // 释放旧适配器
        if (activeAdapter != null && activeAdapter != genericAdapter) {
            activeAdapter.release();
        }

        activeAdapter = adapter;

        // 初始化新适配器
        if (context != null) {
            adapter.init(context);
        }

        Log.d(TAG, "当前适配器已切换为: " + adapter.getBrandName()
                + " (自定义喇叭控制: " + adapter.hasCustomOutspeakerControl() + ")");
        return adapter;
    }

    /**
     * 释放所有资源
     */
    public void release() {
        if (activeAdapter != null && activeAdapter != genericAdapter) {
            activeAdapter.release();
        }
        genericAdapter.release();
        activeAdapter = null;
        adapterMap.clear();
        packageKeywordToBrand.clear();
        initialized = false;
        Log.d(TAG, "品牌注册表已释放");
    }

    public boolean isInitialized() {
        return initialized;
    }
}
