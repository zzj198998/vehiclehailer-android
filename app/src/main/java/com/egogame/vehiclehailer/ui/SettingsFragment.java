package com.egogame.vehiclehailer.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.egogame.vehiclehailer.R;
import com.egogame.vehiclehailer.VehicleHailerApp;
import com.egogame.vehiclehailer.audio.AudioRouter;
import com.egogame.vehiclehailer.engine.ConfigLoader;
import com.egogame.vehiclehailer.engine.TriggerRuleConfig;
import com.egogame.vehiclehailer.engine.VehicleEventTrigger;
import com.egogame.vehiclehailer.engine.VoicePlayer;
import com.egogame.vehiclehailer.floatball.FloatBallManager;
import com.egogame.vehiclehailer.model.CarModel;
import com.egogame.vehiclehailer.service.AudioRecordService;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsFragment extends Fragment {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1002;

    private MaterialSwitch floatBallSwitch;
    private MaterialSwitch realTimeVoiceSwitch;
    private FloatBallManager floatBallManager;
    private boolean isRecording = false;

    private Spinner carModelSpinner;
    private MaterialSwitch channelSwitch;
    private MaterialSwitch ttsEnabledSwitch;
    private TextInputEditText ttsUrlInput;
    private MaterialSwitch autoPlaySwitch;

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
        MaterialSwitch noiseSwitch;
        MaterialSwitch voiceSwitch;
        boolean updating; // 防止循环回调
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
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
        carModelPackageView = view.findViewById(R.id.car_model_spinner); // 借用提示区域
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

                Toast.makeText(getContext(), getString(R.string.car_model_switched, selected.getDisplayName()), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 声道切换（车内/车外）
        channelSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                voicePlayer.setChannel(VoicePlayer.Channel.INSIDE);
                channelSwitch.setText(R.string.setting_channel_inside);
            } else {
                voicePlayer.setChannel(VoicePlayer.Channel.OUTSIDE);
                channelSwitch.setText(R.string.setting_channel_outside);
            }
        });

        // 初始化5个声音源的路由控制
        initRouteControls(view);

        // ===== 绑定新功能卡片控件 =====

        // 激活状态 + 有效期
        TextView activationStatus = view.findViewById(R.id.activation_status);
        if (activationStatus != null) {
            // 计算剩余有效期（示例：永久有效）
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DAY_OF_MONTH);
            activationStatus.setText(getString(R.string.activation_status, year, month, day));
        }

        // 日志权限开关
        MaterialSwitch logPermissionSwitch = view.findViewById(R.id.log_permission_switch);
        if (logPermissionSwitch != null) {
            logPermissionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    Log.d("SettingsFragment", "日志权限已开启");
                    // TODO: 实际实现日志权限授予逻辑
                } else {
                    Log.d("SettingsFragment", "日志权限已关闭");
                }
            });
        }

        // 车内音/车外音响开关
        TextView carAudioStatus = view.findViewById(R.id.car_audio_status);
        MaterialSwitch carAudioSwitch = view.findViewById(R.id.car_audio_switch);
        if (carAudioSwitch != null && carAudioStatus != null) {
            carAudioSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    carAudioStatus.setText(R.string.car_audio_inside);
                    // 同步到声道切换开关
                    if (channelSwitch != null) {
                        channelSwitch.setChecked(true);
                        channelSwitch.setText(R.string.setting_channel_inside);
                    }
                } else {
                    carAudioStatus.setText(R.string.car_audio_outside);
                    // 同步到声道切换开关
                    if (channelSwitch != null) {
                        channelSwitch.setChecked(false);
                        channelSwitch.setText(R.string.setting_channel_outside);
                    }
                }
            });
        }

        // 版本信息
        TextView versionInfoView = view.findViewById(R.id.version_info);
        if (versionInfoView != null) {
            String versionStr = "Version: 20260110170810";
            try {
                versionStr = "Version: " + requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            } catch (Exception e) {
                // 使用默认版本号
            }
            versionInfoView.setText(versionStr);
        }

        // adb命令点击复制（使用动态显示的命令）
        adbCmdView.setOnClickListener(v -> {
            String commandText = adbCmdView.getText().toString();
            if (commandText.isEmpty()) return;
            ClipboardManager clipboard = (ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("adb_command", commandText);
            clipboard.setPrimaryClip(clip);
            adbCopiedHint.setText(R.string.connection_adb_copied);
            Toast.makeText(getContext(), getString(R.string.adb_copied, commandText), Toast.LENGTH_LONG).show();
        });

        // ===== 悬浮球控制绑定 =====
        floatBallManager = FloatBallManager.getInstance(requireContext());
        floatBallSwitch = view.findViewById(R.id.float_ball_switch);
        realTimeVoiceSwitch = view.findViewById(R.id.real_time_voice_switch);

        // 悬浮球启用/禁用
        if (floatBallSwitch != null) {
            floatBallSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // 检查悬浮窗权限
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            !android.provider.Settings.canDrawOverlays(requireContext())) {
                        // 请求悬浮窗权限
                        Intent intent = new Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + requireContext().getPackageName()));
                        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                        // 恢复开关状态，等权限回来后再打开
                        floatBallSwitch.setChecked(false);
                        return;
                    }
                    // 检查录音权限
                    if (ContextCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(requireActivity(),
                                new String[]{Manifest.permission.RECORD_AUDIO},
                                REQUEST_RECORD_AUDIO_PERMISSION);
                        floatBallSwitch.setChecked(false);
                        return;
                    }
                    showFloatBall();
                } else {
                    hideFloatBall();
                }
            });
        }

        // 录音喊话开关
        if (realTimeVoiceSwitch != null) {
            realTimeVoiceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // 检查录音权限
                    if (ContextCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(requireActivity(),
                                new String[]{Manifest.permission.RECORD_AUDIO},
                                REQUEST_RECORD_AUDIO_PERMISSION);
                        realTimeVoiceSwitch.setChecked(false);
                        return;
                    }
                    startRealTimeVoice();
                } else {
                    stopRealTimeVoice();
                }
            });
        }
    }

    private void showFloatBall() {
        if (floatBallManager == null) return;
        floatBallManager.show();
        floatBallManager.setListener(new FloatBallManager.OnFloatBallListener() {
            @Override
            public void onSingleClick() {
                // 单击：开始/停止录音保存
                if (!isRecording) {
                    startRecording();
                } else {
                    stopRecording();
                }
            }

            @Override
            public void onDoubleClick() {
                // 双击：切换实时喊话模式
                if (realTimeVoiceSwitch != null) {
                    realTimeVoiceSwitch.setChecked(!realTimeVoiceSwitch.isChecked());
                }
            }

            @Override
            public void onLongPress() {
                // 长按：停止所有录音和喊话
                stopRecording();
                stopRealTimeVoice();
                Toast.makeText(requireContext(), R.string.all_stopped, Toast.LENGTH_SHORT).show();
            }
        });
        Toast.makeText(requireContext(), R.string.float_ball_shown, Toast.LENGTH_SHORT).show();
    }

    private void hideFloatBall() {
        if (floatBallManager != null) {
            floatBallManager.hide();
        }
        // 同时停止所有音频操作
        stopRecording();
        stopRealTimeVoice();
        if (realTimeVoiceSwitch != null) {
            realTimeVoiceSwitch.setChecked(false);
        }
        Toast.makeText(requireContext(), R.string.float_ball_hidden, Toast.LENGTH_SHORT).show();
    }

    private void startRecording() {
        Intent intent = new Intent(requireContext(), AudioRecordService.class);
        intent.setAction(AudioRecordService.ACTION_START_RECORDING);
        requireContext().startForegroundService(intent);
        isRecording = true;
        if (floatBallManager != null) {
            floatBallManager.setRecordingStatus(true);
        }
        Toast.makeText(requireContext(), R.string.recording, Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        Intent intent = new Intent(requireContext(), AudioRecordService.class);
        intent.setAction(AudioRecordService.ACTION_STOP_RECORDING);
        requireContext().startService(intent);
        isRecording = false;
        if (floatBallManager != null) {
            floatBallManager.setRecordingStatus(false);
        }
    }

    private void startRealTimeVoice() {
        Intent intent = new Intent(requireContext(), AudioRecordService.class);
        intent.setAction(AudioRecordService.ACTION_START_REAL_TIME);
        requireContext().startForegroundService(intent);
        if (floatBallManager != null) {
            floatBallManager.setRecordingStatus(true);
        }
        Toast.makeText(requireContext(), R.string.real_time_voice_on, Toast.LENGTH_SHORT).show();
    }

    private void stopRealTimeVoice() {
        Intent intent = new Intent(requireContext(), AudioRecordService.class);
        intent.setAction(AudioRecordService.ACTION_STOP_REAL_TIME);
        requireContext().startService(intent);
        if (floatBallManager != null) {
            floatBallManager.setRecordingStatus(false);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            // 从悬浮窗权限设置返回，重新检查权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    android.provider.Settings.canDrawOverlays(requireContext())) {
                // 权限已授予，尝试开启悬浮球
                if (floatBallSwitch != null) {
                    floatBallSwitch.setChecked(true);
                }
            } else {
                Toast.makeText(requireContext(), R.string.permission_overlay_needed, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 录音权限已授予，根据上下文判断是开启悬浮球还是开启录音
                if (floatBallSwitch != null && floatBallSwitch.isChecked()) {
                    // 已经在悬浮球开启流程中，由外层继续
                } else if (realTimeVoiceSwitch != null && !realTimeVoiceSwitch.isChecked()) {
                    // 来自录音喊话开关的请求，重新触发开启
                    realTimeVoiceSwitch.setChecked(true);
                }
                Toast.makeText(requireContext(), R.string.permission_record_granted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), R.string.permission_record_needed, Toast.LENGTH_LONG).show();
            }
        }
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

        // 通道Spinner - 使用全部可测试的音频通道配置
        ArrayAdapter<String> channelAdapter;
        if (config != null && config.fixedOutside) {
            // 固定车外：只显示"车外（固定）"
            channelAdapter = new ArrayAdapter<>(getContext(),
                    android.R.layout.simple_spinner_item,
                    new String[]{getString(R.string.channel_outside_fixed)});
            control.channelSpinner.setEnabled(false);
            control.channelSpinner.setAdapter(channelAdapter);
            control.channelSpinner.setSelection(0);
            // 固定车外不绑定选择事件，不返回
        } else {
            // 从 ALL_CHANNEL_PROFILES 提取所有显示名称
            AudioRouter.AudioChannelProfile[] allProfiles = AudioRouter.ALL_CHANNEL_PROFILES;
            String[] profileNames = new String[allProfiles.length];
            for (int i = 0; i < allProfiles.length; i++) {
                profileNames[i] = allProfiles[i].displayName;
            }

            channelAdapter = new ArrayAdapter<>(getContext(),
                    android.R.layout.simple_spinner_item, profileNames);
            channelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            control.channelSpinner.setAdapter(channelAdapter);

            // 根据当前配置设置初始选中位置
            if (config != null && config.audioProfile != null) {
                for (int i = 0; i < allProfiles.length; i++) {
                    if (allProfiles[i].displayName.equals(config.audioProfile.displayName)) {
                        control.channelSpinner.setSelection(i);
                        break;
                    }
                }
            } else if (config != null) {
                // 降级到旧版 mapping
                int defaultIdx = 0; // 车内(媒体)
                if (config.channel == AudioRouter.SpeakerChannel.OUTSIDE) {
                    defaultIdx = 1; // 车外(通话)
                } else if (config.channel == AudioRouter.SpeakerChannel.BOTH) {
                    defaultIdx = 7; // 双通道(媒体)
                }
                control.channelSpinner.setSelection(defaultIdx);
            }

            // 用户选择通道 → 自动保存配置并播放测试音
            control.channelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                private boolean isInitial = true;

                @Override
                public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                    if (control.updating) return;
                    if (isInitial) {
                        isInitial = false;
                        return; // 首次初始化不播放测试音
                    }
                    // 通过 profile 索引设置通道并自动播放测试音
                    audioRouter.setRouteWithProfile(source, position);
                }

                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

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

        Log.d("SettingsFragment", "车型切换至 [" + newModel.getDisplayName()
                + "]，已加载 " + ruleConfig.getSystemRules().size() + " 条联动规则");
    }
}
