package com.aug32.l7audio.ui.fragment;

import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aug32.l7audio.R;
import com.aug32.l7audio.base.BaseFragment;
import com.aug32.l7audio.ui.adapter.FileBrowserAdapter;
import com.aug32.l7audio.ui.model.FileItem;
import com.aug32.l7audio.utils.AppExecutors;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 文件浏览器 Fragment
 *
 * <p>提供内置文件浏览功能，支持目录选择模式和文件选择模式：
 * <ul>
 *   <li>目录选择模式：仅显示文件夹，用于扫描音乐时选择目录</li>
 *   <li>文件选择模式：显示文件夹和音频文件，用于添加音乐</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>完全使用 File API，不依赖 MediaStore 或 SAF</li>
 *   <li>全部操作真实文件路径，无缓存复制，零额外存储占用</li>
 *   <li>两种模式交互统一：长按进入多选，单击进入目录/切换选中</li>
 * </ul>
 */
public class FileBrowserFragment extends BaseFragment {

    /** 参数键：浏览模式 */
    public static final String ARG_MODE = "mode";

    /** 浏览模式：目录选择（扫描音乐） */
    public static final int MODE_DIRECTORY = FileBrowserAdapter.MODE_DIRECTORY;

    /** 浏览模式：文件选择（添加音乐） */
    public static final int MODE_FILE = FileBrowserAdapter.MODE_FILE;

    /** 支持的音频格式 */
    private static final String[] AUDIO_EXTENSIONS = {
            ".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac", ".wma", ".amr"
    };

    private TextView tvTitle;
    private TextView tvCurrentPath;
    private Button btnBack;
    private Button btnHome;
    private Button btnSelectAll;
    private Button btnCancel;
    private Button btnConfirm;
    private RecyclerView rvFiles;

    private FileBrowserAdapter adapter;
    private int mode = MODE_DIRECTORY;
    private String currentPath;
    private FileBrowserCallback callback;

    /**
     * 文件浏览器回调接口
     */
    public interface FileBrowserCallback {
        /**
         * 目录模式：选择了文件夹
         *
         * @param directoryPaths 选中的目录路径列表
         */
        void onDirectoriesSelected(List<String> directoryPaths);

        /**
         * 文件模式：选择了文件
         *
         * @param filePaths 选中的文件路径列表
         */
        void onFilesSelected(List<String> filePaths);

        /** 用户取消 */
        void onCancel();
    }

    /**
     * 创建 FileBrowserFragment 实例
     *
     * @param mode MODE_DIRECTORY 或 MODE_FILE
     * @return Fragment 实例
     */
    public static FileBrowserFragment newInstance(int mode) {
        FileBrowserFragment fragment = new FileBrowserFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_MODE, mode);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * 设置回调
     *
     * @param callback 回调
     */
    public void setCallback(FileBrowserCallback callback) {
        this.callback = callback;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_file_browser;
    }

    @Override
    protected void initViews(View view) {
        tvTitle = view.findViewById(R.id.tv_title);
        tvCurrentPath = view.findViewById(R.id.tv_current_path);
        btnBack = view.findViewById(R.id.btn_back);
        btnHome = view.findViewById(R.id.btn_home);
        btnSelectAll = view.findViewById(R.id.btn_select_all);
        btnCancel = view.findViewById(R.id.btn_cancel);
        btnConfirm = view.findViewById(R.id.btn_confirm);
        rvFiles = view.findViewById(R.id.rv_files);

        rvFiles.setLayoutManager(new LinearLayoutManager(getSafeContext()));
        adapter = new FileBrowserAdapter();
        rvFiles.setAdapter(adapter);
    }

    @Override
    protected void initData() {
        if (getArguments() != null) {
            mode = getArguments().getInt(ARG_MODE, MODE_DIRECTORY);
        }
        adapter.setMode(mode);
        updateTitle();
        updateConfirmButton();

        // 默认从外部存储根目录开始
        currentPath = getRootDirectory();
        loadDirectory(currentPath);
    }

    @Override
    protected void initListeners() {
        btnBack.setOnClickListener(v -> handleBackClick());

        btnHome.setOnClickListener(v -> {
            if (callback != null) {
                callback.onCancel();
            }
        });

        btnSelectAll.setOnClickListener(v -> {
            int selectedCount = adapter.getSelectedCount();
            int totalSelectable = getSelectableCount();
            if (selectedCount == totalSelectable && totalSelectable > 0) {
                adapter.selectAll(false);
            } else {
                adapter.selectAll(true);
            }
            updateTitle();
            updateConfirmButton();
        });

        btnCancel.setOnClickListener(v -> {
            if (adapter.isSelectionMode()) {
                // 取消多选模式
                adapter.setSelectionMode(false);
                adapter.clearSelection();
                updateTitle();
                updateConfirmButton();
                btnSelectAll.setVisibility(View.GONE);
            } else if (callback != null) {
                callback.onCancel();
            }
        });

        btnConfirm.setOnClickListener(v -> handleConfirm());

        adapter.setOnItemClickListener(new FileBrowserAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                handleItemClick(position);
            }

            @Override
            public void onItemLongClick(int position) {
                handleItemLongClick(position);
            }
        });
    }

    /**
     * 处理返回键点击
     */
    private void handleBackClick() {
        if (adapter.isSelectionMode()) {
            // 多选模式下，取消多选
            adapter.setSelectionMode(false);
            adapter.clearSelection();
            updateTitle();
            updateConfirmButton();
            btnSelectAll.setVisibility(View.GONE);
            return;
        }

        // 返回上级目录
        File currentDir = new File(currentPath);
        File parentDir = currentDir.getParentFile();
        if (parentDir != null && parentDir.canRead()) {
            loadDirectory(parentDir.getAbsolutePath());
        } else {
            // 已经在根目录，返回
            if (callback != null) {
                callback.onCancel();
            }
        }
    }

    /**
     * 处理项目点击
     *
     * @param position 位置
     */
    private void handleItemClick(int position) {
        FileItem item = adapter.getItem(position);
        if (item == null) return;

        // 上级目录特殊项
        if ("..".equals(item.name)) {
            File parentDir = new File(currentPath).getParentFile();
            if (parentDir != null && parentDir.canRead()) {
                loadDirectory(parentDir.getAbsolutePath());
            }
            return;
        }

        // 多选模式
        if (adapter.isSelectionMode()) {
            adapter.toggleSelection(position);
            updateTitle();
            updateConfirmButton();
            return;
        }

        // 单选模式
        if (item.isDirectory) {
            // 目录 → 进入
            loadDirectory(item.path);
        } else if (mode == MODE_FILE) {
            // 文件模式下单击文件 → 进入多选模式并选中当前项
            adapter.setSelectionMode(true);
            adapter.toggleSelection(position);
            btnSelectAll.setVisibility(View.VISIBLE);
            updateTitle();
            updateConfirmButton();
        }
    }

    /**
     * 处理项目长按
     *
     * @param position 位置
     */
    private void handleItemLongClick(int position) {
        if (adapter.isSelectionMode()) return;

        FileItem item = adapter.getItem(position);
        if (item == null) return;

        // 目录模式下只有目录可长按；文件模式下只有文件可长按
        if (mode == MODE_DIRECTORY && !item.isDirectory) return;
        if (mode == MODE_FILE && item.isDirectory) return;

        // 进入多选模式并选中当前项
        adapter.setSelectionMode(true);
        adapter.toggleSelection(position);
        btnSelectAll.setVisibility(View.VISIBLE);
        updateTitle();
        updateConfirmButton();
    }

    /**
     * 处理确认按钮点击
     */
    private void handleConfirm() {
        if (callback == null) return;

        List<FileItem> selectedItems = adapter.getSelectedItems();

        if (mode == MODE_DIRECTORY) {
            // 目录模式
            List<String> dirPaths = new ArrayList<>();
            for (FileItem item : selectedItems) {
                if (item.isDirectory) {
                    dirPaths.add(item.path);
                }
            }
            // 没有多选时，使用当前目录
            if (dirPaths.isEmpty()) {
                dirPaths.add(currentPath);
            }
            callback.onDirectoriesSelected(dirPaths);
        } else {
            // 文件模式
            List<String> filePaths = new ArrayList<>();
            for (FileItem item : selectedItems) {
                if (!item.isDirectory) {
                    filePaths.add(item.path);
                }
            }
            if (!filePaths.isEmpty()) {
                callback.onFilesSelected(filePaths);
            }
        }
    }

    /**
     * 加载指定目录的内容
     *
     * @param path 目录路径
     */
    private void loadDirectory(String path) {
        currentPath = path;
        tvCurrentPath.setText(path);

        AppExecutors.getInstance().executeOnComputeThread(() -> {
            List<FileItem> items = new ArrayList<>();
            File dir = new File(path);

            // 上级目录项（非根目录时添加）
            File parentDir = dir.getParentFile();
            if (parentDir != null && parentDir.canRead()) {
                items.add(new FileItem("..", parentDir.getAbsolutePath(), true));
            }

            // 读取目录内容
            File[] files = dir.listFiles();
            if (files != null) {
                List<FileItem> dirList = new ArrayList<>();
                List<FileItem> fileList = new ArrayList<>();

                for (File file : files) {
                    // 跳过隐藏文件
                    if (file.getName().startsWith(".")) continue;

                    if (file.isDirectory()) {
                        // 跳过 .nomedia 目录
                        if (hasNomediaFile(file)) continue;
                        dirList.add(new FileItem(file));
                    } else if (mode == MODE_FILE && isAudioFile(file)) {
                        fileList.add(new FileItem(file));
                    }
                }

                // 排序：按名称升序
                sortByName(dirList);
                sortByName(fileList);

                items.addAll(dirList);
                items.addAll(fileList);
            }

            final List<FileItem> result = items;
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    adapter.setItems(result);
                    updateTitle();
                    updateConfirmButton();
                });
            }
        });
    }

    /**
     * 按名称排序
     *
     * @param list 文件项列表
     */
    private void sortByName(List<FileItem> list) {
        Collections.sort(list, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem o1, FileItem o2) {
                return o1.name.toLowerCase(Locale.getDefault())
                        .compareTo(o2.name.toLowerCase(Locale.getDefault()));
            }
        });
    }

    /**
     * 检查目录是否包含 .nomedia 文件
     *
     * @param dir 目录
     * @return true 表示有 .nomedia 文件
     */
    private boolean hasNomediaFile(File dir) {
        try {
            File nomedia = new File(dir, ".nomedia");
            return nomedia.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断是否为音频文件
     *
     * @param file 文件
     * @return true 表示是支持的音频格式
     */
    private boolean isAudioFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase(Locale.getDefault());
        for (String ext : AUDIO_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取根目录路径
     *
     * @return 根目录路径
     */
    private String getRootDirectory() {
        try {
            File external = Environment.getExternalStorageDirectory();
            if (external != null && external.exists() && external.canRead()) {
                return external.getAbsolutePath();
            }
        } catch (Exception ignored) {
        }
        return "/";
    }

    /**
     * 获取可选的项目总数
     *
     * @return 可选中的项目数量
     */
    private int getSelectableCount() {
        int count = 0;
        for (FileItem item : adapter.getItems()) {
            if ("..".equals(item.name)) continue;
            if (mode == MODE_DIRECTORY && item.isDirectory) count++;
            if (mode == MODE_FILE && !item.isDirectory) count++;
        }
        return count;
    }

    /**
     * 更新标题
     */
    private void updateTitle() {
        int selectedCount = adapter.getSelectedCount();
        if (adapter.isSelectionMode() && selectedCount > 0) {
            if (mode == MODE_DIRECTORY) {
                tvTitle.setText(String.format(Locale.getDefault(), "选择文件夹(%d)", selectedCount));
            } else {
                tvTitle.setText(String.format(Locale.getDefault(), "选择文件(%d)", selectedCount));
            }
        } else {
            if (mode == MODE_DIRECTORY) {
                tvTitle.setText("选择文件夹");
            } else {
                tvTitle.setText("选择文件");
            }
        }

        // 更新全选按钮文字
        int totalSelectable = getSelectableCount();
        if (totalSelectable > 0 && selectedCount == totalSelectable) {
            btnSelectAll.setText("取消全选");
        } else {
            btnSelectAll.setText("全选");
        }
    }

    /**
     * 更新确认按钮状态
     */
    private void updateConfirmButton() {
        int selectedCount = adapter.getSelectedCount();
        boolean enabled;
        String text;

        if (mode == MODE_DIRECTORY) {
            // 目录模式下，没选也可以用当前目录
            enabled = true;
            if (selectedCount > 0) {
                text = String.format(Locale.getDefault(), "扫描选中的文件夹(%d个)", selectedCount);
            } else {
                text = "扫描此文件夹";
            }
        } else {
            // 文件模式下，必须有选中
            enabled = selectedCount > 0;
            text = String.format(Locale.getDefault(), "确认添加(%d首)", selectedCount);
        }

        btnConfirm.setEnabled(enabled);
        btnConfirm.setText(text);
        btnConfirm.setAlpha(enabled ? 1.0f : 0.5f);
    }
}
