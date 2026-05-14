package com.egogame.vehiclehailer.engine;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.egogame.vehiclehailer.VehicleHailerApp;
import com.egogame.vehiclehailer.model.VoiceItem;
import com.egogame.vehiclehailer.model.VoiceItem.VoiceTab;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动触发播放引擎
 * 根据车辆属性状态组合，自动判断应播放的语音和声道
 *
 * 触发规则逻辑（与原App保持一致）：
 * - 系统级语音（SYSTEM）：当属性值匹配时，自动播放到车内
 * - 阻塞级语音（BLOCK）：当属性值匹配且满足组合条件时，自动播放到车外
 */
public class AutoTriggerEngine {

    private static final String TAG = "AutoTriggerEngine";

    private final VehicleStateManager stateManager;
    private final VoicePlayer voicePlayer;
    private final List<VoiceItem> voiceItems;

    // 已播放的语音去重（避免同一状态下重复播放）
    private final Set<Integer> playedVoiceIds = new HashSet<>();

    // 上次各属性的值快照（用于检测变化）
    private final Map<String, String> lastPropertyValues = new ConcurrentHashMap<>();

    // 是否启用自动触发
    private boolean enabled = true;

    // 防抖定时器
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;
    private static final long DEBOUNCE_DELAY_MS = 300;

    public AutoTriggerEngine(VehicleStateManager stateManager, VoicePlayer voicePlayer) {
        this.stateManager = stateManager;
        this.voicePlayer = voicePlayer;
        this.voiceItems = VehicleHailerApp.getInstance().getConfigLoader().getVoiceItems();

        // 注册属性变化监听
        stateManager.addListener((propertyName, oldValue, newValue) -> {
            if (!enabled) return;

            String prevValue = lastPropertyValues.get(propertyName);
            if (prevValue == null || !prevValue.equals(newValue)) {
                lastPropertyValues.put(propertyName, newValue);
                // 防抖：属性变化后延迟300ms再判断触发条件
                debounceTrigger();
            }
        });
    }

    private void debounceTrigger() {
        if (debounceRunnable != null) {
            handler.removeCallbacks(debounceRunnable);
        }
        debounceRunnable = this::evaluateAndTrigger;
        handler.postDelayed(debounceRunnable, DEBOUNCE_DELAY_MS);
    }

    /**
     * 评估当前所有属性状态，判断是否需要触发语音播放
     */
    private void evaluateAndTrigger() {
        if (voiceItems == null) return;

        for (VoiceItem item : voiceItems) {
            if (playedVoiceIds.contains(item.getId())) {
                continue; // 已经播放过，不重复
            }

            if (shouldTrigger(item)) {
                triggerVoice(item);
            }
        }
    }

    /**
     * 判断是否应该触发某条语音
     *
     * 系统级语音：当关联属性值匹配时立即触发
     * 阻塞级语音：当关联属性值匹配且满足阻塞条件时触发
     */
    private boolean shouldTrigger(VoiceItem item) {
        // 根据语音ID映射到触发条件
        // 这里实现与原App一致的触发逻辑
        // 规则：
        //   1001-1011 系统级：匹配属性值即触发
        //   2001-2018 阻塞级：需满足多个条件组合

        if (item.getTab() == VoiceTab.CUSTOM) {
            return checkBlockTrigger(item);
        } else if (item.getTab() == VoiceTab.SYSTEM) {
            return checkSystemTrigger(item);
        }
        return false;
    }

    /**
     * 系统级语音触发条件检查
     * 根据原App的voice.csv配置，系统属性匹配特定值触发
     */
    private boolean checkSystemTrigger(VoiceItem item) {
        switch (item.getId()) {
            case 1001: // Welcome to Deepal — 电源档位ON时触发
                return "2".equals(stateManager.getPropertyValue("power_mode"));
            case 1002: // Welcome to Ego — 电源档位ON时触发（备用）
                return "2".equals(stateManager.getPropertyValue("power_mode"));
            case 1003: // Seat belt reminder — 主驾未系安全带且档位非P
                return "0".equals(stateManager.getPropertyValue("seatbelt_driver"))
                        && !"P".equals(stateManager.getPropertyValue("gear_position"));
            case 1004: // Seat belt reminder (passenger) — 副驾未系安全带
                return "0".equals(stateManager.getPropertyValue("seatbelt_passenger"));
            case 1005: // Left turn signal — 左转向灯激活
                return "1".equals(stateManager.getPropertyValue("turn_signal_left"));
            case 1006: // Right turn signal — 右转向灯激活
                return "1".equals(stateManager.getPropertyValue("turn_signal_right"));
            case 1007: // Hazard lights — 双闪激活
                return "1".equals(stateManager.getPropertyValue("hazard_lights"));
            case 1008: // Low fuel warning — 燃油不足
                return "1".equals(stateManager.getPropertyValue("low_fuel_warning"));
            case 1009: // Door open — 任一车门开启
                return checkAnyDoorOpen();
            case 1010: // Trunk open — 后备箱开启
                return "1".equals(stateManager.getPropertyValue("trunk_status"));
            case 1011: // Speed alert — 超速警告
                String speedStr = stateManager.getPropertyValue("vehicle_speed");
                if (speedStr != null) {
                    try {
                        return Integer.parseInt(speedStr) > 120;
                    } catch (NumberFormatException ignored) {}
                }
                return false;
            default:
                return false;
        }
    }

    /**
     * 阻塞级语音触发条件检查
     * 需要状态组合满足条件
     */
    private boolean checkBlockTrigger(VoiceItem item) {
        switch (item.getId()) {
            case 2001: // 拥堵提醒 — 车速持续低于10km/h且未熄火
                String speedStr = stateManager.getPropertyValue("vehicle_speed");
                if (speedStr != null) {
                    try {
                        return Integer.parseInt(speedStr) < 10
                                && "2".equals(stateManager.getPropertyValue("power_mode"));
                    } catch (NumberFormatException ignored) {}
                }
                return false;
            case 2002: // 行人提醒 — 低速行驶且附近有行人（实际由车辆传感器触发）
                String speedStr2 = stateManager.getPropertyValue("vehicle_speed");
                if (speedStr2 != null) {
                    try {
                        return Integer.parseInt(speedStr2) < 30
                                && Integer.parseInt(speedStr2) > 0;
                    } catch (NumberFormatException ignored) {}
                }
                return false;
            case 2003: // 倒车提醒 — 档位在R
                return "R".equals(stateManager.getPropertyValue("gear_position"));
            case 2004: // 充电提醒 — 充电口连接
                return "1".equals(stateManager.getPropertyValue("charge_port_status"));
            case 2005: // 启动提示音 — 电源ON且档位P
                return "2".equals(stateManager.getPropertyValue("power_mode"))
                        && "P".equals(stateManager.getPropertyValue("gear_position"));
            case 2006: // 熄火提示音 — 电源从ON变OFF
                return "0".equals(stateManager.getPropertyValue("power_mode"));
            case 2007: // 门未关提醒 — 车门开启且档位非P
                return checkAnyDoorOpen()
                        && !"P".equals(stateManager.getPropertyValue("gear_position"));
            case 2008: // 电量不足提醒 — 电池电量低于20%
                String batteryStr = stateManager.getPropertyValue("battery_level");
                if (batteryStr != null) {
                    try {
                        return Integer.parseInt(batteryStr) < 20;
                    } catch (NumberFormatException ignored) {}
                }
                return false;
            case 2009: // 胎压异常提醒
                return "1".equals(stateManager.getPropertyValue("tire_pressure_abnormal"));
            case 2010: // 刹车盘过热提醒
                return "1".equals(stateManager.getPropertyValue("brake_overheat"));
            case 2011: // 自动驻车提示
                return "1".equals(stateManager.getPropertyValue("auto_hold_active"));
            case 2012: // 电子手刹提示
                return "1".equals(stateManager.getPropertyValue("electronic_park_brake"));
            case 2013: // 驾驶模式切换
                return "1".equals(stateManager.getPropertyValue("drive_mode_switch"));
            case 2014: // 陡坡缓降启用
                return "1".equals(stateManager.getPropertyValue("hill_descent_control"));
            case 2015: // 前向碰撞预警
                return "1".equals(stateManager.getPropertyValue("fcw_alert"));
            case 2016: // 车道偏离提醒
                return "1".equals(stateManager.getPropertyValue("ldw_alert"));
            case 2017: // 盲区监测提醒
                return "1".equals(stateManager.getPropertyValue("bsd_alert"));
            case 2018: // 后方交通穿行提醒
                return "1".equals(stateManager.getPropertyValue("rcta_alert"));
            default:
                return false;
        }
    }

    private boolean checkAnyDoorOpen() {
        return "1".equals(stateManager.getPropertyValue("door_front_left"))
                || "1".equals(stateManager.getPropertyValue("door_front_right"))
                || "1".equals(stateManager.getPropertyValue("door_rear_left"))
                || "1".equals(stateManager.getPropertyValue("door_rear_right"));
    }

    /**
     * 触发语音播放
     */
    private void triggerVoice(VoiceItem item) {
        Log.d(TAG, "触发语音: id=" + item.getId() + " name=" + item.getName());

        // 自定义音效放车内（STREAM_MUSIC），系统音效放车外（STREAM_VOICE_CALL）
        if (item.getTab() == VoiceTab.CUSTOM) {
            voicePlayer.setChannel(VoicePlayer.Channel.INSIDE);
        } else {
            voicePlayer.setChannel(VoicePlayer.Channel.OUTSIDE);
        }

        voicePlayer.play(item.getFilePath());
        playedVoiceIds.add(item.getId());

        // 自定义音效播放完后自动重置"已播放"状态
        if (item.getTab() == VoiceTab.CUSTOM) {
            handler.postDelayed(() -> {
                playedVoiceIds.remove(item.getId());
            }, 10000); // 10秒后可再次触发
        }
    }

    /**
     * 重置触发引擎（属性状态回退后重置已播放标记）
     */
    public void reset() {
        playedVoiceIds.clear();
        lastPropertyValues.clear();
    }

    /**
     * 设置启用/禁用自动触发
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            reset();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
