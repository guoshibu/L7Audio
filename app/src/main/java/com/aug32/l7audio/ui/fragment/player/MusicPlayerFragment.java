package com.aug32.l7audio.ui.fragment.player;

import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.aug32.l7audio.domain.audio.AudioServiceLocator;
import com.aug32.l7audio.domain.audio.player.LrcParser;
import com.aug32.l7audio.domain.audio.player.MusicItem;
import com.aug32.l7audio.domain.audio.player.MusicPlayerManager;
import com.aug32.l7audio.R;
import com.aug32.l7audio.ui.activity.MainActivity;
import com.aug32.l7audio.ui.adapter.MusicPlaylistAdapter;
import com.aug32.l7audio.utils.AlbumArtCache;
import com.aug32.l7audio.utils.AppExecutors;
import com.aug32.l7audio.utils.AppLog;

/**
 * 音乐播放器 Fragment
 *
 * <p>职责：
 * <ul>
 *   <li>只处理 UI 展示和用户交互</li>
 *   <li>业务逻辑全部委托给 MusicPlayerManager</li>
 *   <li>生命周期内正确注册/注销回调</li>
 *   <li>音乐播放列表的展示与管理</li>
 *   <li>歌词同步显示与滚动</li>
 * </ul>
 *
 * <p>设计模式：采用 MVP 风格的分层，Fragment 作为 View 层，
 * MusicPlayerManager 作为 Presenter/Model 层处理播放逻辑。
 */
public class MusicPlayerFragment extends Fragment {

    /** 日志标签 */
    private static final String TAG = "MusicPlayerFragment";

    // ========== UI 组件 ==========
    /** 歌曲标题文本视图 */
    private TextView tvSongTitle;
    /** 歌曲艺术家文本视图 */
    private TextView tvSongArtist;
    /** 专辑封面图片视图 */
    private ImageView ivAlbumArt;
    /** 播放进度条 */
    private SeekBar seekBar;
    /** 当前播放时间文本 */
    private TextView tvCurrentTime;
    /** 歌曲总时长文本 */
    private TextView tvTotalTime;
    /** 播放/暂停按钮 */
    private Button btnPlayPause;
    /** 上一首按钮 */
    private Button btnPrev;
    /** 下一首按钮 */
    private Button btnNext;
    /** 循环模式切换按钮 */
    private Button btnRepeat;
    /** 音量调节滑块 */
    private SeekBar sbVolume;
    /** 音量数值标签 */
    private TextView tvVolumeLabel;
    /** 歌词滚动容器 */
    private ScrollView svLyrics;
    /** 歌词行布局容器 */
    private android.widget.LinearLayout llLyricsContainer;
    /** 无歌词时的占位文本 */
    private TextView tvLyricsPlaceholder;
    /** 播放列表 RecyclerView */
    private RecyclerView rvPlaylist;
    /** 扫描音乐按钮 */
    private Button btnScanMusic;
    /** 选择文件按钮 */
    private Button btnSelectFile;
    /** 全选按钮 */
    private Button btnSelectAll;
    /** 删除选中按钮 */
    private Button btnDelete;
    /** 取消选择按钮 */
    private Button btnCancel;
    /** 播放列表标题（含数量） */
    private TextView tvPlaylistTitle;

    // ========== 数据 ==========
    /** 音乐播放器管理器（业务逻辑委托） */
    private MusicPlayerManager musicPlayerManager;
    /** 播放列表适配器 */
    private MusicPlaylistAdapter playlistAdapter;
    /** 当前歌曲的歌词行列表 */
    private List<LrcParser.LrcLine> lyricLines = new ArrayList<>();
    /** 当前高亮的歌词行索引 */
    private int currentLyricIndex = -1;
    /** 上次滚动到的列表位置（防重复滚动） */
    private int lastScrollToIndex = -1;
    /** 用户是否正在拖动进度条（防止进度更新冲突） */
    private boolean isUserSeeking = false;
    /** 权限授予后待执行的操作：0-无，1-打开目录浏览器，2-打开文件浏览器 */
    private int pendingPermissionAction = 0;
    /** 播放列表变化回调被跳过时置 true，onResume 时检查并刷新 */
    private boolean pendingRefresh = false;

    // ========== 回调 ==========
    private final MusicPlayerManager.MusicPlayerCallback playerCallback =
            new MusicPlayerManager.MusicPlayerCallback() {
                @Override
                public void onPlaybackStarted(int index) {
                    if (!isAdded() || getActivity() == null) return;
                    updatePlayPauseButton(true);
                    updateCurrentSongInfo();
                    playlistAdapter.setCurrentPlayingIndex(index);
                    scrollPlaylistTo(index);
                    loadLyricsForCurrentSong();
                }

                @Override
                public void onPlaybackPaused() {
                    if (!isAdded() || getActivity() == null) return;
                    updatePlayPauseButton(false);
                }

                @Override
                public void onPlaybackStopped() {
                    if (!isAdded() || getActivity() == null) return;
                    updatePlayPauseButton(false);
                }

                @Override
                public void onPlaybackProgress(long current, long duration) {
                    if (!isAdded() || getActivity() == null) return;
                    if (!isUserSeeking) {
                        seekBar.setProgress((int) current);
                        seekBar.setMax((int) duration);
                    }
                    tvCurrentTime.setText(formatTime(current));
                    tvTotalTime.setText(formatTime(duration));
                    updateLyricHighlight(current);
                }

                @Override
                public void onPlaylistChanged() {
                    if (!isAdded() || getActivity() == null) {
                        pendingRefresh = true;
                        return;
                    }
                    refreshPlaylist();
                }

                @Override
                public void onLyricsLoaded(int index) {
                    if (!isAdded() || getActivity() == null) return;
                    loadLyricsForCurrentSong();
                }

                @Override
                public void onError(String error) {
                    if (!isAdded() || getActivity() == null) return;
                    Toast.makeText(getActivity(), error, Toast.LENGTH_SHORT).show();
                }
            };

    // ========== 权限 launcher ==========
    private final ActivityResultLauncher<String[]> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean granted = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    granted = result.get(Manifest.permission.READ_MEDIA_AUDIO);
                } else {
                    granted = result.get(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
                if (granted != null && granted) {
                    int action = pendingPermissionAction;
                    pendingPermissionAction = 0;
                    if (action == 1) {
                        openFileBrowserForDirectory();
                    } else if (action == 2) {
                        openFileBrowserForFiles();
                    }
                } else {
                    pendingPermissionAction = 0;
                    Toast.makeText(getActivity(), "需要存储权限才能访问文件", Toast.LENGTH_SHORT).show();
                }
            });

    // ========== 生命周期 ==========

    /**
     * 创建 Fragment 视图。
     *
     * <p>加载布局文件并初始化视图控件。
     *
     * @param inflater 布局填充器
     * @param container 父容器视图
     * @param savedInstanceState 保存的实例状态
     * @return 创建的根视图
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music_player, container, false);
        initViews(view);
        return view;
    }

    /**
     * 视图创建完成后的回调。
     *
     * <p>初始化播放器、设置监听器、刷新播放列表、同步当前播放状态。
     * 在此处初始化是因为视图已完全创建，可以安全访问所有 UI 控件。
     *
     * @param view 创建的根视图
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initPlayer();
        setupListeners();
        refreshPlaylist();
        updateUIWithCurrentState();
    }

    /**
     * Fragment 对用户可见时的回调。
     *
     * <p>重新注册播放器回调并同步 UI 状态，
     * 确保用户返回页面时显示最新的播放状态。
     */
    @Override
    public void onResume() {
        super.onResume();
        if (musicPlayerManager != null) {
            musicPlayerManager.setCallback(playerCallback);
            if (pendingRefresh) {
                pendingRefresh = false;
                refreshPlaylist();
            }
            updateUIWithCurrentState();
        }
    }

    /**
     * Fragment 对用户不可见时的回调。
     *
     * <p>注销播放器回调，避免后台状态更新导致内存泄漏或空指针异常。
     */
    @Override
    public void onPause() {
        super.onPause();
        if (musicPlayerManager != null) {
            musicPlayerManager.setCallback(null);
        }
    }

    /**
     * 视图销毁时的回调。
     *
     * <p>注销播放器回调并释放所有 UI 组件引用，
     * 防止 Fragment 视图销毁后内存泄漏。
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (musicPlayerManager != null) {
            musicPlayerManager.setCallback(null);
        }
        tvSongTitle = null;
        tvSongArtist = null;
        ivAlbumArt = null;
        seekBar = null;
        tvCurrentTime = null;
        tvTotalTime = null;
        btnPlayPause = null;
        btnPrev = null;
        btnNext = null;
        btnRepeat = null;
        sbVolume = null;
        tvVolumeLabel = null;
        svLyrics = null;
        llLyricsContainer = null;
        tvLyricsPlaceholder = null;
        rvPlaylist = null;
        btnScanMusic = null;
        btnSelectFile = null;
        btnSelectAll = null;
        btnDelete = null;
        btnCancel = null;
        tvPlaylistTitle = null;
        playlistAdapter = null;
    }

    // ========== 初始化 ==========

    private void initViews(View view) {
        tvSongTitle = view.findViewById(R.id.tv_song_title);
        tvSongArtist = view.findViewById(R.id.tv_song_artist);
        ivAlbumArt = view.findViewById(R.id.iv_album_art);
        seekBar = view.findViewById(R.id.seek_bar);
        tvCurrentTime = view.findViewById(R.id.tv_current_time);
        tvTotalTime = view.findViewById(R.id.tv_total_time);
        btnPlayPause = view.findViewById(R.id.btn_play_pause);
        btnPrev = view.findViewById(R.id.btn_prev);
        btnNext = view.findViewById(R.id.btn_next);
        btnRepeat = view.findViewById(R.id.btn_repeat);
        sbVolume = view.findViewById(R.id.sb_volume);
        tvVolumeLabel = view.findViewById(R.id.tv_volume_label);
        svLyrics = view.findViewById(R.id.sv_lyrics);
        llLyricsContainer = view.findViewById(R.id.ll_lyrics_container);
        tvLyricsPlaceholder = view.findViewById(R.id.tv_lyrics_placeholder);
        rvPlaylist = view.findViewById(R.id.rv_playlist);
        btnScanMusic = view.findViewById(R.id.btn_scan_music);
        btnSelectFile = view.findViewById(R.id.btn_select_file);
        btnSelectAll = view.findViewById(R.id.btn_select_all);
        btnDelete = view.findViewById(R.id.btn_delete);
        btnCancel = view.findViewById(R.id.btn_cancel);
        tvPlaylistTitle = view.findViewById(R.id.tv_playlist_title);

        // 设置 RecyclerView
        playlistAdapter = new MusicPlaylistAdapter();
        rvPlaylist.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvPlaylist.setAdapter(playlistAdapter);
        rvPlaylist.addItemDecoration(new DividerItemDecoration(
                getActivity(), DividerItemDecoration.VERTICAL));
    }

    private void initPlayer() {
        musicPlayerManager = AudioServiceLocator.getInstance().getMusicPlayerManager();
        if (musicPlayerManager == null) {
            AppLog.e(TAG, "MusicPlayerManager is null");
        }
    }

    private void setupListeners() {
        // 播放/暂停
        btnPlayPause.setOnClickListener(v -> {
            if (musicPlayerManager != null) {
                musicPlayerManager.togglePlayPause();
            }
        });

        // 上一首
        btnPrev.setOnClickListener(v -> {
            if (musicPlayerManager != null) {
                musicPlayerManager.playPrevious();
            }
        });

        // 下一首
        btnNext.setOnClickListener(v -> {
            if (musicPlayerManager != null) {
                musicPlayerManager.playNext();
            }
        });

        // 循环模式
        btnRepeat.setOnClickListener(v -> cycleRepeatMode());

        // 进度条
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (musicPlayerManager == null) return;

                musicPlayerManager.seekTo(seekBar.getProgress());
                if (!musicPlayerManager.isPlaying()) {
                    musicPlayerManager.resume();
                }
            }
        });

        // 音量
        sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && musicPlayerManager != null) {
                    musicPlayerManager.setVolume(progress);
                }
                tvVolumeLabel.setText("音量: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbVolume.setProgress(80);

        // 扫描音乐
        btnScanMusic.setOnClickListener(v -> openFileBrowserForDirectory());

        // 添加音乐
        btnSelectFile.setOnClickListener(v -> openFileBrowserForFiles());

        // 全选
        btnSelectAll.setOnClickListener(v -> {
            if (playlistAdapter != null) {
                playlistAdapter.selectAll();
                updateDeleteButton();
            }
        });

        // 删除
        btnDelete.setOnClickListener(v -> deleteSelectedSongs());

        // 取消选择
        btnCancel.setOnClickListener(v -> exitSelectionMode());

        // 列表点击
        playlistAdapter.setOnItemClickListener(new MusicPlaylistAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                if (playlistAdapter.isSelectionMode()) {
                    playlistAdapter.toggleSelection(position);
                    updateDeleteButton();
                } else if (musicPlayerManager != null) {
                    musicPlayerManager.start(position, 0);
                }
            }

            @Override
            public void onItemLongClick(int position) {
                if (!playlistAdapter.isSelectionMode()) {
                    enterSelectionMode();
                    playlistAdapter.toggleSelection(position);
                    updateDeleteButton();
                }
            }
        });
    }

    // ========== UI 更新 ==========

    private void updateUIWithCurrentState() {
        if (musicPlayerManager == null) return;

        // 更新播放/暂停按钮
        boolean isPlaying = musicPlayerManager.isPlaying();
        updatePlayPauseButton(isPlaying);

        // 更新当前歌曲信息
        updateCurrentSongInfo();

        // 更新进度
        long pos = musicPlayerManager.getCurrentPosition();
        long dur = musicPlayerManager.getDuration();
        seekBar.setProgress((int) pos);
        seekBar.setMax((int) dur);
        tvCurrentTime.setText(formatTime(pos));
        tvTotalTime.setText(formatTime(dur));

        // 更新循环模式按钮
        updateRepeatButton();

        // 更新列表高亮
        playlistAdapter.setCurrentPlayingIndex(musicPlayerManager.getCurrentIndex());

        // 加载歌词
        loadLyricsForCurrentSong();
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        btnPlayPause.setText(isPlaying ? "暂停" : "播放");
    }

    private void scrollPlaylistTo(int index) {
        if (rvPlaylist == null || index < 0 || index == lastScrollToIndex) return;
        lastScrollToIndex = index;
        LinearLayoutManager layoutManager = (LinearLayoutManager) rvPlaylist.getLayoutManager();
        if (layoutManager == null) return;
        int firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition();
        int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
        if (index < firstVisible || index > lastVisible) {
            rvPlaylist.smoothScrollToPosition(index);
        }
    }

    private void updateCurrentSongInfo() {
        if (musicPlayerManager == null) return;
        MusicItem item = musicPlayerManager.getCurrentMusicItem();
        if (item != null) {
            tvSongTitle.setText(item.title);
            tvSongArtist.setText(item.artist);
            // 加载专辑封面
            loadAlbumArt(item);
        } else {
            tvSongTitle.setText("未选择歌曲");
            tvSongArtist.setText("");
            // 无歌曲时显示默认封面
            if (ivAlbumArt != null) {
                ivAlbumArt.setImageResource(R.drawable.ic_launcher_playstore);
            }
        }
    }

    /**
     * 加载专辑封面图片
     *
     * <p>通过 AlbumArtCache 统一管理，支持 LRU 内存缓存 + 文件缓存 + 采样压缩。
     * 首次加载在工作线程解码，避免主线程卡顿。
     *
     * @param item 音乐项
     */
    private void loadAlbumArt(MusicItem item) {
        if (ivAlbumArt == null) return;
        if (item == null || item.filePath == null) {
            ivAlbumArt.setImageResource(R.drawable.ic_launcher_playstore);
            return;
        }

        // 从缓存获取（内存 LRU 命中则直接返回 Bitmap）
        int reqWidth = ivAlbumArt.getWidth() > 0 ? ivAlbumArt.getWidth() : 360;
        int reqHeight = ivAlbumArt.getHeight() > 0 ? ivAlbumArt.getHeight() : 360;
        android.graphics.Bitmap cached = AlbumArtCache.getInstance(requireActivity())
                .get(item.filePath, item.albumArt, reqWidth, reqHeight);
        if (cached != null) {
            ivAlbumArt.setImageBitmap(cached);
            return;
        }

        // 缓存未命中且 albumArt 数据存在 → 后台解码
        if (item.albumArt != null && item.albumArt.length > 0) {
            final String path = item.filePath;
            final byte[] data = item.albumArt;
            final int targetW = reqWidth;
            final int targetH = reqHeight;
            final android.content.Context ctx = getActivity();
            if (ctx == null) return;
            AppExecutors.getInstance().executeOnComputeThread(() -> {
                android.graphics.Bitmap bitmap = AlbumArtCache.getInstance(ctx)
                        .get(path, data, targetW, targetH);
                if (bitmap != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (ivAlbumArt != null) {
                            ivAlbumArt.setImageBitmap(bitmap);
                        }
                    });
                }
            });
        } else {
            ivAlbumArt.setImageResource(R.drawable.ic_launcher_playstore);
        }
    }

    private void updateRepeatButton() {
        if (musicPlayerManager == null) return;
        int mode = musicPlayerManager.getRepeatMode();
        switch (mode) {
            case MusicPlayerManager.REPEAT_MODE_ALL:
                btnRepeat.setText("全部循环");
                break;
            case MusicPlayerManager.REPEAT_MODE_SHUFFLE:
                btnRepeat.setText("随机播放");
                break;
            case MusicPlayerManager.REPEAT_MODE_ONE:
                btnRepeat.setText("单曲循环");
                break;
            case MusicPlayerManager.REPEAT_MODE_OFF:
                btnRepeat.setText("单曲播放");
                break;
        }
    }

    private void cycleRepeatMode() {
        if (musicPlayerManager == null) return;
        int mode = musicPlayerManager.getRepeatMode();
        int nextMode = (mode + 1) % 4;
        musicPlayerManager.setRepeatMode(nextMode);
        updateRepeatButton();
        String[] modeNames = {"全部循环", "随机播放", "单曲循环", "单曲播放"};
        Toast.makeText(getActivity(), modeNames[nextMode], Toast.LENGTH_SHORT).show();
    }

    // ========== 播放列表 ==========

    private void refreshPlaylist() {
        if (musicPlayerManager == null || playlistAdapter == null) return;
        List<MusicItem> items = musicPlayerManager.getMusicItems();
        playlistAdapter.setMusicItems(items);
        playlistAdapter.setCurrentPlayingIndex(musicPlayerManager.getCurrentIndex());
        tvPlaylistTitle.setText("播放列表 (" + items.size() + ")");
    }

    // ========== 选择模式 ==========

    private void enterSelectionMode() {
        playlistAdapter.setSelectionMode(true);
        btnSelectAll.setVisibility(View.VISIBLE);
        btnDelete.setVisibility(View.VISIBLE);
        btnCancel.setVisibility(View.VISIBLE);
        btnScanMusic.setVisibility(View.GONE);
        btnSelectFile.setVisibility(View.GONE);
    }

    private void exitSelectionMode() {
        playlistAdapter.setSelectionMode(false);
        btnSelectAll.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);
        btnCancel.setVisibility(View.GONE);
        btnScanMusic.setVisibility(View.VISIBLE);
        btnSelectFile.setVisibility(View.VISIBLE);
    }

    private void updateDeleteButton() {
        int count = playlistAdapter.getSelectedCount();
        btnDelete.setText("删除(" + count + ")");
        btnDelete.setEnabled(count > 0);
    }

    private void deleteSelectedSongs() {
        if (musicPlayerManager == null) return;
        List<Integer> positions = playlistAdapter.getSelectedPositions();
        if (positions.isEmpty()) return;

        musicPlayerManager.removeMusicItems(positions);
        exitSelectionMode();
        Toast.makeText(getActivity(), "已删除 " + positions.size() + " 首歌曲", Toast.LENGTH_SHORT).show();
    }

    // ========== 文件浏览器 ==========

    /** 支持的音频格式扩展名 */
    private static final String[] AUDIO_EXTENSIONS = {
            ".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac", ".wma", ".amr"
    };

    /**
     * 打开文件浏览器选择目录（扫描音乐）
     *
     * <p>先检查存储权限，权限授予后打开文件浏览器。
     * 选择目录后递归扫描该目录下所有音频文件并添加到播放列表。
     */
    private void openFileBrowserForDirectory() {
        if (getActivity() == null) return;
        // 检查存储权限
        if (!hasStoragePermission()) {
            pendingPermissionAction = 1;
            requestStoragePermission();
            return;
        }
        if (!(getActivity() instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) getActivity();

        activity.showFileBrowserFragment(FileBrowserFragment.MODE_DIRECTORY,
                new FileBrowserFragment.FileBrowserCallback() {
                    @Override
                    public void onDirectoriesSelected(List<String> directoryPaths) {
                        activity.closeFileBrowserFragment();
                        scanDirectories(directoryPaths);
                    }

                    @Override
                    public void onFilesSelected(List<String> filePaths) {
                        // 目录模式下不会回调此方法
                    }

                    @Override
                    public void onCancel() {
                        activity.closeFileBrowserFragment();
                    }
                });
    }

    /**
     * 检查是否有存储权限
     *
     * @return true 表示已有存储权限
     */
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireActivity(),
                    Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireActivity(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * 请求存储权限
     */
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            storagePermissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_AUDIO});
        } else {
            storagePermissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
        }
    }

    /**
     * 打开文件浏览器选择文件（添加音乐）
     *
     * <p>先检查存储权限，权限授予后打开文件浏览器。
     * 支持多选，选择完成后直接添加到播放列表。
     */
    private void openFileBrowserForFiles() {
        if (getActivity() == null) return;
        // 检查存储权限
        if (!hasStoragePermission()) {
            pendingPermissionAction = 2;
            requestStoragePermission();
            return;
        }
        if (!(getActivity() instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) getActivity();

        activity.showFileBrowserFragment(FileBrowserFragment.MODE_FILE,
                new FileBrowserFragment.FileBrowserCallback() {
                    @Override
                    public void onDirectoriesSelected(List<String> directoryPaths) {
                        // 文件模式下不会回调此方法
                    }

                    @Override
                    public void onFilesSelected(List<String> filePaths) {
                        activity.closeFileBrowserFragment();
                        addFilesToPlaylist(filePaths);
                    }

                    @Override
                    public void onCancel() {
                        activity.closeFileBrowserFragment();
                    }
                });
    }

    /**
     * 递归扫描选中的目录，收集所有音频文件
     *
     * <p>在计算线程中执行扫描，扫描完成后通过 addMusicFilesWithUris 添加到播放列表。
     * 跳过隐藏文件和 .nomedia 目录。
     *
     * @param directoryPaths 要扫描的目录路径列表
     */
    private void scanDirectories(List<String> directoryPaths) {
        if (musicPlayerManager == null || directoryPaths == null || directoryPaths.isEmpty()) return;
        Toast.makeText(getActivity(), "正在扫描音乐...", Toast.LENGTH_SHORT).show();

        AppExecutors.getInstance().executeOnComputeThread(() -> {
            List<String> audioFiles = new ArrayList<>();
            for (String dirPath : directoryPaths) {
                scanDirectoryRecursive(new File(dirPath), audioFiles);
            }

            final List<String> result = audioFiles;
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> addFilesToPlaylist(result));
            }
        });
    }

    /**
     * 递归扫描目录下的音频文件
     *
     * <p>深度优先遍历目录树，收集所有支持的音频格式文件。
     * 跳过隐藏文件和包含 .nomedia 的目录。
     *
     * @param dir      要扫描的目录
     * @param result   收集结果的列表
     */
    private void scanDirectoryRecursive(File dir, List<String> result) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || !dir.canRead()) return;

        // 跳过包含 .nomedia 的目录
        File nomedia = new File(dir, ".nomedia");
        if (nomedia.exists()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            // 跳过隐藏文件
            if (file.getName().startsWith(".")) continue;

            if (file.isDirectory()) {
                scanDirectoryRecursive(file, result);
            } else if (isAudioFile(file)) {
                result.add(file.getAbsolutePath());
            }
        }
    }

    /**
     * 判断是否为支持的音频文件
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
     * 将文件批量添加到播放列表
     *
     * <p>使用真实文件路径直接添加，无需缓存复制，零额外存储占用。
     *
     * @param filePaths 文件路径列表
     */
    private void addFilesToPlaylist(List<String> filePaths) {
        if (musicPlayerManager == null || filePaths == null || filePaths.isEmpty()) return;
        musicPlayerManager.addMusicFilesWithUris(filePaths, filePaths,
                new MusicPlayerManager.AddMusicCallback() {
                    @Override
                    public void onAddComplete(List<MusicItem> addedItems, int startPosition,
                                             int skippedExistCount, int skippedFailedCount) {
                        if (!isAdded() || getActivity() == null) {
                            pendingRefresh = true;
                            return;
                        }

                        // 构建提示信息
                        String message;
                        if (skippedExistCount > 0 && skippedFailedCount > 0) {
                            message = String.format("添加了 %d 首，跳过 %d 首（已存在 %d，失败 %d）",
                                    addedItems.size(), skippedExistCount + skippedFailedCount,
                                    skippedExistCount, skippedFailedCount);
                        } else if (skippedExistCount > 0) {
                            message = String.format("添加了 %d 首，已跳过 %d 首（已存在）",
                                    addedItems.size(), skippedExistCount);
                        } else if (skippedFailedCount > 0) {
                            message = String.format("添加了 %d 首，失败 %d 首",
                                    addedItems.size(), skippedFailedCount);
                        } else {
                            message = "添加了 " + addedItems.size() + " 首歌曲";
                        }
                        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ========== 歌词 ==========

    private void loadLyricsForCurrentSong() {
        if (musicPlayerManager == null || llLyricsContainer == null) return;

        MusicItem item = musicPlayerManager.getCurrentMusicItem();
        if (item == null || item.lyrics == null || item.lyrics.isEmpty()) {
            showLyricsPlaceholder("暂无歌词");
            return;
        }

        lyricLines = LrcParser.parse(item.lyrics);
        if (lyricLines.isEmpty()) {
            showLyricsPlaceholder("暂无歌词");
            return;
        }

        renderLyrics(lyricLines);
        currentLyricIndex = -1;
    }

    private void showLyricsPlaceholder(String text) {
        llLyricsContainer.removeAllViews();
        tvLyricsPlaceholder.setText(text);
        llLyricsContainer.addView(tvLyricsPlaceholder);
        lyricLines.clear();
        currentLyricIndex = -1;
    }

    private void renderLyrics(List<LrcParser.LrcLine> lines) {
        llLyricsContainer.removeAllViews();
        for (LrcParser.LrcLine line : lines) {
            TextView tv = new TextView(getActivity());
            tv.setText(line.text);
            tv.setTextSize(14f);
            tv.setTextColor(getResources().getColor(R.color.text_secondary));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(0, 8, 0, 8);
            llLyricsContainer.addView(tv);
        }
    }

    private void updateLyricHighlight(long currentPosition) {
        if (lyricLines == null || lyricLines.isEmpty()) return;
        if (llLyricsContainer == null || llLyricsContainer.getChildCount() == 0) return;

        // 找到当前应该高亮的行
        int newIndex = -1;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (lyricLines.get(i).timeMs <= currentPosition) {
                newIndex = i;
            } else {
                break;
            }
        }

        if (newIndex != currentLyricIndex && newIndex >= 0) {
            // 取消上一行的高亮
            if (currentLyricIndex >= 0 && currentLyricIndex < llLyricsContainer.getChildCount()) {
                TextView prevTv = (TextView) llLyricsContainer.getChildAt(currentLyricIndex);
                prevTv.setTextColor(getResources().getColor(R.color.text_secondary));
                prevTv.setTextSize(14f);
            }
            // 高亮当前行
            if (newIndex < llLyricsContainer.getChildCount()) {
                TextView currTv = (TextView) llLyricsContainer.getChildAt(newIndex);
                currTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorAccent));
                currTv.setTextSize(16f);

                // 滚动到中间
                final int scrollToIndex = newIndex;
                svLyrics.post(() -> {
                    if (svLyrics != null && llLyricsContainer != null) {
                        View child = llLyricsContainer.getChildAt(scrollToIndex);
                        if (child != null) {
                            int targetY = child.getTop() - svLyrics.getHeight() / 2 + child.getHeight() / 2;
                            svLyrics.smoothScrollTo(0, Math.max(0, targetY));
                        }
                    }
                });
            }
            currentLyricIndex = newIndex;
        }
    }

    // ========== 工具方法 ==========

    private String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
