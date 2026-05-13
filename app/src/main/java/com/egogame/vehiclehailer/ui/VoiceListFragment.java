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
    private int selectedTabPosition = 0;

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

        // Chip点击切换过滤
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int position = 0;
            if (!checkedIds.isEmpty()) {
                int checkedId = checkedIds.get(0);
                position = group.indexOfChild(group.findViewById(checkedId));
            }
            selectedTabPosition = position;
            filterVoiceItems(position, searchInput.getText().toString());
        });

        // 搜索过滤
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterVoiceItems(selectedTabPosition, s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterVoiceItems(int tabPosition, String query) {
        filteredVoiceItems.clear();
        for (VoiceItem item : allVoiceItems) {
            // Tab过滤：0=全部，1=系统，2=阻塞
            if (tabPosition == 1 && item.getTab() != VoiceTab.SYSTEM) continue;
            if (tabPosition == 2 && item.getTab() != VoiceTab.BLOCK) continue;
            // 搜索过滤
            if (!query.isEmpty() && !item.getName().toLowerCase().contains(query.toLowerCase())) continue;
            filteredVoiceItems.add(item);
        }
        voiceAdapter.notifyDataSetChanged();
    }
}
