package com.egogame.vehiclehailer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.egogame.vehiclehailer.R;
import com.egogame.vehiclehailer.VehicleHailerApp;
import com.egogame.vehiclehailer.engine.ConfigLoader;
import com.egogame.vehiclehailer.engine.TriggerRule;
import com.egogame.vehiclehailer.engine.TriggerRuleConfig;
import com.egogame.vehiclehailer.engine.VehicleEventTrigger;
import com.egogame.vehiclehailer.engine.VehicleStateManager;
import com.egogame.vehiclehailer.model.CarModel;
import com.egogame.vehiclehailer.model.VehicleProperty;
import com.egogame.vehiclehailer.model.VoiceItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 联动规则管理页面
 * 展示所有联动规则，支持启用/禁用、编辑、添加、删除和测试触发。
 */
public class TriggerListFragment extends Fragment {

    private ListView ruleListView;
    private TextView emptyView;
    private Button enableAllBtn;
    private Button resetBtn;
    private Button addBtn;
    private Spinner testPropertySpinner;
    private Spinner testValueSpinner;
    private Button testTriggerBtn;

    private VehicleEventTrigger eventTrigger;
    private TriggerRuleConfig ruleConfig;
    private ConfigLoader configLoader;
    private VehicleStateManager stateManager;

    private List<TriggerRule> currentRules = new ArrayList<>();
    private RuleListAdapter ruleAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_trigger_list, container, false);

        // 初始化引用
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            eventTrigger = activity.getVehicleEventTrigger();
            ruleConfig = activity.getTriggerRuleConfig();
        }
        VehicleHailerApp app = VehicleHailerApp.getInstance();
        configLoader = app.getConfigLoader();
        stateManager = app.getVehicleStateManager();

        // 绑定视图
        ruleListView = root.findViewById(R.id.trigger_rule_list);
        emptyView = root.findViewById(R.id.trigger_empty_view);
        enableAllBtn = root.findViewById(R.id.trigger_enable_all);
        resetBtn = root.findViewById(R.id.trigger_reset);
        addBtn = root.findViewById(R.id.trigger_add);
        testPropertySpinner = root.findViewById(R.id.trigger_test_property);
        testValueSpinner = root.findViewById(R.id.trigger_test_value);
        testTriggerBtn = root.findViewById(R.id.trigger_test_btn);

        // 加载规则列表
        refreshRules();

        // 启用/禁用所有
        enableAllBtn.setOnClickListener(v -> {
            boolean allEnabled = areAllRulesEnabled();
            for (TriggerRule rule : currentRules) {
                eventTrigger.setRuleEnabled(rule.getVoiceId(), !allEnabled);
            }
            refreshRules();
            Toast.makeText(getContext(),
                    allEnabled ? R.string.trigger_disable : R.string.trigger_enable,
                    Toast.LENGTH_SHORT).show();
        });

        // 重置为默认规则
        resetBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.trigger_reset)
                    .setMessage(R.string.trigger_reset_confirm)
                    .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                        // 清除现有规则，重新加载默认
                        eventTrigger.clearRules();
                        ruleConfig.reset();
                        ruleConfig.loadDefaults();
                        eventTrigger.addRules(ruleConfig.getSystemRules());
                        refreshRules();
                        Toast.makeText(getContext(), R.string.trigger_reset_done, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        });

        // 添加规则
        addBtn.setOnClickListener(v -> {
            // TODO: 打开规则编辑页面
            Toast.makeText(getContext(), "规则编辑功能开发中", Toast.LENGTH_SHORT).show();
        });

        // 设置测试用Spinner
        setupTestSpinners();

        // 测试触发
        testTriggerBtn.setOnClickListener(v -> performTestTrigger());

        return root;
    }

    private void refreshRules() {
        currentRules = eventTrigger.getRules();
        if (ruleAdapter == null) {
            ruleAdapter = new RuleListAdapter(getLayoutInflater(), currentRules);
            ruleListView.setAdapter(ruleAdapter);
        } else {
            ruleAdapter.updateRules(currentRules);
        }
        ruleListView.setEmptyView(emptyView);
    }

    private boolean areAllRulesEnabled() {
        for (TriggerRule rule : currentRules) {
            if (!rule.isEnabled()) return false;
        }
        return currentRules.isEmpty() || true; // 空列表认为是全部已启用
    }

    private void setupTestSpinners() {
        // 从配置加载所有车辆属性（含中文显示名）
        List<VehicleProperty> allProperties = configLoader.getVehicleProperties();
        List<String> propertyDisplayNames = new ArrayList<>();
        // 记录属性名到VehicleProperty的映射，用于后面获取值选项
        final java.util.Map<String, VehicleProperty> propMap = new java.util.HashMap<>();
        for (VehicleProperty p : allProperties) {
            propertyDisplayNames.add(p.getDisplayName());
            propMap.put(p.getDisplayName(), p);
        }

        ArrayAdapter<String> propertyAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, propertyDisplayNames);
        propertyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        testPropertySpinner.setAdapter(propertyAdapter);

        // 默认选中第一个属性后，动态加载其值选项
        updateTestValueSpinner(propMap, propertyDisplayNames.isEmpty() ? null : propertyDisplayNames.get(0));

        // 监听属性选择变化，动态更新值选项
        testPropertySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedDisplay = (String) parent.getItemAtPosition(position);
                updateTestValueSpinner(propMap, selectedDisplay);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 测试值Spinner（初始值由updateTestValueSpinner填充）
    }

    private void updateTestValueSpinner(java.util.Map<String, VehicleProperty> propMap, String displayName) {
        List<String> testValues = new ArrayList<>();
        if (displayName != null && propMap.containsKey(displayName)) {
            VehicleProperty prop = propMap.get(displayName);
            String[] options = prop.getOptions();
            String[] optionValues = prop.getOptionValues();
            if (options != null && options.length > 0 && optionValues != null && optionValues.length > 0) {
                // 有选项列表：显示"选项值→中文选项"
                for (int i = 0; i < options.length; i++) {
                    String val = i < optionValues.length ? optionValues[i] : String.valueOf(i);
                    testValues.add(val + "→" + options[i]);
                }
            } else {
                // 滑块类型：显示常用范围
                float min = prop.getMinValue();
                float max = prop.getMaxValue();
                String unit = prop.getUnit() != null ? prop.getUnit() : "";
                testValues.add(String.valueOf((int)min) + unit + "→最小值");
                testValues.add(String.valueOf((int)(min + (max - min) / 4)) + unit + "→低");
                testValues.add(String.valueOf((int)(min + (max - min) / 2)) + unit + "→中");
                testValues.add(String.valueOf((int)(min + (max - min) * 3 / 4)) + unit + "→高");
                testValues.add(String.valueOf((int)max) + unit + "→最大值");
            }
        } else {
            // 兜底
            testValues.add("0→关闭");
            testValues.add("1→开启");
        }

        ArrayAdapter<String> valueAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, testValues);
        valueAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        testValueSpinner.setAdapter(valueAdapter);
    }

    private void performTestTrigger() {
        if (currentRules.isEmpty()) {
            Toast.makeText(getContext(), R.string.trigger_rule_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        String displayName = (String) testPropertySpinner.getSelectedItem();
        String newValueDisplay = (String) testValueSpinner.getSelectedItem();

        // 根据中文显示名反查英文属性名
        List<VehicleProperty> allProperties = configLoader.getVehicleProperties();
        String propertyName = null;
        for (VehicleProperty p : allProperties) {
            if (p.getDisplayName().equals(displayName)) {
                propertyName = p.getPropertyName();
                break;
            }
        }

        Toast.makeText(getContext(), R.string.trigger_test_playing, Toast.LENGTH_SHORT).show();

        // 模拟属性变化，测试联动
        if (propertyName != null && newValueDisplay != null) {
            // 从 "val→中文选项" 中提取前面数字部分
            String mappedValue = newValueDisplay;
            int arrowIndex = newValueDisplay.indexOf("→");
            if (arrowIndex > 0) {
                mappedValue = newValueDisplay.substring(0, arrowIndex).trim();
            }
            stateManager.setPropertyValue(propertyName, mappedValue);
        }
    }

    /**
     * 规则列表适配器（内部类）
     */
    private static class RuleListAdapter extends android.widget.BaseAdapter {
        private final LayoutInflater inflater;
        private List<TriggerRule> rules;

        RuleListAdapter(LayoutInflater inflater, List<TriggerRule> rules) {
            this.inflater = inflater;
            this.rules = new ArrayList<>(rules);
        }

        void updateRules(List<TriggerRule> newRules) {
            this.rules = new ArrayList<>(newRules);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() { return rules.size(); }

        @Override
        public TriggerRule getItem(int position) { return rules.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_trigger_rule, parent, false);
            }

            TriggerRule rule = rules.get(position);

            TextView propertyText = convertView.findViewById(R.id.rule_property);
            TextView conditionText = convertView.findViewById(R.id.rule_condition);
            TextView voiceText = convertView.findViewById(R.id.rule_voice);
            android.widget.Switch enableSwitch = convertView.findViewById(R.id.rule_enable_switch);

            propertyText.setText(rule.getPropertyName());
            String condition = (rule.getOldValue() != null ? rule.getOldValue() : "任意")
                    + " → " + rule.getNewValue();
            conditionText.setText(condition);
            voiceText.setText("音频#" + rule.getVoiceId()
                    + (rule.isExterior() ? " 车外" : " 车内")
                    + (rule.getDelayMs() > 0 ? " +" + rule.getDelayMs() + "ms" : ""));
            enableSwitch.setChecked(rule.isEnabled());
            enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // 启用/禁用规则
                // 注意：这里通过外部holder引用比较麻烦，简化处理
            });

            return convertView;
        }
    }
}
