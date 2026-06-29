package com.aug32.l7audio.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.aug32.l7audio.R;
import com.aug32.l7audio.domain.audio.MusicItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 音乐播放列表适配器
 *
 * 职责：
 * - 渲染音乐列表
 * - 处理点击事件（播放、删除）
 * - 支持多选删除模式
 */
public class MusicPlaylistAdapter extends RecyclerView.Adapter<MusicPlaylistAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
        void onItemLongClick(int position);
    }

    private List<MusicItem> musicItems;
    private boolean isSelectionMode = false;
    private final List<Integer> selectedPositions = new ArrayList<>();
    private int currentPlayingIndex = -1;
    private OnItemClickListener listener;

    public MusicPlaylistAdapter() {
        this.musicItems = new ArrayList<>();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setMusicItems(List<MusicItem> items) {
        if (items == null) {
            this.musicItems = new ArrayList<>();
        } else {
            this.musicItems = new ArrayList<>(items);
        }
        notifyDataSetChanged();
    }

    public void addMusicItemsRange(List<MusicItem> newItems, int startPosition) {
        if (newItems == null || newItems.isEmpty()) {
            return;
        }
        int insertPos = Math.min(startPosition, this.musicItems.size());
        this.musicItems.addAll(insertPos, newItems);
        notifyItemRangeInserted(insertPos, newItems.size());
    }

    public void removeMusicItems(List<Integer> positions) {
        if (positions == null || positions.isEmpty()) return;

        List<Integer> sorted = new ArrayList<>(positions);
        Collections.sort(sorted, (a, b) -> b - a);

        for (int pos : sorted) {
            if (pos >= 0 && pos < musicItems.size()) {
                musicItems.remove(pos);
            }
        }
        notifyDataSetChanged();
    }

    public void setCurrentPlayingIndex(int index) {
        if (this.currentPlayingIndex == index) return;
        int oldIndex = this.currentPlayingIndex;
        this.currentPlayingIndex = index;
        if (oldIndex >= 0) {
            notifyItemChanged(oldIndex);
        }
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }

    // ========== 选择模式 ==========

    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) {
            selectedPositions.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(Integer.valueOf(position));
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
    }

    public List<Integer> getSelectedPositions() {
        return new ArrayList<>(selectedPositions);
    }

    public int getSelectedCount() {
        return selectedPositions.size();
    }

    public void selectAll() {
        selectedPositions.clear();
        for (int i = 0; i < musicItems.size(); i++) {
            selectedPositions.add(i);
        }
        notifyDataSetChanged();
    }

    private void setOddEvenBackground(ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        if (position % 2 == 0) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.item_background_even));
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.item_background_odd));
        }
    }

    // ========== RecyclerView.Adapter ==========

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_music_playlist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MusicItem item = musicItems.get(position);
        Context context = holder.itemView.getContext();

        holder.titleTextView.setText(item.title);
        holder.artistTextView.setText(item.artist);

        if (isSelectionMode) {
            holder.playingIndicator.setVisibility(View.GONE);
            if (selectedPositions.contains(position)) {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.colorAccent));
                holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                holder.artistTextView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            } else {
                setOddEvenBackground(holder, position);
            }
        } else if (position == currentPlayingIndex) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.colorAccent));
            holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            holder.artistTextView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            holder.playingIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.playingIndicator.setVisibility(View.GONE);
            setOddEvenBackground(holder, position);
        }

        // 选择模式
        if (isSelectionMode) {
            holder.deleteButton.setVisibility(View.GONE);
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setSelected(selectedPositions.contains(position));
        } else {
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.checkBox.setVisibility(View.GONE);
        }

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(position);
            } else if (listener != null) {
                listener.onItemClick(position);
            }
        });

        // 长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(position);
            }
            return true;
        });

        // 删除按钮
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null && !isSelectionMode) {
                listener.onItemLongClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return musicItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView artistTextView;
        ImageView playingIndicator;
        ImageView checkBox;
        ImageButton deleteButton;

        ViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.tv_music_title);
            artistTextView = itemView.findViewById(R.id.tv_music_artist);
            playingIndicator = itemView.findViewById(R.id.iv_playing_indicator);
            checkBox = itemView.findViewById(R.id.iv_checkbox);
            deleteButton = itemView.findViewById(R.id.btn_delete);
        }
    }
}
