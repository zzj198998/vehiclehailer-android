package com.egogame.vehiclehailer.action;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.Map;

/**
 * 发送任意Intent动作 — 通用扩展机制
 *
 * 支持动态构建任意Intent，可设置：
 * - Action
 * - Package + Class（显式Intent）
 * - Data URI
 * - Extra参数（支持String/Integer/Boolean/Map）
 * - 多种发送方式（startActivity / startService / sendBroadcast）
 *
 * 对比鱼蛋的 SendIntentAction：
 * 鱼蛋功能：
 * - mAction / mTargetPackage / mTargetClass / mDataUri / mDeliveryMethod / mExtras
 * - 通过 onStartImplement() 中的 switch 处理不同 deliveryMethod
 *
 * 我们增强：
 * - 更多Extra类型支持（int/boolean/long/float，鱼蛋只支持String）
 * - 更清晰的DeliveryMethod枚举而非字符串硬编码
 * - Builder模式构建
 * - 详细的日志和异常处理
 */
public class SendIntentAction extends VehicleActionBase {

    private static final String TAG = "SendIntentAction";

    /** 发送方式 */
    public enum DeliveryMethod {
        START_ACTIVITY,     // startActivity
        START_SERVICE,      // startService
        SEND_BROADCAST      // sendBroadcast
    }

    // 构造参数
    private String intentAction;
    private String targetPackage;
    private String targetClass;
    private String dataUri;
    private DeliveryMethod deliveryMethod = DeliveryMethod.START_SERVICE;
    private Map<String, Object> extras;
    private int[] flags;

    // 内部缓存
    private Context context;

    /**
     * 最小构造：仅Action + 发送方式（隐式Intent）
     */
    public SendIntentAction(Context context, String intentAction, DeliveryMethod method) {
        super(ActionType.SEND_INTENT);
        this.context = context;
        this.intentAction = intentAction;
        this.deliveryMethod = method != null ? method : DeliveryMethod.START_SERVICE;
    }

    /**
     * 完整构造：显式Intent（指定Package和Class）
     */
    public SendIntentAction(Context context, String intentAction, String targetPackage,
                            String targetClass, DeliveryMethod method) {
        this(context, intentAction, method);
        this.targetPackage = targetPackage;
        this.targetClass = targetClass;
    }

    /**
     * Builder模式设置dataUri
     */
    public SendIntentAction setDataUri(String dataUri) {
        this.dataUri = dataUri;
        return this;
    }

    /**
     * Builder模式设置extra参数
     */
    public SendIntentAction setExtras(Map<String, Object> extras) {
        this.extras = extras;
        return this;
    }

    /**
     * 设置Intent flags
     */
    public SendIntentAction setFlags(int... flags) {
        this.flags = flags;
        return this;
    }

    @Override
    public void execute() {
        if (context == null || TextUtils.isEmpty(intentAction)) {
            Log.e(TAG, "执行失败: context或action为空");
            setStatus(ActionStatus.FAILED);
            return;
        }

        try {
            Intent intent = new Intent();

            // 设置Action
            if (!TextUtils.isEmpty(intentAction)) {
                intent.setAction(intentAction);
            }

            // 设置显式目标（Package + Class）
            if (!TextUtils.isEmpty(targetPackage) && !TextUtils.isEmpty(targetClass)) {
                intent.setComponent(new ComponentName(targetPackage, targetClass));
            }

            // 设置Data URI
            if (!TextUtils.isEmpty(dataUri)) {
                intent.setData(Uri.parse(dataUri));
            }

            // 设置Extra参数
            putExtras(intent, extras);

            // 设置Flags
            if (flags != null && flags.length > 0) {
                for (int flag : flags) {
                    intent.addFlags(flag);
                }
            }

            // 根据发送方式执行
            switch (deliveryMethod) {
                case START_ACTIVITY:
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    break;
                case START_SERVICE:
                    context.startService(intent);
                    break;
                case SEND_BROADCAST:
                    context.sendBroadcast(intent);
                    break;
            }

            setStatus(ActionStatus.COMPLETED);
        } catch (Exception e) {
            Log.e(TAG, "发送Intent失败: action=" + intentAction
                    + ", pkg=" + targetPackage, e);
            setStatus(ActionStatus.FAILED);
        }
    }

    /**
     * 将Map参数注入到Intent中
     * 支持 String / Integer / Boolean / Long / Float / Double
     */
    private void putExtras(Intent intent, Map<String, Object> extras) {
        if (extras == null || extras.isEmpty()) return;

        for (Map.Entry<String, Object> entry : extras.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) continue;

            if (value instanceof String) {
                intent.putExtra(key, (String) value);
            } else if (value instanceof Integer) {
                intent.putExtra(key, (Integer) value);
            } else if (value instanceof Boolean) {
                intent.putExtra(key, (Boolean) value);
            } else if (value instanceof Long) {
                intent.putExtra(key, (Long) value);
            } else if (value instanceof Float) {
                intent.putExtra(key, (Float) value);
            } else if (value instanceof Double) {
                intent.putExtra(key, (Double) value);
            } else {
                Log.w(TAG, "不支持的Extra类型: " + value.getClass().getName() + " for key=" + key);
            }
        }
    }
}
