package com.egogame.vehiclehailer.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.egogame.vehiclehailer.R;
import com.egogame.vehiclehailer.adapter.AudioCardAdapter;
import com.egogame.vehiclehailer.audio.AudioLibraryManager;
import com.egogame.vehiclehailer.audio.AudioRouter;

import java.util.List;

public class AudioLibraryFragment extends Fragment {

    private AudioLibraryManager libraryManager;
    private AudioRouter audioRouter;
    private AudioLibraryManager.AudioCategory currentCategory = AudioLibraryManager.AudioCategory.HAILING;

    private RecyclerView recyclerView;
    private AudioCardAdapter adapter;
    private TextView emptyHint;
    private Button btnUpload;
    private View[] tabViews;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_audio_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        libraryManager = new AudioLibraryManager(requireContext());
        audioRouter = ((MainActivity) requireActivity()).getAudioRouter();

        recyclerView = view.findViewById(R.id.audio_recycler);
        emptyHint = view.findViewById(R.id.audio_empty_hint);
        btnUpload = view.findViewById(R.id.btn_upload_audio);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 分类Tab
        tabViews = new View[]{
                view.findViewById(R.id.tab_hailing),
                view.findViewById(R.id.tab_door),
                view.findViewById(R.id.tab_lock),
                view.findViewById(R.id.tab_custom),
                view.findViewById(R.id.tab_soundwave)
        };

        int[] categories = {0, 1, 2, 3, 4};
        for (int i = 0; i < tabViews.length; i++) {
            final int index = i;
            tabViews[i].setOnClickListener(v -> switchCategory(index));
        }

        btnUpload.setOnClickListener(v -> {
            filePickerLauncher.launch(new String[]{"audio/*", "application/ogg"});
        });

        switchCategory(0); // 默认显示车外喊话
    }

    private void switchCategory(int index) {
        for (int i = 0; i < tabViews.length; i++) {
            tabViews[i].setSelected(i == index);
            tabViews[i].setAlpha(i == index ? 1.0f : 0.5f);
        }

        currentCategory = AudioLibraryManager.AudioCategory.values()[index];
        refreshList();
    }

    private void refreshList() {
        List<AudioLibraryManager.AudioItem> items = libraryManager.getAudioList(currentCategory);
        if (items.isEmpty()) {
            emptyHint.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyHint.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter = new AudioCardAdapter(requireContext(), currentCategory, items,
                    libraryManager, audioRouter, this::refreshList);
            recyclerView.setAdapter(adapter);
        }
    }

    private void onFilePicked(Uri uri) {
        if (uri == null) return;

        // 获取文件名
        String displayName = getString(R.string.audio_unknown_name);
        try (android.database.Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {}

        AudioLibraryManager.AudioItem item = libraryManager.addAudio(currentCategory, uri, displayName);
        if (item != null) {
            Toast.makeText(requireContext(), getString(R.string.audio_add_success, item.name), Toast.LENGTH_SHORT).show();
            refreshList();
        } else {
            Toast.makeText(requireContext(), getString(R.string.audio_add_fail), Toast.LENGTH_SHORT).show();
        }
    }
}