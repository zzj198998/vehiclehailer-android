package com.egogame.vehiclehailer.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.egogame.vehiclehailer.R;
import com.egogame.vehiclehailer.engine.VoicePlayer;
import com.egogame.vehiclehailer.model.VoiceItem;

import java.util.List;

public class VoiceAdapter extends RecyclerView.Adapter<VoiceAdapter.VoiceViewHolder> {

    private final List<VoiceItem> voiceItems;
    private final VoicePlayer voicePlayer;

    public VoiceAdapter(List<VoiceItem> voiceItems, VoicePlayer voicePlayer) {
        this.voiceItems = voiceItems;
        this.voicePlayer = voicePlayer;
    }

    @NonNull
    @Override
    public VoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_voice, parent, false);
        return new VoiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VoiceViewHolder holder, int position) {
        VoiceItem item = voiceItems.get(position);
        holder.idText.setText(String.valueOf(item.getId()));
        holder.nameText.setText(item.getName());
        holder.pathText.setText(item.getFilePath());

        holder.playButton.setOnClickListener(v -> {
            voicePlayer.play(item.getFilePath());
            // 切换播放/暂停按钮状态
            holder.playButton.setVisibility(View.GONE);
            holder.stopButton.setVisibility(View.VISIBLE);
        });

        holder.stopButton.setOnClickListener(v -> {
            voicePlayer.stop();
            holder.playButton.setVisibility(View.VISIBLE);
            holder.stopButton.setVisibility(View.GONE);
        });

        // 预加载声音
        voicePlayer.preloadVoice(item.getFilePath());
    }

    @Override
    public int getItemCount() {
        return voiceItems.size();
    }

    static class VoiceViewHolder extends RecyclerView.ViewHolder {
        TextView idText;
        TextView nameText;
        TextView pathText;
        ImageButton playButton;
        ImageButton stopButton;

        VoiceViewHolder(@NonNull View itemView) {
            super(itemView);
            idText = itemView.findViewById(R.id.voice_id);
            nameText = itemView.findViewById(R.id.voice_name);
            pathText = itemView.findViewById(R.id.voice_path);
            playButton = itemView.findViewById(R.id.btn_play);
            stopButton = itemView.findViewById(R.id.btn_stop);
        }
    }
}
