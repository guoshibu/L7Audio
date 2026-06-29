package com.aug32.l7audio.ui.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
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

import com.aug32.l7audio.R;
import com.aug32.l7audio.domain.audio.AudioServiceLocator;
import com.aug32.l7audio.domain.audio.LrcParser;
import com.aug32.l7audio.domain.audio.MusicItem;
import com.aug32.l7audio.domain.audio.MusicPlayerManager;
import com.aug32.l7audio.domain.audio.PlaybackState;
import com.aug32.l7audio.domain.audio.playlist.ScannedMusicInfo;
import com.aug32.l7audio.ui.activity.MainActivity;
import com.aug32.l7audio.ui.adapter.MusicPlaylistAdapter;
import com.aug32.l7audio.utils.AppLog;
import com.aug32.l7audio.utils.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 音乐播放器 Fragment
 *
 * 职责：
 * - 只处理 UI 展示和用户交互
 * - 业务逻辑全部委托给 MusicPlayerManager
 * - 生命周期内正确注册/注销回调
 */
public class MusicPlayerFragment extends Fragment {

    private static final String TAG = "MusicPlayerFragment";

    // ========== UI 组件 ==========
    private TextView tvSongTitle;
    private TextView tvSongArtist;
    private ImageView ivAlbumArt;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private Button btnPlayPause;
    private Button btnPrev;
    private Button btnNext;
    private Button btnRepeat;
    private SeekBar sbVolume;
    private TextView tvVolumeLabel;
    private ScrollView svLyrics;
    private android.widget.LinearLayout llLyricsContainer;
    private TextView tvLyricsPlaceholder;
    private RecyclerView rvPlaylist;
    private Button btnScanMusic;
    private Button btnSelectFile;
    private Button btnSelectAll;
    private Button btnDelete;
    private Button btnCancel;
    private TextView tvPlaylistTitle;

    // ========== 数据 ==========
    private MusicPlayerManager musicPlayerManager;
    private MusicPlaylistAdapter playlistAdapter;
    private List<LrcParser.LrcLine> lyricLines = new ArrayList<>();
    private int currentLyricIndex = -1;
    private int lastScrollToIndex = -1;
    private boolean isUserSeeking = false;

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
                    if (!isAdded() || getActivity() == null) return;
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
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean granted = result.get(Manifest.permission.READ_MEDIA_AUDIO);
                if (granted != null && granted) {
                    scanMusic();
                } else {
                    Toast.makeText(getActivity(), "需要存储权限才能扫描音乐", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    handleFilePickerResult(result.getData());
                }
            });

    // ========== 生命周期 ==========

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music_player, container, false);
        initViews(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initPlayer();
        setupListeners();
        refreshPlaylist();
        updateUIWithCurrentState();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (musicPlayerManager != null) {
            musicPlayerManager.setCallback(playerCallback);
            updateUIWithCurrentState();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (musicPlayerManager != null) {
            musicPlayerManager.setCallback(null);
        }
    }

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
        btnScanMusic.setOnClickListener(v -> checkPermissionAndScan());

        // 添加音乐
        btnSelectFile.setOnClickListener(v -> openFilePicker());

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
        } else {
            tvSongTitle.setText("未选择歌曲");
            tvSongArtist.setText("");
        }
    }

    private void updateRepeatButton() {
        if (musicPlayerManager == null) return;
        int mode = musicPlayerManager.getRepeatMode();
        switch (mode) {
            case MusicPlayerManager.REPEAT_MODE_ALL:
                btnRepeat.setText("列表循环");
                break;
            case MusicPlayerManager.REPEAT_MODE_SHUFFLE:
                btnRepeat.setText("随机播放");
                break;
            case MusicPlayerManager.REPEAT_MODE_ONE:
                btnRepeat.setText("单曲循环");
                break;
            case MusicPlayerManager.REPEAT_MODE_OFF:
                btnRepeat.setText("不循环");
                break;
        }
    }

    private void cycleRepeatMode() {
        if (musicPlayerManager == null) return;
        int mode = musicPlayerManager.getRepeatMode();
        int nextMode = (mode + 1) % 4;
        musicPlayerManager.setRepeatMode(nextMode);
        updateRepeatButton();
        String[] modeNames = {"列表循环", "随机播放", "单曲循环", "不循环"};
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

    // ========== 扫描音乐 ==========

    private void checkPermissionAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireActivity(),
                    Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                scanMusic();
            } else {
                permissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_AUDIO});
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireActivity(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                scanMusic();
            } else {
                permissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
            }
        }
    }

    private void scanMusic() {
        if (musicPlayerManager == null) return;
        Toast.makeText(getActivity(), "正在扫描音乐...", Toast.LENGTH_SHORT).show();

        AppExecutors.getInstance().executeOnComputeThread(() -> {
            List<ScannedMusicInfo> scannedList = new ArrayList<>();

            try {
                Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

                String[] projection = {
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA
                };

                String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
                String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

                try (Cursor cursor = requireActivity().getContentResolver()
                        .query(collection, projection, selection, null, sortOrder)) {

                    if (cursor != null) {
                        int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                        int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                        int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                        int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                        int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                        int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idCol);
                            String title = cursor.getString(titleCol);
                            String artist = cursor.getString(artistCol);
                            String album = cursor.getString(albumCol);
                            long duration = cursor.getLong(durationCol);
                            String filePath = cursor.getString(dataCol);

                            Uri contentUri = ContentUris.withAppendedId(collection, id);

                            if (duration > 30000 && filePath != null) {
                                scannedList.add(new ScannedMusicInfo(
                                        filePath, contentUri.toString(), title, artist, album, duration));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to scan music", e);
            }

            final List<ScannedMusicInfo> result = scannedList;
            requireActivity().runOnUiThread(() -> {
                if (musicPlayerManager != null) {
                    musicPlayerManager.addScannedMusicItems(result,
                            new MusicPlayerManager.AddMusicCallback() {
                                @Override
                                public void onAddComplete(List<MusicItem> addedItems, int startPosition,
                                                         int skippedExistCount, int skippedFailedCount) {
                                    Toast.makeText(getActivity(),
                                            "添加了 " + addedItems.size() + " 首歌曲",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                }
            });
        });
    }

    // ========== 文件选择 ==========

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "选择音乐文件"));
        } catch (Exception e) {
            Toast.makeText(getActivity(), "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleFilePickerResult(Intent data) {
        if (musicPlayerManager == null) return;

        List<String> filePaths = new ArrayList<>();
        List<String> contentUris = new ArrayList<>();

        if (data.getClipData() != null) {
            // 多选
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                String path = getFilePathFromUri(uri);
                if (path != null) {
                    filePaths.add(path);
                    contentUris.add(uri.toString());
                }
            }
        } else if (data.getData() != null) {
            // 单选
            Uri uri = data.getData();
            String path = getFilePathFromUri(uri);
            if (path != null) {
                filePaths.add(path);
                contentUris.add(uri.toString());
            }
        }

        if (!filePaths.isEmpty()) {
            musicPlayerManager.addMusicFilesWithUris(filePaths, contentUris,
                    new MusicPlayerManager.AddMusicCallback() {
                        @Override
                        public void onAddComplete(List<MusicItem> addedItems, int startPosition,
                                                 int skippedExistCount, int skippedFailedCount) {
                            Toast.makeText(getActivity(),
                                    "添加了 " + addedItems.size() + " 首歌曲",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private String getFilePathFromUri(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }
        // 尝试从 MediaStore 查询
        try {
            Cursor cursor = requireActivity().getContentResolver().query(
                    uri, new String[]{MediaStore.Audio.Media.DATA}, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int col = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                        if (col >= 0) {
                            return cursor.getString(col);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            AppLog.d(TAG, "Failed to get file path from uri");
        }
        return null;
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
