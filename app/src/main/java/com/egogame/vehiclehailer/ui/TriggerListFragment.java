package com.egogame.vehiclehailer.ui;

import android.content.Context;
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
import com.egogame.vehiclehailer.model.VoiceItem;

import java.util.ArrayList;
import java.util.List;

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
            Toast.makeText(getContext(), R.string.trigger_add_feature_wip, Toast.LENGTH_SHORT).show();
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
            ruleAdapter = new RuleListAdapter(getLayoutInflater(), eventTrigger, currentRules);
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
        // 车型选择Spinner
        List<CarModel> models = configLoader.getCarModels();
        List<String> modelNames = new ArrayList<>();
        for (CarModel m : models) {
            modelNames.add(m.getId() + ". " + m.getModelName());
        }
        // 测试属性Spinner
        List<String> propertyNames = new ArrayList<>();
        propertyNames.add("doorLeft");
        propertyNames.add("doorRight");
        propertyNames.add("doorLeftRear");
        propertyNames.add("doorRightRear");
        propertyNames.add("trunk");
        propertyNames.add("lockStatus");

        ArrayAdapter<String> propertyAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, propertyNames);
        propertyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        testPropertySpinner.setAdapter(propertyAdapter);

        // 测试值Spinner
        List<String> testValues = new ArrayList<>();
        testValues.add("0→锁定");
        testValues.add("1→解锁");

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

        String propertyName = (String) testPropertySpinner.getSelectedItem();
        String newValue = (String) testValueSpinner.getSelectedItem();

        Toast.makeText(getContext(), R.string.trigger_test_playing, Toast.LENGTH_SHORT).show();

        // 模拟属性变化，测试联动
        if (propertyName != null && newValue != null) {
            String mappedValue = newValue.startsWith("1") ? "1" : "0";
            stateManager.setPropertyValue(propertyName, mappedValue);
        }
    }

    /**
     * 规则列表适配器（内部类）
     */
    private static class RuleListAdapter extends android.widget.BaseAdapter {
        private final LayoutInflater inflater;
        private final VehicleEventTrigger eventTrigger;
        private List<TriggerRule> rules;

        RuleListAdapter(LayoutInflater inflater, VehicleEventTrigger eventTrigger, List<TriggerRule> rules) {
            this.inflater = inflater;
            this.eventTrigger = eventTrigger;
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
            Context ctx = inflater.getContext();
            String condition = (rule.getOldValue() != null ? rule.getOldValue() : ctx.getString(R.string.trigger_any_value))
                    + " → " + rule.getNewValue();
            conditionText.setText(condition);
            String exteriorText = rule.isExterior()
                    ? ctx.getString(R.string.trigger_channel_exterior)
                    : ctx.getString(R.string.trigger_channel_interior);
            String delayText = rule.getDelayMs() > 0 ? " +" + rule.getDelayMs() + "ms" : "";
            voiceText.setText(ctx.getString(R.string.trigger_voice_info, rule.getVoiceId(), exteriorText, delayText));
            // 先清除监听器再设置checked，避免复用视图时触发回调
            enableSwitch.setOnCheckedChangeListener(null);
            enableSwitch.setChecked(rule.isEnabled());
            enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                eventTrigger.setRuleEnabled(rule.getVoiceId(), isChecked);
                // 刷新视图，确保开关状态与实际同步
                updateRules(eventTrigger.getRules());
            });

            return convertView;
        }
    }
}
