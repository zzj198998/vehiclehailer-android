package com.egogame.vehiclehailer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.egogame.vehiclehailer.R;
import com.egogame.vehiclehailer.VehicleHailerApp;
import com.egogame.vehiclehailer.engine.VehicleStateManager;
import com.egogame.vehiclehailer.model.Catalog;
import com.egogame.vehiclehailer.model.PropertyReg;
import com.egogame.vehiclehailer.model.VehicleProperty;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MonitorFragment extends Fragment {

    private RecyclerView propertyRecycler;
    private PropertyAdapter propertyAdapter;
    private ChipGroup catalogChipGroup;
    private TextView connectionStatus;
    private TextView updateTime;
    private View connectionIndicator;

    private VehicleStateManager stateManager;
    private List<VehicleProperty> allProperties;
    private List<VehicleProperty> filteredProperties;
    private List<Catalog> catalogs;
    private int currentCatalogIndex = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_monitor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        connectionIndicator = view.findViewById(R.id.connection_indicator);
        connectionStatus = view.findViewById(R.id.connection_status);
        updateTime = view.findViewById(R.id.update_time);
        catalogChipGroup = view.findViewById(R.id.catalog_chip_group);
        propertyRecycler = view.findViewById(R.id.property_recycler);

        stateManager = VehicleHailerApp.getInstance().getVehicleStateManager();

        // 获取当前车型属性
        List<PropertyReg> currentRegs = VehicleHailerApp.getInstance().getConfigLoader().getCurrentPropertyRegs();
        allProperties = new ArrayList<>();
        for (PropertyReg reg : currentRegs) {
            VehicleProperty vp = VehicleHailerApp.getInstance().getConfigLoader()
                    .getVehicleProperty(reg.getPropertyName());
            if (vp != null) {
                allProperties.add(vp);
            }
        }

        filteredProperties = new ArrayList<>(allProperties);

        // 获取目录分类
        catalogs = VehicleHailerApp.getInstance().getConfigLoader().getCatalogs();

        // 动态添加分类Chip
        catalogChipGroup.removeAllViews();
        Chip allChip = new Chip(getContext());
        allChip.setText("全部");
        allChip.setChecked(true);
        allChip.setClickable(true);
        allChip.setCheckable(true);
        allChip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentCatalogIndex = 0;
                filterByCatalog(0);
            }
        });
        catalogChipGroup.addView(allChip);

        for (int i = 0; i < catalogs.size(); i++) {
            Catalog cat = catalogs.get(i);
            Chip chip = new Chip(getContext());
            chip.setText(cat.getName());
            chip.setClickable(true);
            chip.setCheckable(true);
            final int index = i + 1; // +1因为0是"全部"
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentCatalogIndex = index;
                    filterByCatalog(index);
                }
            });
            catalogChipGroup.addView(chip);
        }

        // 设置RecyclerView
        propertyRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        propertyAdapter = new PropertyAdapter(filteredProperties, stateManager);
        propertyRecycler.setAdapter(propertyAdapter);

        updateConnectionStatus(false);
    }

    private void filterByCatalog(int catalogIndex) {
        filteredProperties.clear();
        if (catalogIndex == 0) {
            filteredProperties.addAll(allProperties);
        } else {
            Catalog selectedCatalog = catalogs.get(catalogIndex - 1);
            for (VehicleProperty vp : allProperties) {
                if (vp.getCatalogId() == selectedCatalog.getId()) {
                    filteredProperties.add(vp);
                }
            }
        }
        propertyAdapter.notifyDataSetChanged();
    }

    public void onVehicleStateChanged() {
        if (propertyAdapter != null) {
            propertyAdapter.notifyDataSetChanged();
        }
        updateConnectionStatus(true);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        updateTime.setText("更新: " + sdf.format(new Date()));
    }

    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            connectionStatus.setText(R.string.vehicle_connected);
            connectionStatus.setTextColor(getResources().getColor(R.color.success, null));
            connectionIndicator.setBackgroundResource(R.drawable.circle_indicator_green);
        } else {
            connectionStatus.setText(R.string.vehicle_disconnected);
            connectionStatus.setTextColor(getResources().getColor(R.color.gray_400, null));
            connectionIndicator.setBackgroundResource(R.drawable.circle_indicator_red);
        }
    }
}
