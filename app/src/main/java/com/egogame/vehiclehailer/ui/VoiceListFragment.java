package com.egogame.vehiclehailer.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.egogame.vehiclehailer.R;
import com.egogame.vehiclehailer.VehicleHailerApp;
import com.egogame.vehiclehailer.engine.VoicePlayer;
import com.egogame.vehiclehailer.model.VoiceItem;
import com.egogame.vehiclehailer.model.VoiceItem.VoiceTab;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class VoiceListFragment extends Fragment {

    private RecyclerView voiceRecycler;
    private VoiceAdapter voiceAdapter;
    private List<VoiceItem> allVoiceItems;
    private List<VoiceItem> filteredVoiceItems;
    private ChipGroup chipGroup;
    private EditText searchInput;
    private VoicePlayer voicePlayer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_voice_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        voicePlayer = VehicleHailerApp.getInstance().getVoicePlayer();
        allVoiceItems = VehicleHailerApp.getInstance().getConfigLoader().getVoiceItems();
        if (allVoiceItems == null) {
            allVoiceItems = new ArrayList<>();
        }

        chipGroup = view.findViewById(R.id.tab_layout);
        searchInput = view.findViewById(R.id.search_input);
        voiceRecycler = view.findViewById(R.id.voice_recycler);

        voiceRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        filteredVoiceItems = new ArrayList<>(allVoiceItems);
        voiceAdapter = new VoiceAdapter(filteredVoiceItems, voicePlayer);
        voiceRecycler.setAdapter(voiceAdapter);

        // Chip选中切换过滤（参考图：自定义音效/系统音效/全部）
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int position = 0;
            if (checkedIds != null && !checkedIds.isEmpty()) {
                int id = checkedIds.get(0);
                if (id == R.id.chip_custom) position = 1;
                else if (id == R.id.chip_system) position = 2;
            }
            filterVoiceItems(position, searchInput.getText().toString());
        });

        // 默认选中"自定义音效"
        chipGroup.check(R.id.chip_custom);

        // 搜索过滤
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int position = 0;
                int checkedId = chipGroup.getCheckedChipId();
                if (checkedId == R.id.chip_custom) position = 1;
                else if (checkedId == R.id.chip_system) position = 2;
                filterVoiceItems(position, s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterVoiceItems(int chipPosition, String query) {
        filteredVoiceItems.clear();
        for (VoiceItem item : allVoiceItems) {
            // chipPosition: 0=全部, 1=自定义音效(CUSTOM), 2=系统音效(SYSTEM)
            if (chipPosition == 1 && item.getTab() != VoiceTab.CUSTOM) continue;
            if (chipPosition == 2 && item.getTab() != VoiceTab.SYSTEM) continue;
            if (!query.isEmpty() && !item.getName().toLowerCase().contains(query.toLowerCase())) continue;
            filteredVoiceItems.add(item);
        }
        voiceAdapter.notifyDataSetChanged();
    }
}
