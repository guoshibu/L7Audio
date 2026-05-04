package com.aug32.l7audio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.common.Player;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.aug32.l7audio.audio.AudioOutputManager;
import com.aug32.l7audio.audio.MusicPlayerManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import android.os.Environment;

public class MusicPlayerFragment extends Fragment {
    private static final String TAG = "MusicPlayerFragment";
    
    private static final int LOOP_MODE_ALL = 0;
    private static final int LOOP_MODE_SHUFFLE = 1;
    private static final int LOOP_MODE_ONE = 2;
    private static final int LOOP_MODE_OFF = 3;
    
    private int currentLoopMode = LOOP_MODE_OFF;// 当前循环模式

    private ActivityResultLauncher<Intent> selectFileLauncher;// 选择文件ActivityResultLauncher

    private TextView tvSongTitle;// 歌曲标题
    private TextView tvSongArtist;// 歌手
    private TextView tvCurrentTime;// 当前时间
    private TextView tvTotalTime;// 总时间
    private TextView tvPlaylistTitle;// 播放列表标题
    private SeekBar seekBar;// 播放进度条
    private SeekBar sbVolume;// 音量条条
    private Button btnPrev;// 上一首曲
    private Button btnPlayPause;// 播放/暂停
    private Button btnNext;// 下一首曲
    private Button btnRepeat;// 循环播放
    private Button btnSelectFile;// 选择文件
    private Button btnDelete;// 删除
    private Button btnCancel;// 取消
    private Button btnSelectAll;// 全选
    private RecyclerView rvPlaylist;// 播放列表RecyclerView
    private ImageView ivAlbumArt;// 专辑图片

    private MusicPlayerManager musicPlayerManager;// 音乐播放器管理器
    private AppConfig appConfig;// 应用配置
    private MusicPlaylistAdapter playlistAdapter;// 播放列表适配器
    private boolean isUserSeeking = false;// 是否用户正在拖动进度条

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 检查屏幕宽高比，决定使用哪种布局
        checkScreenAspectRatio();
        
        // 让系统根据方向自动选择布局
        View view = inflater.inflate(R.layout.fragment_music_player, container, false);

        initActivityResultLaunchers();// 初始化文件选择ActivityResultLauncher
        initViews(view);// 初始化视图组件
        initManagers();// 初始化音乐播放器管理器
        setupListeners();// 设置事件监听器
        setupPlaylist();// 设置播放列表
        updateUI();// 更新UI

        return view;
    }

    /**
     * 检查屏幕分辨率，当分辨率为2880×1080时，强制使用横屏布局
     */
    private void checkScreenAspectRatio() {
        if (getActivity() != null) {
            // 获取屏幕分辨率
            android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;
            int screenHeight = displayMetrics.heightPixels;

            AppLog.d(TAG, "Screen resolution: " + screenWidth + "x" + screenHeight);

            // 当分辨率为2880×1080时，强制设置为横屏方向
            if ((screenWidth == 2880 && screenHeight == 1080) || (screenWidth == 1080 && screenHeight == 2880)) {
                AppLog.d(TAG, "Forcing landscape orientation for 2880×1080 resolution");
                getActivity().setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                // 否则使用传感器自动检测方向
                AppLog.d(TAG, "Using sensor-based orientation");
                getActivity().setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            }
        }
    }

    private void initActivityResultLaunchers() {
        // 初始化文件选择ActivityResultLauncher
        selectFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                            Intent data = result.getData();
                            if (data != null) {
                                List<String> filePaths = new ArrayList<>();
                                if (data.getClipData() != null) {
                                    int count = data.getClipData().getItemCount();
                                    for (int i = 0; i < count; i++) {
                                        Uri uri = data.getClipData().getItemAt(i).getUri();
                                        String filePath = getRealPathFromURI(uri);
                                        if (filePath != null) {
                                            filePaths.add(filePath);
                                        }
                                    }
                                } else if (data.getData() != null) {
                                    String filePath = getRealPathFromURI(data.getData());
                                    if (filePath != null) {
                                        filePaths.add(filePath);
                                    }
                                }

                                if (!filePaths.isEmpty()) {
                                    musicPlayerManager.addMusicFiles(filePaths, new MusicPlayerManager.AddMusicCallback() {
                                        @Override
                                        public void onAddComplete(int addedCount) {
                                            requireActivity().runOnUiThread(() -> {
                                                // 强制更新播放列表
                                                playlistAdapter.setMusicItems(musicPlayerManager.getMusicItems());
                                                playlistAdapter.notifyDataSetChanged();
                                                
                                                // 根据实际添加的数量显示Toast
                                                if (addedCount > 0) {
                                                    Toast.makeText(getActivity(), "已添加 " + addedCount + " 首音乐", Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(getActivity(), "没有添加新音乐，所有文件已存在", Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
        );
    }

    private Button btnScanMusic;// 扫描音乐按钮

    private void initViews(View view) {
        // 初始化视图组件
        tvSongTitle = view.findViewById(R.id.tv_song_title);// 当前播放的歌曲标题
        tvSongArtist = view.findViewById(R.id.tv_song_artist); // 当前播放的歌曲歌手
        tvCurrentTime = view.findViewById(R.id.tv_current_time);// 当前播放时间
        tvTotalTime = view.findViewById(R.id.tv_total_time);// 总播放时间
        tvPlaylistTitle = view.findViewById(R.id.tv_playlist_title);// 播放列表标题
        seekBar = view.findViewById(R.id.seek_bar);// 播放进度条
        sbVolume = view.findViewById(R.id.sb_volume);// 音量条
        btnPrev = view.findViewById(R.id.btn_prev);// 上一首按钮
        btnPlayPause = view.findViewById(R.id.btn_play_pause);// 播放/暂停按钮
        btnNext = view.findViewById(R.id.btn_next);// 下一首按钮
        btnRepeat = view.findViewById(R.id.btn_repeat);// 循环播放按钮
        btnScanMusic = view.findViewById(R.id.btn_scan_music);// 扫描音乐按钮
        btnSelectFile = view.findViewById(R.id.btn_select_file);// 选择文件按钮
        btnDelete = view.findViewById(R.id.btn_delete);// 删除按钮
        btnCancel = view.findViewById(R.id.btn_cancel);// 取消按钮
        btnSelectAll = view.findViewById(R.id.btn_select_all);// 全选按钮
        rvPlaylist = view.findViewById(R.id.rv_playlist);// 播放列表RecyclerView
        ivAlbumArt = view.findViewById(R.id.iv_album_art);// 专辑图片
    }

    private void initManagers() {
        // 初始化音乐播放器管理器
        appConfig = new AppConfig(requireContext());// 应用配置

        if (getActivity() != null) {
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                musicPlayerManager = mainActivity.getMusicPlayerManager();
            }
        }

        if (musicPlayerManager == null) {
            AudioOutputManager audioOutputManager = null;
            if (getActivity() != null) {
                MainActivity mainActivity = MainActivity.getInstance();
                if (mainActivity != null) {
                    audioOutputManager = mainActivity.getAudioOutputManager();
                }
            }
            musicPlayerManager = new MusicPlayerManager(requireContext(), audioOutputManager);
        }

        musicPlayerManager.setCallback(new MusicPlayerManager.MusicPlayerCallback() {
            @Override
            public void onPlaybackStarted(int position) {
                // 重新加载音乐项列表，确保显示更新后的元数据
                playlistAdapter.setMusicItems(musicPlayerManager.getMusicItems());
                playlistAdapter.setCurrentPlayingIndex(position);
                updateUI();
            }

            @Override
            public void onPlaybackPaused() {
                updateUI();
            }

            @Override
            public void onPlaybackStopped() {
                updateUI();
                playlistAdapter.setCurrentPlayingIndex(-1);
            }

            @Override
            public void onPlaybackCompleted() {
            }

            @Override
            public void onPlaybackProgress(long position, long duration) {
                if (!isUserSeeking) {
                    seekBar.setProgress((int) position);
                    seekBar.setMax((int) duration);
                    tvCurrentTime.setText(formatTime(position));
                    tvTotalTime.setText(formatTime(duration));
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(getActivity(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupPlaylist() {// 设置播放列表
        playlistAdapter = new MusicPlaylistAdapter();
        rvPlaylist.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvPlaylist.setAdapter(playlistAdapter);

        playlistAdapter.setMusicItems(musicPlayerManager.getMusicItems());
        playlistAdapter.setCurrentPlayingIndex(musicPlayerManager.getCurrentIndex());

        /**
         * 播放列表项点击监听器
         * 
         * 方法链路：
         * MusicPlaylistAdapter 项点击 → onItemClickListener
         * → MusicPlayerManager.start(position)
         * → [核心策略：先播放，后更新音频属性]
         */
        playlistAdapter.setOnItemClickListener(position -> {
            AppLog.d(TAG, "=== METHOD CHAIN: playlist item click → onItemClickListener ===");
            if (!playlistAdapter.isSelectionMode()) {
                AppLog.d(TAG, "Calling musicPlayerManager.start(" + position + ")");
                musicPlayerManager.start(position);
            }
        });

        playlistAdapter.setOnItemLongClickListener(position -> {
            if (!playlistAdapter.isSelectionMode()) {
                enterSelectionMode();
                playlistAdapter.toggleSelection(position);
            }
        });

        playlistAdapter.setOnSelectionChangedListener(selectedCount -> {
            tvPlaylistTitle.setText(selectedCount > 0 ? "已选择 " + selectedCount + " 首" : "播放列表");
            if (playlistAdapter.isAllSelected()) {
                btnSelectAll.setText("取消全选");
            } else {
                btnSelectAll.setText("全选");
            }
        });
    }

    private void setupListeners() {// 设置事件监听器
        btnPrev.setOnClickListener(v -> playPrevious());// 上一首按钮点击事件
        btnPlayPause.setOnClickListener(v -> togglePlayPause());// 播放/暂停按钮点击事件
        btnNext.setOnClickListener(v -> playNext());// 下一首按钮点击事件
        btnRepeat.setOnClickListener(v -> toggleRepeatMode());// 循环播放按钮点击事件
        btnScanMusic.setOnClickListener(v -> scanMusicDirectory());// 扫描音乐按钮点击事件
        btnSelectFile.setOnClickListener(v -> selectMusicFiles());// 选择文件按钮点击事件
        btnDelete.setOnClickListener(v -> deleteSelectedItems());// 删除按钮点击事件
        btnCancel.setOnClickListener(v -> exitSelectionMode());// 取消按钮点击事件
        btnSelectAll.setOnClickListener(v -> toggleSelectAll());// 全选按钮点击事件

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int userSelectedPosition = 0;
            
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(formatTime(progress));
                    userSelectedPosition = progress;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
                userSelectedPosition = seekBar.getProgress();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                int progress = seekBar.getProgress();
                userSelectedPosition = progress;
                
                // 先尝试seekTo
                musicPlayerManager.seekTo(progress);
                
                // 如果音乐未播放，自动开始播放
                if (!musicPlayerManager.isPlaying() && musicPlayerManager.getCurrentIndex() >= 0) {
                    // 使用带用户选择位置的resume方法，避免UI闪烁
                    musicPlayerManager.resume(progress);
                }
            }
        });

        sbVolume.setMax(100);
        int currentVolume = getCurrentVolume();
        sbVolume.setProgress(currentVolume);
        sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    setVolume(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    /**
     * 播放上一首
     * 
     * 方法链路：
     * MusicPlayerFragment.btnPrev 点击 → playPrevious()
     * → MusicPlayerManager.playPrevious()
     * → MusicPlayerManager.start(prevIndex)
     * → [核心策略：先播放，后更新音频属性]
     */
    private void playPrevious() {
        AppLog.d(TAG, "=== METHOD CHAIN: btnPrev click → playPrevious() ===");
        if (musicPlayerManager != null) {
            AppLog.d(TAG, "Calling musicPlayerManager.playPrevious()");
            musicPlayerManager.playPrevious();
        }
    }

    /**
     * 切换播放/暂停状态
     * 
     * 方法链路（根据当前状态分三种情况）：
     * 
     * 情况1：当前正在播放 → 暂停
     * MusicPlayerFragment.btnPlayPause 点击 → togglePlayPause()
     * → MusicPlayerManager.pause()
     * 
     * 情况2：当前暂停，有当前播放索引 → 恢复播放
     * MusicPlayerFragment.btnPlayPause 点击 → togglePlayPause()
     * → MusicPlayerManager.resume()
     * → [核心策略：先播放，后更新音频属性]
     * 
     * 情况3：当前无播放索引 → 提示用户选择曲目
     * MusicPlayerFragment.btnPlayPause 点击 → togglePlayPause()
     * → 显示提示信息，等待用户选择具体曲目
     */
    private void togglePlayPause() {
        AppLog.d(TAG, "=== METHOD CHAIN: btnPlayPause click → togglePlayPause() ===");
        if (musicPlayerManager != null) {
            if (musicPlayerManager.isPlaying()) {
                AppLog.d(TAG, "Currently playing, calling musicPlayerManager.pause()");
                musicPlayerManager.pause();
            } else {
                if (musicPlayerManager.getCurrentIndex() >= 0) {
                    AppLog.d(TAG, "Has current index, calling musicPlayerManager.resume()");
                    musicPlayerManager.resume();
                } else if (musicPlayerManager.getMusicItemCount() > 0) {
                    AppLog.d(TAG, "No current index but has playlist, showing select song prompt");
                    // 显示提示信息，等待用户选择具体曲目
                    Toast.makeText(getActivity(), "请选择要播放的曲目", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), "请先添加音乐文件", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * 播放下一首
     * 
     * 方法链路：
     * MusicPlayerFragment.btnNext 点击 → playNext()
     * → MusicPlayerManager.playNext()
     * → MusicPlayerManager.start(nextIndex)
     * → [核心策略：先播放，后更新音频属性]
     */
    private void playNext() {
        AppLog.d(TAG, "=== METHOD CHAIN: btnNext click → playNext() ===");
        if (musicPlayerManager != null) {
            AppLog.d(TAG, "Calling musicPlayerManager.playNext()");
            musicPlayerManager.playNext();
        }
    }

    private void toggleRepeatMode() {
        if (musicPlayerManager != null) {
            currentLoopMode = (currentLoopMode + 1) % 4;
            applyLoopMode();
            updateRepeatButton();
        }
    }

    private void applyLoopMode() {
        switch (currentLoopMode) {
            case LOOP_MODE_ALL:
                musicPlayerManager.setRepeatMode(Player.REPEAT_MODE_ALL);
                musicPlayerManager.setShuffleModeEnabled(false);
                break;
            case LOOP_MODE_SHUFFLE:
                musicPlayerManager.setRepeatMode(Player.REPEAT_MODE_ALL);
                musicPlayerManager.setShuffleModeEnabled(true);
                break;
            case LOOP_MODE_ONE:
                musicPlayerManager.setRepeatMode(Player.REPEAT_MODE_ONE);
                musicPlayerManager.setShuffleModeEnabled(false);
                break;
            case LOOP_MODE_OFF:
            default:
                musicPlayerManager.setRepeatMode(Player.REPEAT_MODE_OFF);
                musicPlayerManager.setShuffleModeEnabled(false);
                break;
        }
    }

    private void updateRepeatButton() {
        switch (currentLoopMode) {
            case LOOP_MODE_OFF:
                btnRepeat.setText("单曲播放");
                btnRepeat.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.button_background));
                break;
            case LOOP_MODE_ALL:
                btnRepeat.setText("全部循环");
                btnRepeat.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.button_background_selected));
                break;
            case LOOP_MODE_SHUFFLE:
                btnRepeat.setText("随机循环");
                btnRepeat.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.button_background_selected));
                break;
            case LOOP_MODE_ONE:
                btnRepeat.setText("单曲循环");
                btnRepeat.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.status_recording));
                break;
        }
    }

    private void selectMusicFiles() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            selectFileLauncher.launch(Intent.createChooser(intent, "选择音乐文件"));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getActivity(), "请安装文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 使用MediaStore API扫描设备上的所有音乐文件
     */
    private void scanMusicDirectory() {
        AppLog.d(TAG, "=== 开始使用MediaStore扫描音乐 ===");
        Toast.makeText(getActivity(), "开始扫描音乐...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            List<String> musicFiles = new ArrayList<>();
            
            if (getActivity() == null) return;
            
            try {
                // 使用MediaStore查询所有存储卷（包括USB）的音乐文件
                String[] projection = {
                    android.provider.MediaStore.Audio.Media._ID,
                    android.provider.MediaStore.Audio.Media.DATA,
                    android.provider.MediaStore.Audio.Media.TITLE,
                    android.provider.MediaStore.Audio.Media.ARTIST,
                    android.provider.MediaStore.Audio.Media.ALBUM,
                    android.provider.MediaStore.Audio.Media.DURATION
                };

                String selection = android.provider.MediaStore.Audio.Media.IS_MUSIC + " != 0";
                String sortOrder = android.provider.MediaStore.Audio.Media.TITLE + " ASC";

                // 尝试获取所有存储卷
                android.database.Cursor cursor = null;
                try {
                    // 先尝试扫描默认的外部存储
                    cursor = getActivity().getContentResolver().query(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        null,
                        sortOrder
                    );

                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            String filePath = cursor.getString(
                                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                            );
                            String title = cursor.getString(
                                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                            );
                            String artist = cursor.getString(
                                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                            );

                            File file = new File(filePath);
                            if (file.exists() && isMusicFile(file)) {
                                // 检查是否已经在列表中（去重）
                                String absolutePath;
                                try {
                                    absolutePath = file.getCanonicalPath();
                                } catch (java.io.IOException e) {
                                    absolutePath = file.getAbsolutePath();
                                }
                                
                                boolean alreadyExists = false;
                                for (String existingPath : musicFiles) {
                                    try {
                                        File existingFile = new File(existingPath);
                                        if (existingFile.getCanonicalPath().equals(absolutePath)) {
                                            alreadyExists = true;
                                            break;
                                        }
                                    } catch (java.io.IOException e) {
                                        if (existingPath.equals(absolutePath)) {
                                            alreadyExists = true;
                                            break;
                                        }
                                    }
                                }
                                
                                if (!alreadyExists) {
                                    musicFiles.add(filePath);
                                    AppLog.d(TAG, "找到音乐: " + title + " - " + artist + " (" + filePath + ")");
                                }
                            }
                        }
                        cursor.close();
                    }
                } catch (Exception e) {
                    AppLog.e(TAG, "扫描默认存储失败", e);
                }

                AppLog.d(TAG, "MediaStore扫描完成，找到 " + musicFiles.size() + " 首音乐");

            } catch (Exception e) {
                AppLog.e(TAG, "使用MediaStore扫描失败，回退到目录扫描", e);
            }

            // 即使MediaStore成功，也尝试使用回退方案扫描USB存储
            scanMusicFallback(musicFiles);

            // 处理扫描结果
            final int foundCount = musicFiles.size();

            requireActivity().runOnUiThread(() -> {
                if (foundCount > 0) {
                    // 添加找到的音乐文件
                    musicPlayerManager.addMusicFiles(musicFiles, new MusicPlayerManager.AddMusicCallback() {
                        @Override
                        public void onAddComplete(int addedCount) {
                            requireActivity().runOnUiThread(() -> {
                                // 强制更新播放列表
                                playlistAdapter.setMusicItems(musicPlayerManager.getMusicItems());
                                playlistAdapter.notifyDataSetChanged();
                                
                                // 根据实际添加的数量显示Toast
                                if (addedCount > 0) {
                                    Toast.makeText(getActivity(), "已扫描到 " + addedCount + " 首音乐", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getActivity(), "没有添加新音乐，所有文件已存在", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                } else {
                    Toast.makeText(getActivity(), "未找到音乐文件", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    /**
     * 传统的目录扫描方法（作为MediaStore的回退方案）
     */
    private void scanMusicFallback(List<String> musicFiles) {
        AppLog.d(TAG, "使用回退方案扫描音乐目录");
        
        // 扫描常见的音乐目录 + USB挂载点
        List<String> musicDirectories = new ArrayList<>();
        
        // 添加常见内部存储目录
        musicDirectories.add("/storage/emulated/0/Music/");
        musicDirectories.add(Environment.getExternalStorageDirectory().getPath() + "/Music/");
        musicDirectories.add("/storage/emulated/0/Download/Music/");
        musicDirectories.add(Environment.getExternalStorageDirectory().getPath() + "/Download/Music/");
        musicDirectories.add("/storage/emulated/0/Download/");
        
        // 添加USB挂载点
        musicDirectories.add("/storage/usb0/");
        musicDirectories.add("/storage/usb1/");
        musicDirectories.add("/mnt/usb/");
        musicDirectories.add("/mnt/media_rw/");
        musicDirectories.add("/storage/"); // 扫描整个storage目录，可能会找到UUID格式的挂载点
        
        // 扫描所有目录
        for (String directoryPath : musicDirectories) {
            File directory = new File(directoryPath);
            if (directory.exists() && directory.isDirectory() && !hasNomediaFile(directory)) {
                AppLog.d(TAG, "扫描目录: " + directoryPath);
                scanDirectory(directory, musicFiles);
            } else if (hasNomediaFile(directory)) {
                AppLog.d(TAG, "跳过目录（存在.nomedia）: " + directoryPath);
            }
        }

        AppLog.d(TAG, "回退扫描完成，找到 " + musicFiles.size() + " 首音乐");
    }

    /**
     * 检查目录下是否存在.nomedia文件
     */
    private boolean hasNomediaFile(File directory) {
        File nomediaFile = new File(directory, ".nomedia");
        return nomediaFile.exists();
    }


    /**
     * 递归扫描目录，查找音频文件
     */
    private void scanDirectory(File directory, List<String> musicFiles) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归扫描子目录
                scanDirectory(file, musicFiles);
            } else if (isMusicFile(file)) {
                // 检查是否已经在列表中（去重）
                String filePath = file.getAbsolutePath();
                boolean alreadyExists = false;
                for (String existingPath : musicFiles) {
                    // 比较绝对路径，避免重复
                    try {
                        File existingFile = new File(existingPath);
                        File newFile = new File(filePath);
                        if (existingFile.getCanonicalPath().equals(newFile.getCanonicalPath())) {
                            alreadyExists = true;
                            break;
                        }
                    } catch (java.io.IOException e) {
                        // 如果获取canonicalPath失败，用absolutePath比较
                        if (existingPath.equals(filePath)) {
                            alreadyExists = true;
                            break;
                        }
                    }
                }
                
                if (!alreadyExists) {
                    // 添加音频文件
                    musicFiles.add(filePath);
                    AppLog.d(TAG, "找到音乐文件: " + filePath);
                }
            }
        }
    }

    /**
     * 检查文件是否为音频文件
     */
    private boolean isMusicFile(File file) {
        String fileName = file.getName().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".mp3") || 
               fileName.endsWith(".wav") || 
               fileName.endsWith(".flac") || 
               fileName.endsWith(".ogg") || 
               fileName.endsWith(".aac") || 
               fileName.endsWith(".m4a") ||
               fileName.endsWith(".wma") ||
               fileName.endsWith(".amr");
    }

    private String getRealPathFromURI(Uri uri) {
        String path = null;
        if (getActivity() != null) {
            if (DocumentsContract.isDocumentUri(getActivity(), uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                    String[] split = docId.split(":");
                    String type = split[0];
                    if ("primary".equalsIgnoreCase(type)) {
                        path = "storage/emulated/0/" + split[1];
                    }
                }
            } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                try {
                    path = getDataColumn(uri, null, null);
                } catch (Exception e) {
                    AppLog.e(TAG, "Error getting data column", e);
                }
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                path = uri.getPath();
            }
        }
        return path;
    }

    private String getDataColumn(Uri uri, String selection, String[] selectionArgs) {
        String column = "_data";
        String[] projection = {column};
        try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error querying content resolver", e);
        }
        return null;
    }

    private void enterSelectionMode() {
        playlistAdapter.setSelectionMode(true);
        btnDelete.setVisibility(View.VISIBLE);
        btnCancel.setVisibility(View.VISIBLE);
        btnSelectAll.setVisibility(View.VISIBLE);
        btnSelectFile.setVisibility(View.GONE);
    }

    private void exitSelectionMode() {
        playlistAdapter.setSelectionMode(false);
        btnDelete.setVisibility(View.GONE);
        btnCancel.setVisibility(View.GONE);
        btnSelectAll.setVisibility(View.GONE);
        btnSelectFile.setVisibility(View.VISIBLE);
        tvPlaylistTitle.setText("播放列表");
    }

    private void toggleSelectAll() {
        if (playlistAdapter.isAllSelected()) {
            playlistAdapter.clearSelection();
        } else {
            playlistAdapter.selectAll();
        }
    }

    private void deleteSelectedItems() {
        List<Integer> selectedItems = playlistAdapter.getSelectedItems();
        if (!selectedItems.isEmpty()) {
            musicPlayerManager.removeMusicItems(selectedItems);
            playlistAdapter.setMusicItems(musicPlayerManager.getMusicItems());
            exitSelectionMode();
            Toast.makeText(getActivity(), "已删除 " + selectedItems.size() + " 首音乐", Toast.LENGTH_SHORT).show();
        }
    }

    private int getCurrentVolume() {
        if (getActivity() != null) {
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                AudioOutputManager audioOutputManager = mainActivity.getAudioOutputManager();
                if (audioOutputManager != null) {
                    if (audioOutputManager.getOutputMode() == AudioOutputManager.OUTPUT_CAR) {
                        return appConfig.getCarVolume();
                    } else {
                        return appConfig.getExternalVolume();
                    }
                }
            }
        }
        return appConfig.getCarVolume();
    }

    private void setVolume(int volume) {
        if (musicPlayerManager != null) {
            musicPlayerManager.setVolume(volume);
        }
        if (getActivity() != null) {
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                AudioOutputManager audioOutputManager = mainActivity.getAudioOutputManager();
                if (audioOutputManager != null) {
                    if (audioOutputManager.getOutputMode() == AudioOutputManager.OUTPUT_CAR) {
                        appConfig.setCarVolume(volume);
                    } else {
                        appConfig.setExternalVolume(volume);
                    }
                }
            }
        }
    }

    private void updateUI() {
        if (musicPlayerManager != null) {
            MusicPlayerManager.MusicItem currentItem = musicPlayerManager.getCurrentMusicItem();
            if (currentItem != null) {
                tvSongTitle.setText(currentItem.title);
                tvSongArtist.setText(currentItem.artist);
                // 更新专辑图片
                updateAlbumArt();
            } else {
                tvSongTitle.setText("未选择歌曲");
                tvSongArtist.setText("");
                // 显示默认专辑图片
                if (ivAlbumArt != null) {
                    ivAlbumArt.setImageResource(R.drawable.ic_launcher_playstore);
                }
            }

            if (musicPlayerManager.isPlaying()) {
                btnPlayPause.setText("暂停");
            } else {
                btnPlayPause.setText("播放");
            }

            // 更新进度条和时间显示
            if (currentItem != null) {
                long currentPosition = musicPlayerManager.getCurrentPosition();
                long duration = musicPlayerManager.getDuration();
                
                // 如果播放器未初始化但有保存的播放位置，使用保存的位置
                if (currentPosition == 0 && !musicPlayerManager.isExoPlayerInitialized()) {
                    currentPosition = musicPlayerManager.getLastPlayedPosition();
                    // 使用音乐项的duration作为总时长
                    duration = currentItem.duration;
                }
                
                // 确保duration大于0，即使播放器未初始化
                if (duration <= 0) {
                    duration = currentItem.duration;
                }
                
                if (duration > 0) {
                    seekBar.setMax((int) duration);
                    seekBar.setProgress((int) currentPosition);
                    tvCurrentTime.setText(formatTime(currentPosition));
                    tvTotalTime.setText(formatTime(duration));
                }
            }

            initLoopModeFromPlayer();
            updateRepeatButton();
        }
    }
    
    private void updateAlbumArt() {
        // 这里可以实现从音乐文件中提取专辑图片的逻辑
        if (ivAlbumArt != null) {
            if (musicPlayerManager != null) {
                MusicPlayerManager.MusicItem currentItem = musicPlayerManager.getCurrentMusicItem();
                if (currentItem != null) {
                    // 在后台线程中提取专辑封面，避免阻塞UI线程
                    new Thread(() -> {
                        android.graphics.Bitmap albumArt = musicPlayerManager.extractAlbumArt(currentItem.filePath);
                        // 检查Fragment是否仍然附加到Activity上
                        if (isAdded() && getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                // 再次检查Fragment是否仍然附加到Activity上
                                if (isAdded() && ivAlbumArt != null) {
                                    if (albumArt != null) {
                                        ivAlbumArt.setImageBitmap(albumArt);
                                    } else {
                                        // 如果没有提取到专辑封面，使用默认图片
                                        ivAlbumArt.setImageResource(R.drawable.ic_launcher_playstore);
                                    }
                                }
                            });
                        }
                    }).start();
                } else {
                    // 如果没有当前播放项，使用默认图片
                    ivAlbumArt.setImageResource(R.drawable.ic_launcher_playstore);
                }
            } else {
                // 如果音乐播放器管理器为空，使用默认图片
                ivAlbumArt.setImageResource(R.drawable.ic_launcher_playstore);
            }
        }
    }
    
    private void initLoopModeFromPlayer() {
        if (musicPlayerManager.isShuffleModeEnabled()) {
            currentLoopMode = LOOP_MODE_SHUFFLE;
        } else {
            int repeatMode = musicPlayerManager.getRepeatMode();
            switch (repeatMode) {
                case Player.REPEAT_MODE_ALL:
                    currentLoopMode = LOOP_MODE_ALL;
                    break;
                case Player.REPEAT_MODE_ONE:
                    currentLoopMode = LOOP_MODE_ONE;
                    break;
                case Player.REPEAT_MODE_OFF:
                default:
                    currentLoopMode = LOOP_MODE_OFF;
                    break;
            }
        }
    }

    private String formatTime(long milliseconds) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (musicPlayerManager != null) {
            // 同步播放列表和当前索引
            playlistAdapter.setMusicItems(musicPlayerManager.getMusicItems());
            playlistAdapter.setCurrentPlayingIndex(musicPlayerManager.getCurrentIndex());
            // 强制更新UI状态，确保与播放器实际状态同步
            updateUI();
            // 确保isPlaying状态与实际播放器状态一致
            if (musicPlayerManager.isPlaying() && !musicPlayerManager.isExoPlayerInitialized()) {
                // 如果isPlaying为true但播放器未初始化，重置状态
                musicPlayerManager.resetPlayingState();
                updateUI();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // 当切换到其他模块时暂停播放，应用进入后台时继续播放
        // 注意：Fragment的onPause会在MainActivity的onPause之前调用，此时isInBackground还未更新
        // 因此我们需要检查当前是否真的是切换模块，而不是应用进入后台
        // 这里暂时不暂停播放，让音乐在后台继续播放
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 不要调用stop()，因为这会影响整个应用的音乐播放
        // 只有在Fragment自己创建的播放器实例时才需要停止
        // if (musicPlayerManager != null) {
        //     musicPlayerManager.stop();
        // }
    }
}
