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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aug32.l7audio.domain.audio.player.MusicItem;
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

    /** 缓存的颜色值，避免 onBindViewHolder 中反复调用 ContextCompat.getColor() */
    private int colorAccent;
    private int colorEven;
    private int colorOdd;
    private int colorPrimary;
    private int colorSecondary;
    /** 颜色是否已初始化 */
    private boolean colorsInitialized = false;

    /**
     * 构造函数，创建空的音乐列表
     */
    public MusicPlaylistAdapter() {
        this.musicItems = new ArrayList<>();
        setHasStableIds(true);
    }

    /**
     * 初始化颜色缓存（首次设置数据时或 context 可用时调用）
     */
    public void initColors(Context context) {
        colorAccent = ContextCompat.getColor(context, R.color.colorAccent);
        colorEven = ContextCompat.getColor(context, R.color.item_background_even);
        colorOdd = ContextCompat.getColor(context, R.color.item_background_odd);
        colorPrimary = ContextCompat.getColor(context, R.color.text_primary);
        colorSecondary = ContextCompat.getColor(context, R.color.text_secondary);
    }

    /**
     * 设置列表项点击事件监听器
     * @param listener 监听器实例
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /**
     * 设置音乐列表数据并刷新列表（使用 DiffUtil 增量刷新）
     * 传入 null 时清空列表
     *
     * @param items 音乐项列表
     */
    public void setMusicItems(List<MusicItem> items) {
        if (items == null) {
            items = new ArrayList<>();
        }
        updateMusicItems(items);
    }

    /**
     * 使用 DiffUtil 增量更新列表
     */
    private void updateMusicItems(List<MusicItem> newItems) {
        final List<MusicItem> oldItems = this.musicItems;
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return oldItems.size(); }

            @Override
            public int getNewListSize() { return newItems.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                MusicItem oldItem = oldItems.get(oldPos);
                MusicItem newItem = newItems.get(newPos);
                return oldItem.filePath != null && oldItem.filePath.equals(newItem.filePath);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                MusicItem o = oldItems.get(oldPos);
                MusicItem n = newItems.get(newPos);
                return o.title != null ? o.title.equals(n.title) : n.title == null
                        && o.artist != null ? o.artist.equals(n.artist) : n.artist == null
                        && o.duration == n.duration;
            }
        }, false);
        this.musicItems = newItems;
        result.dispatchUpdatesTo(this);
    }

    /**
     * 批量删除指定位置的音乐项
     * 逐个 notifyItemRemoved 实现增量动画
     *
     * @param positions 要删除的位置列表
     */
    public void removeMusicItems(List<Integer> positions) {
        if (positions == null || positions.isEmpty()) return;

        List<Integer> sorted = new ArrayList<>(positions);
        Collections.sort(sorted, (a, b) -> b - a);

        for (int pos : sorted) {
            if (pos >= 0 && pos < musicItems.size()) {
                musicItems.remove(pos);
                notifyItemRemoved(pos);
            }
        }
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

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < musicItems.size()) {
            MusicItem item = musicItems.get(position);
            return item.filePath != null ? item.filePath.hashCode() : position;
        }
        return position;
    }

    private void ensureColors(Context context) {
        if (!colorsInitialized) {
            initColors(context);
            colorsInitialized = true;
        }
    }

    private void setOddEvenBackground(ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        ensureColors(context);
        holder.itemView.setBackgroundColor(position % 2 == 0 ? colorEven : colorOdd);
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
        ensureColors(context);

        holder.titleTextView.setText(item.title);
        holder.artistTextView.setText(item.artist);

        if (isSelectionMode) {
            holder.playingIndicator.setVisibility(View.GONE);
            if (selectedPositions.contains(position)) {
                holder.itemView.setBackgroundColor(colorAccent);
                holder.titleTextView.setTextColor(colorPrimary);
                holder.artistTextView.setTextColor(colorSecondary);
            } else {
                setOddEvenBackground(holder, position);
            }
        } else if (position == currentPlayingIndex) {
            holder.itemView.setBackgroundColor(colorAccent);
            holder.titleTextView.setTextColor(colorPrimary);
            holder.artistTextView.setTextColor(colorSecondary);
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
