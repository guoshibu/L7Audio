package com.aug32.l7audio;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.aug32.l7audio.audio.MusicPlayerManager;

import java.util.ArrayList;
import java.util.List;

public class MusicPlaylistAdapter extends RecyclerView.Adapter<MusicPlaylistAdapter.ViewHolder> {

    private List<MusicPlayerManager.MusicItem> musicItems;
    private List<Integer> selectedItems;
    private int currentPlayingIndex = -1;
    private boolean isSelectionMode = false;

    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private OnSelectionChangedListener onSelectionChangedListener;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(int position);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSongTitle;
        TextView tvSongArtist;
        View itemView;

        public ViewHolder(View view) {
            super(view);
            itemView = view;
            tvSongTitle = view.findViewById(R.id.tv_song_title_item);
            tvSongArtist = view.findViewById(R.id.tv_song_artist_item);
        }
    }

    public MusicPlaylistAdapter() {
        this.musicItems = new ArrayList<>();
        this.selectedItems = new ArrayList<>();
    }

    public void setMusicItems(List<MusicPlayerManager.MusicItem> musicItems) {
        this.musicItems = musicItems;
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClickListener = listener;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.onSelectionChangedListener = listener;
    }

    public void setCurrentPlayingIndex(int index) {
        int oldIndex = this.currentPlayingIndex;
        this.currentPlayingIndex = index;
        if (oldIndex >= 0) {
            notifyItemChanged(oldIndex);
        }
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.isSelectionMode = selectionMode;
        if (!selectionMode) {
            selectedItems.clear();
        }
        notifyDataSetChanged();
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.onSelectionChanged(selectedItems.size());
        }
    }

    public void toggleSelection(int position) {
        if (selectedItems.contains(position)) {
            selectedItems.remove(Integer.valueOf(position));
        } else {
            selectedItems.add(position);
        }
        notifyItemChanged(position);
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.onSelectionChanged(selectedItems.size());
        }
    }

    public List<Integer> getSelectedItems() {
        return new ArrayList<>(selectedItems);
    }

    public void clearSelection() {
        selectedItems.clear();
        notifyDataSetChanged();
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.onSelectionChanged(0);
        }
    }

    public void selectAll() {
        selectedItems.clear();
        for (int i = 0; i < musicItems.size(); i++) {
            selectedItems.add(i);
        }
        notifyDataSetChanged();
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.onSelectionChanged(selectedItems.size());
        }
    }

    public boolean isAllSelected() {
        return selectedItems.size() == musicItems.size() && !musicItems.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_music, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MusicPlayerManager.MusicItem item = musicItems.get(position);

        holder.tvSongTitle.setText(item.title);
        holder.tvSongArtist.setText(item.artist);

        if (isSelectionMode) {
            if (selectedItems.contains(position)) {
                holder.itemView.setBackgroundColor(
                        ContextCompat.getColor(holder.itemView.getContext(), R.color.button_background_selected)
                );
            } else {
                if (position % 2 == 0) {
                    holder.itemView.setBackgroundColor(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.item_background_even)
                    );
                } else {
                    holder.itemView.setBackgroundColor(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.item_background_odd)
                    );
                }
            }
        } else if (position == currentPlayingIndex) {
            holder.itemView.setBackgroundColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.status_recording)
            );
        } else {
            if (position % 2 == 0) {
                holder.itemView.setBackgroundColor(
                        ContextCompat.getColor(holder.itemView.getContext(), R.color.item_background_even)
                );
            } else {
                holder.itemView.setBackgroundColor(
                        ContextCompat.getColor(holder.itemView.getContext(), R.color.item_background_odd)
                );
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(position);
            } else if (onItemClickListener != null) {
                onItemClickListener.onItemClick(position);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (onItemLongClickListener != null) {
                onItemLongClickListener.onItemLongClick(position);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return musicItems.size();
    }
}
