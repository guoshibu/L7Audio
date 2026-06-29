package com.aug32.l7audio.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aug32.l7audio.R;
import com.aug32.l7audio.ui.model.FileItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文件浏览器列表适配器
 *
 * <p>支持目录选择和文件选择两种模式，支持多选操作。
 * 目录模式下仅显示目录，文件模式下显示目录和音频文件。
 */
public class FileBrowserAdapter extends RecyclerView.Adapter<FileBrowserAdapter.ViewHolder> {

    /** 文件浏览模式：目录选择 */
    public static final int MODE_DIRECTORY = 0;

    /** 文件浏览模式：文件选择 */
    public static final int MODE_FILE = 1;

    private final List<FileItem> items = new ArrayList<>();
    private int mode = MODE_DIRECTORY;
    private boolean selectionMode = false;
    private OnItemClickListener listener;

    /**
     * 点击回调接口
     */
    public interface OnItemClickListener {
        /** 单击项目 */
        void onItemClick(int position);

        /** 长按项目（触发多选模式） */
        void onItemLongClick(int position);
    }

    /**
     * ViewHolder
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCheckbox;
        ImageView ivIcon;
        TextView tvName;
        TextView tvSubtitle;
        ImageView ivArrow;

        public ViewHolder(View itemView) {
            super(itemView);
            ivCheckbox = itemView.findViewById(R.id.iv_checkbox);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvName = itemView.findViewById(R.id.tv_name);
            tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
            ivArrow = itemView.findViewById(R.id.iv_arrow);
        }
    }

    /**
     * 设置点击监听器
     *
     * @param listener 监听器
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /**
     * 设置浏览模式
     *
     * @param mode MODE_DIRECTORY 或 MODE_FILE
     */
    public void setMode(int mode) {
        this.mode = mode;
        notifyDataSetChanged();
    }

    /**
     * 获取当前模式
     *
     * @return 当前模式
     */
    public int getMode() {
        return mode;
    }

    /**
     * 设置选择模式
     *
     * @param selectionMode 是否为多选模式
     */
    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
        notifyDataSetChanged();
    }

    /**
     * 是否为选择模式
     *
     * @return true 表示多选模式
     */
    public boolean isSelectionMode() {
        return selectionMode;
    }

    /**
     * 设置数据列表
     *
     * @param newItems 新的文件项列表
     */
    public void setItems(List<FileItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    /**
     * 获取指定位置的文件项
     *
     * @param position 位置
     * @return 文件项
     */
    public FileItem getItem(int position) {
        if (position < 0 || position >= items.size()) return null;
        return items.get(position);
    }

    /**
     * 获取所有项
     *
     * @return 文件项列表
     */
    public List<FileItem> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * 获取选中的项
     *
     * @return 选中的文件项列表
     */
    public List<FileItem> getSelectedItems() {
        List<FileItem> selected = new ArrayList<>();
        for (FileItem item : items) {
            if (item.isSelected) {
                selected.add(item);
            }
        }
        return selected;
    }

    /**
     * 获取选中数量
     *
     * @return 选中的数量
     */
    public int getSelectedCount() {
        int count = 0;
        for (FileItem item : items) {
            if (item.isSelected) {
                count++;
            }
        }
        return count;
    }

    /**
     * 切换选中状态
     *
     * @param position 位置
     */
    public void toggleSelection(int position) {
        if (position < 0 || position >= items.size()) return;
        FileItem item = items.get(position);
        // 目录模式下只能选目录；文件模式下只能选文件（上级目录特殊项除外）
        if (isParentDirectoryItem(position)) return;
        if (mode == MODE_DIRECTORY && !item.isDirectory) return;
        if (mode == MODE_FILE && item.isDirectory) return;

        item.isSelected = !item.isSelected;
        notifyItemChanged(position);
    }

    /**
     * 全选/取消全选
     *
     * @param selectAll true 全选，false 取消全选
     */
    public void selectAll(boolean selectAll) {
        for (int i = 0; i < items.size(); i++) {
            FileItem item = items.get(i);
            if (isParentDirectoryItem(i)) continue;
            if (mode == MODE_DIRECTORY && !item.isDirectory) continue;
            if (mode == MODE_FILE && item.isDirectory) continue;
            item.isSelected = selectAll;
        }
        notifyDataSetChanged();
    }

    /**
     * 清除所有选中状态
     */
    public void clearSelection() {
        for (FileItem item : items) {
            item.isSelected = false;
        }
        notifyDataSetChanged();
    }

    /**
     * 是否为"上级目录"特殊项
     *
     * @param position 位置
     * @return true 表示是上级目录项
     */
    private boolean isParentDirectoryItem(int position) {
        FileItem item = items.get(position);
        return item != null && "..".equals(item.name);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file_browser, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = items.get(position);
        if (item == null) return;

        boolean isParent = isParentDirectoryItem(position);

        // 图标
        if (isParent) {
            holder.ivIcon.setImageResource(R.drawable.ic_back_folder);
        } else if (item.isDirectory) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder);
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_audio_file);
        }

        // 名称
        holder.tvName.setText(item.name);

        // 副标题
        if (isParent) {
            holder.tvSubtitle.setText("返回上级目录");
        } else if (item.isDirectory) {
            int childCount = getChildCount(item.path);
            holder.tvSubtitle.setText(String.format(Locale.getDefault(), "%d 项", childCount));
        } else {
            holder.tvSubtitle.setText(item.getFormattedSize());
        }

        // 多选模式下显示复选框，隐藏箭头
        if (selectionMode && !isParent && isSelectable(item)) {
            holder.ivCheckbox.setVisibility(View.VISIBLE);
            holder.ivCheckbox.setImageResource(item.isSelected
                    ? android.R.drawable.checkbox_on_background
                    : android.R.drawable.checkbox_off_background);
            holder.ivArrow.setVisibility(View.GONE);
        } else {
            holder.ivCheckbox.setVisibility(View.GONE);
            // 目录显示箭头；文件模式下文件不显示箭头
            if (item.isDirectory) {
                holder.ivArrow.setVisibility(View.VISIBLE);
            } else {
                holder.ivArrow.setVisibility(View.GONE);
            }
        }

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(holder.getAdapterPosition());
            }
        });

        // 长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null && isSelectable(item) && !isParent) {
                listener.onItemLongClick(holder.getAdapterPosition());
                return true;
            }
            return false;
        });
    }

    /**
     * 判断项是否可选中
     *
     * @param item 文件项
     * @return true 表示可选中
     */
    private boolean isSelectable(FileItem item) {
        if (mode == MODE_DIRECTORY) {
            return item.isDirectory;
        } else {
            return !item.isDirectory;
        }
    }

    /**
     * 获取目录下的子项数量（粗略计算，避免性能问题）
     *
     * @param dirPath 目录路径
     * @return 子项数量
     */
    private int getChildCount(String dirPath) {
        try {
            File dir = new File(dirPath);
            File[] files = dir.listFiles();
            if (files == null) return 0;
            int count = 0;
            for (File f : files) {
                // 跳过隐藏文件
                if (f.getName().startsWith(".")) continue;
                count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
