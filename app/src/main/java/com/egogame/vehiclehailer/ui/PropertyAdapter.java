package com.egogame.vehiclehailer.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.egogame.vehiclehailer.R;
import com.egogame.vehiclehailer.engine.VehicleStateManager;
import com.egogame.vehiclehailer.model.VehicleProperty;
import com.egogame.vehiclehailer.model.VehicleProperty.ControlType;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class PropertyAdapter extends RecyclerView.Adapter<PropertyAdapter.PropertyViewHolder> {

    private final List<VehicleProperty> properties;
    private final VehicleStateManager stateManager;

    public PropertyAdapter(List<VehicleProperty> properties, VehicleStateManager stateManager) {
        this.properties = properties;
        this.stateManager = stateManager;
    }

    @NonNull
    @Override
    public PropertyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_property, parent, false);
        return new PropertyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PropertyViewHolder holder, int position) {
        VehicleProperty prop = properties.get(position);
        holder.nameText.setText(prop.getDisplayName());

        // 获取当前值
        String currentValue = stateManager.getPropertyValue(prop.getName());
        holder.valueText.setText(currentValue != null ? currentValue : "—");

        // 根据控件类型显示不同UI
        if (prop.getControlType() == ControlType.BUTTON_GROUP) {
            holder.chipGroup.setVisibility(View.VISIBLE);
            holder.sliderLayout.setVisibility(View.GONE);
            setupChipGroup(holder.chipGroup, prop, currentValue);
        } else if (prop.getControlType() == ControlType.SLIDER) {
            holder.chipGroup.setVisibility(View.GONE);
            holder.sliderLayout.setVisibility(View.VISIBLE);
            setupSlider(holder.sliderBar, holder.sliderValue, prop, currentValue);
        } else {
            holder.chipGroup.setVisibility(View.GONE);
            holder.sliderLayout.setVisibility(View.GONE);
        }
    }

    private void setupChipGroup(ChipGroup chipGroup, VehicleProperty prop, String currentValue) {
        chipGroup.removeAllViews();
        String[] options = prop.getOptions();
        if (options != null) {
            for (String option : options) {
                Chip chip = new Chip(chipGroup.getContext());
                chip.setText(option.trim());
                chip.setClickable(true);
                chip.setCheckable(true);
                // 如果当前值与选项匹配，选中
                if (currentValue != null && currentValue.equals(option.trim())) {
                    chip.setChecked(true);
                }
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        stateManager.setPropertyValue(prop.getName(), option.trim());
                    }
                });
                chipGroup.addView(chip);
            }
        }
    }

    private void setupSlider(SeekBar seekBar, TextView valueText, VehicleProperty prop, String currentValue) {
        seekBar.setMax(100);
        try {
            int progress = currentValue != null ? Integer.parseInt(currentValue) : 0;
            seekBar.setProgress(Math.min(progress, 100));
        } catch (NumberFormatException e) {
            seekBar.setProgress(0);
        }
        valueText.setText(currentValue != null ? currentValue : "0");
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    valueText.setText(String.valueOf(progress));
                    stateManager.setPropertyValue(prop.getName(), String.valueOf(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    @Override
    public int getItemCount() {
        return properties.size();
    }

    static class PropertyViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView valueText;
        ChipGroup chipGroup;
        View sliderLayout;
        SeekBar sliderBar;
        TextView sliderValue;

        PropertyViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.property_name);
            valueText = itemView.findViewById(R.id.property_value);
            chipGroup = itemView.findViewById(R.id.chip_group);
            sliderLayout = itemView.findViewById(R.id.slider_layout);
            sliderBar = itemView.findViewById(R.id.property_seekbar);
            sliderValue = itemView.findViewById(R.id.slider_value);
        }
    }
}
