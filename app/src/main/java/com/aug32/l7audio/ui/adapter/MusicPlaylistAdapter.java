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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aug32.l7audio.domain.audio.MusicItem;
import com.aug32.l7audio.R;

/**
 * 音乐播放列表适配器
 *
 * 职责：
 * - 渲染音乐列表项，支持奇偶行交替背景
 * - 处理列表项点击、长按、删除等交互事件
 * - 支持多选删除模式，可批量选中并删除
 * - 高亮当前播放的音乐项
 *
 * 设计模式：使用 ViewHolder 模式优化 RecyclerView 性能
 */
public class MusicPlaylistAdapter extends RecyclerView.Adapter<MusicPlaylistAdapter.ViewHolder> {

    /**
     * 列表项点击事件监听器接口
     * 用于将点击/长按事件回调给外部处理
     */
    public interface OnItemClickListener {
        /**
         * 列表项单击回调
         * @param position 点击的位置索引
         */
        void onItemClick(int position);

        /**
         * 列表项长按回调（也用于删除按钮点击）
         * @param position 长按的位置索引
         */
        void onItemLongClick(int position);
    }

    /** 音乐数据列表 */
    private List<MusicItem> musicItems;
    /** 是否处于多选模式 */
    private boolean isSelectionMode = false;
    /** 已选中的位置集合（多选模式下使用） */
    private final List<Integer> selectedPositions = new ArrayList<>();
    /** 当前播放的音乐索引，-1 表示未播放 */
    private int currentPlayingIndex = -1;
    /** 点击事件监听器 */
    private OnItemClickListener listener;

    /**
     * 构造函数，创建空的音乐列表
     */
    public MusicPlaylistAdapter() {
        this.musicItems = new ArrayList<>();
    }

    /**
     * 设置列表项点击事件监听器
     * @param listener 监听器实例
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /**
     * 设置音乐列表数据并刷新列表
     * 传入 null 时清空列表
     *
     * @param items 音乐项列表
     */
    public void setMusicItems(List<MusicItem> items) {
        if (items == null) {
            this.musicItems = new ArrayList<>();
        } else {
            this.musicItems = new ArrayList<>(items);
        }
        notifyDataSetChanged();
    }

    /**
     * 在指定位置批量插入音乐项
     * 使用 notifyItemRangeInserted 实现局部刷新，性能更优
     *
     * @param newItems      要插入的音乐项列表
     * @param startPosition 插入起始位置
     */
    public void addMusicItemsRange(List<MusicItem> newItems, int startPosition) {
        if (newItems == null || newItems.isEmpty()) {
            return;
        }
        // 边界保护：插入位置不超过列表大小
        int insertPos = Math.min(startPosition, this.musicItems.size());
        this.musicItems.addAll(insertPos, newItems);
        notifyItemRangeInserted(insertPos, newItems.size());
    }

    /**
     * 批量删除指定位置的音乐项
     * 按位置从大到小删除，避免索引偏移导致删除错误
     *
     * @param positions 要删除的位置列表
     */
    public void removeMusicItems(List<Integer> positions) {
        if (positions == null || positions.isEmpty()) return;

        // 降序排序，从后往前删除，避免前面的删除影响后面的索引
        List<Integer> sorted = new ArrayList<>(positions);
        Collections.sort(sorted, (a, b) -> b - a);

        for (int pos : sorted) {
            if (pos >= 0 && pos < musicItems.size()) {
                musicItems.remove(pos);
            }
        }
        notifyDataSetChanged();
    }

    /**
     * 设置当前播放的音乐索引并更新UI
     * 仅刷新新旧两个位置，避免全量刷新
     *
     * @param index 当前播放的索引，-1 表示取消高亮
     */
    public void setCurrentPlayingIndex(int index) {
        if (this.currentPlayingIndex == index) return;
        int oldIndex = this.currentPlayingIndex;
        this.currentPlayingIndex = index;
        // 刷新旧位置（恢复正常样式）
        if (oldIndex >= 0) {
            notifyItemChanged(oldIndex);
        }
        // 刷新新位置（设置高亮样式）
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }

    // ========== 选择模式 ==========

    /**
     * 设置多选模式开关
     * 关闭多选模式时会清空已选中的项
     *
     * @param enabled true 开启多选模式，false 关闭
     */
    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) {
            selectedPositions.clear();
        }
        notifyDataSetChanged();
    }

    /**
     * 判断是否处于多选模式
     * @return true 多选模式，false 正常模式
     */
    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    /**
     * 切换指定位置的选中状态
     * 已选中则取消选中，未选中则选中
     *
     * @param position 要切换的位置
     */
    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(Integer.valueOf(position));
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
    }

    /**
     * 获取所有已选中的位置列表（返回副本，避免外部修改内部状态）
     * @return 已选中位置的列表副本
     */
    public List<Integer> getSelectedPositions() {
        return new ArrayList<>(selectedPositions);
    }

    /**
     * 获取已选中的项数量
     * @return 选中项的数量
     */
    public int getSelectedCount() {
        return selectedPositions.size();
    }

    /**
     * 全选：选中列表中所有音乐项
     */
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

    /**
     * 创建 ViewHolder 实例
     * 加载列表项布局并返回对应的 ViewHolder
     *
     * @param parent   父 ViewGroup
     * @param viewType 视图类型
     * @return ViewHolder 实例
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_music_playlist, parent, false);
        return new ViewHolder(view);
    }

    /**
     * 绑定数据到 ViewHolder
     * 根据当前模式（正常/多选/播放中）设置不同的UI样式和点击行为
     *
     * @param holder   ViewHolder 实例
     * @param position 绑定位置
     */
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

    /**
     * 获取列表项总数
     * @return 音乐列表的大小
     */
    @Override
    public int getItemCount() {
        return musicItems.size();
    }

    /**
     * 列表项 ViewHolder，缓存子视图引用以优化滚动性能
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        /** 音乐标题文本 */
        TextView titleTextView;
        /** 艺术家文本 */
        TextView artistTextView;
        /** 播放中指示器图标 */
        ImageView playingIndicator;
        /** 多选复选框图标 */
        ImageView checkBox;
        /** 删除按钮 */
        ImageButton deleteButton;

        /**
         * 构造函数，查找并缓存子视图引用
         * @param itemView 列表项根视图
         */
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
