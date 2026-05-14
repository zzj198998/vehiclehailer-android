package com.egogame.vehiclehailer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.egogame.vehiclehailer.R;
import com.egogame.vehiclehailer.audio.AudioLibraryManager;
import com.egogame.vehiclehailer.audio.AudioRouter;

import java.io.File;
import java.util.List;

public class AudioCardAdapter extends RecyclerView.Adapter<AudioCardAdapter.ViewHolder> {

    private final AudioLibraryManager.AudioCategory category;
    private List<AudioLibraryManager.AudioItem> items;
    private final AudioLibraryManager libraryManager;
    private final AudioRouter audioRouter;
    private final Runnable onRefresh;
    private int playingPosition = -1;

    public AudioCardAdapter(android.content.Context context,
                            AudioLibraryManager.AudioCategory category,
                            List<AudioLibraryManager.AudioItem> items,
                            AudioLibraryManager libraryManager,
                            AudioRouter audioRouter,
                            Runnable onRefresh) {
        this.category = category;
        this.items = items;
        this.libraryManager = libraryManager;
        this.audioRouter = audioRouter;
        this.onRefresh = onRefresh;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.audio_card_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AudioLibraryManager.AudioItem item = items.get(position);
        holder.nameText.setText(item.name);

        // 播放状态指示
        boolean isPlaying = playingPosition == position;
        holder.playBtn.setImageResource(isPlaying
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);

        // 播放/暂停
        holder.playBtn.setOnClickListener(v -> {
            if (isPlaying) {
                audioRouter.stop(AudioRouter.SoundSource.values()[category.ordinal()]);
                playingPosition = -1;
                notifyDataSetChanged();
            } else {
                audioRouter.stopAll();
                File file = new File(item.filePath);
                if (file.exists()) {
                    AudioRouter.SoundSource soundSource = AudioRouter.SoundSource.values()[category.ordinal()];
                    audioRouter.playFile(soundSource, item.filePath);
                    playingPosition = position;
                    notifyDataSetChanged();
                } else {
                    Toast.makeText(v.getContext(), "文件不存在", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 重命名
        holder.renameBtn.setOnClickListener(v -> showRenameDialog(holder.itemView.getContext(), item));

        // 删除
        holder.deleteBtn.setOnClickListener(v -> showDeleteDialog(holder.itemView.getContext(), item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void showRenameDialog(android.content.Context context, AudioLibraryManager.AudioItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("重命名");

        android.widget.EditText input = new android.widget.EditText(context);
        input.setText(item.name);
        input.setSelection(item.name.length());
        builder.setView(input);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                libraryManager.renameAudio(category, item.id, newName);
                onRefresh.run();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showDeleteDialog(android.content.Context context, AudioLibraryManager.AudioItem item) {
        new AlertDialog.Builder(context)
                .setTitle("删除音频")
                .setMessage("确定删除 \"" + item.name + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    libraryManager.deleteAudio(category, item.id);
                    onRefresh.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        ImageButton playBtn;
        TextView nameText;
        ImageButton renameBtn;
        ImageButton deleteBtn;

        ViewHolder(View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.audio_card);
            playBtn = itemView.findViewById(R.id.btn_play_audio);
            nameText = itemView.findViewById(R.id.audio_name);
            renameBtn = itemView.findViewById(R.id.btn_rename);
            deleteBtn = itemView.findViewById(R.id.btn_delete);
        }
    }
}