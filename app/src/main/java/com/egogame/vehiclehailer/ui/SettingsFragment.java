package com.egogame.vehiclehailer.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.egogame.vehiclehailer.R;
import com.egogame.vehiclehailer.VehicleHailerApp;
import com.egogame.vehiclehailer.audio.AudioRouter;
import com.egogame.vehiclehailer.engine.ConfigLoader;
import com.egogame.vehiclehailer.engine.TriggerRuleConfig;
import com.egogame.vehiclehailer.engine.VehicleEventTrigger;
import com.egogame.vehiclehailer.engine.VoicePlayer;
import com.egogame.vehiclehailer.model.CarModel;
import androidx.appcompat.widget.SwitchCompat;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsFragment extends Fragment {

    /**
     * 安全设置Switch文本：防止Android 14上SwitchCompat.makeLayout因文本崩溃
     */
    private void safeSetSwitchText(SwitchCompat sw, int resId) {
        if (sw != null) {
            sw.setText(resId);
            // 重置安全检查（防止系统主题覆写）
            if (sw.getText() == null) {
                sw.setText("");
            }
        }
    }

    private void safeSetSwitchText(SwitchCompat sw, CharSequence text) {
        if (sw != null) {
            sw.setText(text);
            if (sw.getText() == null) {
                sw.setText("");
            }
        }
    }

    private Spinner carModelSpinner;
    private SwitchCompat channelSwitch;
    private SwitchCompat ttsEnabledSwitch;
    private TextInputEditText ttsUrlInput;
    private SwitchCompat autoPlaySwitch;

    private ConfigLoader configLoader;
    private VoicePlayer voicePlayer;
    private AudioRouter audioRouter;
    private VehicleEventTrigger eventTrigger;
    private TriggerRuleConfig ruleConfig;

    private TextView adbCmdView;
    private TextView carModelPackageView;
    private TextView adbCopiedHint;

    // 5个声音源的路由控制UI
    private final Map<AudioRouter.SoundSource, RouteControl> routeControls = new HashMap<>();

    private static class RouteControl {
        Spinner channelSpinner;
        SeekBar volumeSeekBar;
        TextView volumeText;
        SwitchCompat noiseSwitch;
        SwitchCompat voiceSwitch;
        boolean updating; // 防止循环回调
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 使用ContextThemeWrapper确保Material组件主题不被系统覆写（OPPO realme ColorOS会强制覆写Theme.DeviceDefault）
        ContextThemeWrapper contextWrapper = new ContextThemeWrapper(inflater.getContext(), R.style.Theme_VehicleHailer);
        LayoutInflater themedInflater = inflater.cloneInContext(contextWrapper);
        return themedInflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        configLoader = VehicleHailerApp.getInstance().getConfigLoader();
        voicePlayer = VehicleHailerApp.getInstance().getVoicePlayer();
        audioRouter = ((MainActivity) requireActivity()).getAudioRouter();
        eventTrigger = ((MainActivity) requireActivity()).getVehicleEventTrigger();
        ruleConfig = ((MainActivity) requireActivity()).getTriggerRuleConfig();

        carModelSpinner = view.findViewById(R.id.car_model_spinner);
        channelSwitch = view.findViewById(R.id.channel_switch);
        ttsEnabledSwitch = view.findViewById(R.id.tts_enabled_switch);
        ttsUrlInput = view.findViewById(R.id.tts_url_input);
        autoPlaySwitch = view.findViewById(R.id.auto_play_switch);

        // 车型adb信息控件
        // carModelPackageView = view.findViewById(R.id.car_model_spinner); // 借用提示区域（已废弃，Spinner不能强转TextView）
        adbCmdView = view.findViewById(R.id.adb_cmd_self);
        // 将adb_cmd_other区域改为显示车型包名
        View adbOtherParent = view.findViewById(R.id.adb_cmd_other);
        adbCopiedHint = view.findViewById(R.id.adb_copied_hint);

        // 车型选择
        List<CarModel> carModels = configLoader.getCarModels();
        String[] modelNames = new String[carModels.size()];
        for (int i = 0; i < carModels.size(); i++) {
            modelNames[i] = carModels.get(i).getDisplayName();
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, modelNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        carModelSpinner.setAdapter(spinnerAdapter);

        // 默认选中当前已设置的车型
        int currentId = configLoader.getCurrentCarModelId();
        int defaultIndex = 0;
        for (int i = 0; i < carModels.size(); i++) {
            if (carModels.get(i).getId() == currentId) {
                defaultIndex = i;
                break;
            }
        }
        carModelSpinner.setSelection(defaultIndex);

        // 初始化显示当前车型的adb信息
        updateCarModelInfo(defaultIndex);

        // 车型切换 → 更新adb + 联动规则
        carModelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean isInitial = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CarModel selected = configLoader.getCarModels().get(position);
                configLoader.setCurrentCarModelId(selected.getId());

                // 更新adb信息
                updateCarModelInfo(position);

                // 切换联动规则（跳过首次初始化）
                if (!isInitial) {
                    reloadTriggerRules(selected);
                }
                isInitial = false;

                Toast.makeText(getContext(), "已切换至: " + selected.getDisplayName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 声道切换（车内/车外）- 先安全设置文本防止SwitchCompat崩溃
        safeSetSwitchText(channelSwitch, R.string.setting_channel_inside);
        channelSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                voicePlayer.setChannel(VoicePlayer.Channel.INSIDE);
                safeSetSwitchText(channelSwitch, R.string.setting_channel_inside);
            } else {
                voicePlayer.setChannel(VoicePlayer.Channel.OUTSIDE);
                safeSetSwitchText(channelSwitch, R.string.setting_channel_outside);
            }
        });

        // 初始化5个声音源的路由控制
        initRouteControls(view);

        // adb命令点击复制（使用动态显示的命令）
        adbCmdView.setOnClickListener(v -> {
            String commandText = adbCmdView.getText().toString();
            if (commandText.isEmpty()) return;
            ClipboardManager clipboard = (ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("adb_command", commandText);
            clipboard.setPrimaryClip(clip);
            adbCopiedHint.setText(R.string.connection_adb_copied);
            Toast.makeText(getContext(), "✅ 已复制: " + commandText, Toast.LENGTH_LONG).show();
        });
    }

    private void initRouteControls(View view) {
        // 喊话：id_route_hailing_*
        registerRouteControl(view, AudioRouter.SoundSource.HAILING,
                R.id.route_hailing_channel, R.id.route_hailing_volume, R.id.route_hailing_volume_text,
                R.id.route_hailing_noise, R.id.route_hailing_voice);

        // 开关门：id_route_door_*
        registerRouteControl(view, AudioRouter.SoundSource.DOOR_SOUND,
                R.id.route_door_channel, R.id.route_door_volume, R.id.route_door_volume_text,
                R.id.route_door_noise, R.id.route_door_voice);

        // 锁车：id_route_lock_*
        registerRouteControl(view, AudioRouter.SoundSource.LOCK_SOUND,
                R.id.route_lock_channel, R.id.route_lock_volume, R.id.route_lock_volume_text,
                R.id.route_lock_noise, R.id.route_lock_voice);

        // 模拟声浪：固定车外，id_route_soundwave_*
        registerRouteControl(view, AudioRouter.SoundSource.SOUNDWAVE_SIM,
                R.id.route_soundwave_channel, R.id.route_soundwave_volume, R.id.route_soundwave_volume_text,
                R.id.route_soundwave_noise, R.id.route_soundwave_voice);

        // 自定义音效：id_route_custom_*
        registerRouteControl(view, AudioRouter.SoundSource.CUSTOM_AUDIO,
                R.id.route_custom_channel, R.id.route_custom_volume, R.id.route_custom_volume_text,
                R.id.route_custom_noise, R.id.route_custom_voice);
    }

    private void registerRouteControl(View view, AudioRouter.SoundSource source,
                                      int channelId, int volumeId, int volumeTextId,
                                      int noiseId, int voiceId) {
        RouteControl control = new RouteControl();
        control.channelSpinner = view.findViewById(channelId);
        control.volumeSeekBar = view.findViewById(volumeId);
        control.volumeText = view.findViewById(volumeTextId);
        control.noiseSwitch = view.findViewById(noiseId);
        control.voiceSwitch = view.findViewById(voiceId);

        AudioRouter.SoundRouteConfig config = audioRouter.getRouteConfig(source);

        // 通道Spinner
        ArrayAdapter<String> channelAdapter;
        if (config != null && config.fixedOutside) {
            // 固定车外：只显示"车外（固定）"
            channelAdapter = new ArrayAdapter<>(getContext(),
                    android.R.layout.simple_spinner_item,
                    new String[]{getString(R.string.channel_outside_fixed)});
            control.channelSpinner.setEnabled(false);
        } else {
            channelAdapter = new ArrayAdapter<>(getContext(),
                    android.R.layout.simple_spinner_item,
                    new String[]{
                            getString(R.string.channel_inside),
                            getString(R.string.channel_outside),
                            getString(R.string.channel_both)
                    });
            control.channelSpinner.setEnabled(true);
        }
        channelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        control.channelSpinner.setAdapter(channelAdapter);

        if (config != null) {
            // 设置通道选中位置
            if (config.fixedOutside) {
                control.channelSpinner.setSelection(0);
            } else {
                switch (config.channel) {
                    case INSIDE: control.channelSpinner.setSelection(0); break;
                    case OUTSIDE: control.channelSpinner.setSelection(1); break;
                    case BOTH: control.channelSpinner.setSelection(2); break;
                }
            }
        }

        control.channelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                if (control.updating) return;
                if (config != null && config.fixedOutside) return;

                AudioRouter.SpeakerChannel channel;
                switch (position) {
                    case 0: channel = AudioRouter.SpeakerChannel.INSIDE; break;
                    case 1: channel = AudioRouter.SpeakerChannel.OUTSIDE; break;
                    case 2: channel = AudioRouter.SpeakerChannel.BOTH; break;
                    default: channel = AudioRouter.SpeakerChannel.INSIDE;
                }
                audioRouter.setRoute(source, channel);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 音量SeekBar
        if (config != null) {
            control.volumeSeekBar.setProgress(config.volume);
            control.volumeText.setText(getString(R.string.volume_hint, config.volume));
        }
        control.volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    control.volumeText.setText(getString(R.string.volume_hint, progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                audioRouter.setVolume(source, seekBar.getProgress());
            }
        });

        // 降噪开关
        if (config != null) {
            control.noiseSwitch.setChecked(config.noiseReduction);
        }
        control.noiseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AudioRouter.SoundRouteConfig cfg = audioRouter.getRouteConfig(source);
            if (cfg != null) {
                audioRouter.setRouteConfig(source, cfg.channel, cfg.volume,
                        isChecked, cfg.voiceEnhance);
            }
        });

        // 人声增强开关
        if (config != null) {
            control.voiceSwitch.setChecked(config.voiceEnhance);
        }
        control.voiceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AudioRouter.SoundRouteConfig cfg = audioRouter.getRouteConfig(source);
            if (cfg != null) {
                audioRouter.setRouteConfig(source, cfg.channel, cfg.volume,
                        cfg.noiseReduction, isChecked);
            }
        });

        routeControls.put(source, control);
    }

    /**
     * 更新车型adb信息显示
     */
    private void updateCarModelInfo(int position) {
        CarModel selected = configLoader.getCarModels().get(position);
        // 在adb命令区域上方显示包名
        if (adbCmdView != null) {
            String adbCmd = selected.getFormattedAdbCommand();
            adbCmdView.setText(adbCmd);
        }
    }

    /**
     * 切换车型时重新加载联动规则
     */
    private void reloadTriggerRules(CarModel newModel) {
        if (eventTrigger == null || ruleConfig == null) return;

        eventTrigger.clearRules();
        ruleConfig.reset();
        ruleConfig.loadDefaults();
        eventTrigger.addRules(ruleConfig.getSystemRules());
        eventTrigger.setVoiceMap(
                VehicleHailerApp.getInstance().getConfigLoader().getVoiceItems());

        android.util.Log.d("SettingsFragment", "车型切换至 [" + newModel.getDisplayName()
                + "]，已加载 " + ruleConfig.getSystemRules().size() + " 条联动规则");
    }
}
