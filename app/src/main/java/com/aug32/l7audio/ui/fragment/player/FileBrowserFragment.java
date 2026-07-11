package com.aug32.l7audio.ui.fragment.player;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.aug32.l7audio.base.BaseFragment;
import com.aug32.l7audio.R;
import com.aug32.l7audio.ui.adapter.FileBrowserAdapter;
import com.aug32.l7audio.ui.model.FileItem;
import com.aug32.l7audio.utils.AppExecutors;

/**
 * 文件浏览器 Fragment
 *
 * <p>提供内置文件浏览功能，支持目录选择模式和文件选择模式：
 * <ul>
 *   <li>目录选择模式：显示所有文件和目录，但只有目录可选中，用于扫描音乐</li>
 *   <li>文件选择模式：显示所有文件和目录，但只有音频文件可选中，用于添加音乐</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>完全使用 File API，不依赖 MediaStore 或 SAF</li>
 *   <li>全部操作真实文件路径，无缓存复制，零额外存储占用</li>
 *   <li>两种模式交互统一：长按进入多选，单击进入目录/切换选中</li>
 *   <li>支持多存储设备：内部存储、SD卡、U盘等外接存储</li>
 *   <li>不可选项灰色显示，清晰区分可选与不可选</li>
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
    /** 根目录特殊标记：存储设备选择页 */
    private static final String PATH_STORAGE_ROOT = "__storage_root__";

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

        // 从存储设备选择页开始（显示内部存储、U盘等）
        currentPath = PATH_STORAGE_ROOT;
        loadStorageList();
    }

    /**
     * Fragment 视图销毁时调用。
     *
     * <p>置空 View 引用和回调，防止内存泄漏。
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        tvTitle = null;
        tvCurrentPath = null;
        btnBack = null;
        btnHome = null;
        btnSelectAll = null;
        btnCancel = null;
        btnConfirm = null;
        rvFiles = null;
        adapter = null;
        callback = null;
        currentPath = null;
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
        if (PATH_STORAGE_ROOT.equals(currentPath)) {
            // 已经在存储设备选择页，返回取消
            if (callback != null) {
                callback.onCancel();
            }
            return;
        }

        File currentDir = new File(currentPath);
        File parentDir = currentDir.getParentFile();
        if (parentDir != null && parentDir.canRead()) {
            loadDirectory(parentDir.getAbsolutePath());
        } else {
            // 没有上级目录了，回到存储设备选择页
            loadStorageList();
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
            // 如果是存储设备页的".."虚拟项，点击返回主页（取消）
            if (PATH_STORAGE_ROOT.equals(item.path)) {
                if (callback != null) {
                    callback.onCancel();
                }
            } else {
                // 普通目录的".."返回上级目录
                // 如果父目录可读则进入，否则返回存储设备选择页
                File parentDir = new File(item.path);
                if (parentDir.canRead()) {
                    loadDirectory(parentDir.getAbsolutePath());
                } else {
                    loadStorageList();
                }
            }
            return;
        }

        // 存储设备选择页：点击存储设备进入
        if (PATH_STORAGE_ROOT.equals(currentPath)) {
            loadDirectory(item.path);
            return;
        }

        // 多选模式
        if (adapter.isSelectionMode()) {
            // 只有可选的项目才能切换选中
            if (adapter.isSelectable(position)) {
                adapter.toggleSelection(position);
                updateTitle();
                updateConfirmButton();
            }
            return;
        }

        // 单选模式
        if (item.isDirectory) {
            // 目录 → 进入
            loadDirectory(item.path);
        } else if (mode == MODE_FILE && isAudioFile(new File(item.path))) {
            // 文件模式下单击音频文件 → 进入多选模式并选中当前项
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

        // 只有可选的项目才能长按进入多选
        if (!adapter.isSelectable(position)) return;

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
            if (dirPaths.isEmpty() && !PATH_STORAGE_ROOT.equals(currentPath)) {
                dirPaths.add(currentPath);
            }
            if (!dirPaths.isEmpty()) {
                callback.onDirectoriesSelected(dirPaths);
            }
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
     * 加载存储设备列表（内部存储、U盘、SD卡等）
     *
     * <p>作为文件浏览器的根页面，用户可选择进入哪个存储设备。
     * API 24+ 使用 StorageManager 获取存储卷，低版本降级为仅显示内部存储。
     * 始终在列表开头添加"返回上级目录"虚拟项，点击后返回主页（取消）。
     */
    private void loadStorageList() {
        currentPath = PATH_STORAGE_ROOT;
        tvCurrentPath.setText("存储设备");

        // 切换目录时重置多选模式
        adapter.setSelectionMode(false);
        adapter.clearSelection();
        btnSelectAll.setVisibility(View.GONE);

        AppExecutors.getInstance().executeOnComputeThread(() -> {
            List<FileItem> items = new ArrayList<>();
            Context context = getSafeContext();

            // 添加"返回上级目录"虚拟项（存储设备页的根目录，点击返回主页）
            items.add(new FileItem("..", PATH_STORAGE_ROOT, true));

            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // API 24+ 使用 StorageManager 获取所有存储卷
                try {
                    StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                    if (storageManager != null) {
                        List<StorageVolume> volumes = storageManager.getStorageVolumes();
                        for (StorageVolume volume : volumes) {
                            File dir = volume.getDirectory();
                            if (dir == null || !dir.exists() || !dir.canRead()) continue;

                            String name = volume.getDescription(context);
                            if (name == null || name.isEmpty()) {
                                name = volume.isPrimary() ? "内部存储" : dir.getName();
                            }
                            // 标记为目录类型（存储设备当作目录处理）
                            FileItem item = new FileItem(name, dir.getAbsolutePath(), true);
                            item.size = -1; // 存储设备不显示大小
                            item.childCount = countChildren(dir);
                            items.add(item);
                        }
                    }
                } catch (Exception e) {
                    // 降级使用内部存储
                    addInternalStorage(items);
                }
            } else {
                // 低版本降级
                addInternalStorage(items);
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
     * 添加内部存储到列表
     *
     * @param items 文件项列表
     */
    private void addInternalStorage(List<FileItem> items) {
        try {
            File external = Environment.getExternalStorageDirectory();
            if (external != null && external.exists() && external.canRead()) {
                FileItem item = new FileItem("内部存储", external.getAbsolutePath(), true);
                item.size = -1;
                item.childCount = countChildren(external);
                items.add(item);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 加载指定目录的内容
     *
     * <p>两种模式下都显示所有文件和目录：
     * <ul>
     *   <li>目录模式：只有目录可选中，文件灰色不可选</li>
     *   <li>文件模式：只有音频文件可选中，其他文件灰色不可选</li>
     * </ul>
     *
     * @param path 目录路径
     */
    private void loadDirectory(String path) {
        currentPath = path;
        tvCurrentPath.setText(path);

        // 切换目录时重置多选模式
        adapter.setSelectionMode(false);
        adapter.clearSelection();
        btnSelectAll.setVisibility(View.GONE);

        AppExecutors.getInstance().executeOnComputeThread(() -> {
            List<FileItem> items = new ArrayList<>();
            File dir = new File(path);

            // 上级目录项（始终显示，点击时判断父目录是否可读）
            File parentDir = dir.getParentFile();
            if (parentDir != null) {
                items.add(new FileItem("..", parentDir.getAbsolutePath(), true));
            }

            // 读取目录内容（所有文件和目录都显示）
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
                        FileItem item = new FileItem(file);
                        item.childCount = countChildren(file);
                        dirList.add(item);
                    } else {
                        // 所有文件都显示（两种模式下都显示全部文件）
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
     * 统计目录下非隐藏子项数（在后台线程调用，不阻塞 UI）
     */
    private static int countChildren(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            if (!f.getName().startsWith(".")) count++;
        }
        return count;
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
        for (String ext : com.aug32.l7audio.utils.FileUtils.AUDIO_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取可选的项目总数
     *
     * <p>只统计当前模式下允许选中的项目：
     * <ul>
     *   <li>目录模式：只统计目录</li>
     *   <li>文件模式：只统计音频文件</li>
     * </ul>
     *
     * @return 可选中的项目数量
     */
    private int getSelectableCount() {
        int count = 0;
        for (FileItem item : adapter.getItems()) {
            if ("..".equals(item.name)) continue;
            if (PATH_STORAGE_ROOT.equals(currentPath)) {
                // 存储设备选择页：所有存储设备都可选
                count++;
            } else if (mode == MODE_DIRECTORY && item.isDirectory) {
                count++;
            } else if (mode == MODE_FILE && !item.isDirectory
                    && isAudioFile(new File(item.path))) {
                count++;
            }
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
            if (PATH_STORAGE_ROOT.equals(currentPath)) {
                tvTitle.setText("选择存储设备");
            } else if (mode == MODE_DIRECTORY) {
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
            // 目录模式下，在具体目录时没选也可以用当前目录
            enabled = !PATH_STORAGE_ROOT.equals(currentPath) || selectedCount > 0;
            if (selectedCount > 0) {
                text = String.format(Locale.getDefault(), "扫描选中的文件夹(%d个)", selectedCount);
            } else if (!PATH_STORAGE_ROOT.equals(currentPath)) {
                text = "扫描此文件夹";
            } else {
                text = "请选择文件夹";
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
