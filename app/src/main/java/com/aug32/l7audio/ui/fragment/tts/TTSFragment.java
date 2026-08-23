package com.aug32.l7audio.ui.fragment.tts;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aug32.l7audio.base.BaseFragment;
import com.aug32.l7audio.data.local.config.floating.FloatingWindowConfig;
import com.aug32.l7audio.data.model.TTSItem;
import com.aug32.l7audio.domain.audio.AudioServiceLocator;
import com.aug32.l7audio.domain.audio.AudioVisualizerView;
import com.aug32.l7audio.domain.audio.tts.TTSManager;
import com.aug32.l7audio.R;
import com.aug32.l7audio.ui.viewmodel.TTSViewModel;
import com.aug32.l7audio.utils.AppLog;

public class TTSFragment extends BaseFragment {

    private static final String TAG = "TTSFragment";
    private static final int MAX_ITEMS = 20;
    private static final int MAX_TEXT_LENGTH = 100;

    private LinearLayout ttsItemsContainer;
    private EditText ttsInput;
    private Button btnAddTTS;
    private TTSManager ttsManager;
    /** ViewModel 数据直接赋值，ttsItems 始终持有最新副本 */
    private List<TTSItem> ttsItems;
    private List<AudioVisualizerView> visualizerViews;
    private int currentlyPlayingPosition = -1;
    private TTSViewModel viewModel;
    /** 是否有待处理的悬浮窗编辑器打开请求（防止 LiveData 多次发射导致请求丢失） */
    private boolean pendingFloatingEditor = false;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_tts;
    }

    @Override
    protected void initViews(View view) {
        ttsItemsContainer = view.findViewById(R.id.tts_items_container);
        ttsInput = view.findViewById(R.id.tts_input);
        btnAddTTS = view.findViewById(R.id.btn_add_tts);
        Button btnEditFloatingList = view.findViewById(R.id.btn_edit_floating_list);

        AudioServiceLocator locator = AudioServiceLocator.getInstance();
        locator.init(requireContext());
        ttsManager = locator.getTTSManager();
        if (ttsManager != null) {
            ttsManager.setProgressListener(new TTSManager.TTSProgressListener() {
                @Override
                public void onTTSStart() {
                }

                @Override
                public void onTTSDone() {
                    if (getSafeActivity() != null) {
                        getSafeActivity().runOnUiThread(() -> {
                            if (currentlyPlayingPosition >= 0 && currentlyPlayingPosition < ttsItems.size()) {
                                ttsItems.get(currentlyPlayingPosition).isPlaying = false;
                                currentlyPlayingPosition = -1;
                                refreshTTSItemsView();
                            }
                        });
                    }
                }

                @Override
                public void onTTSError() {
                    if (getSafeActivity() != null) {
                        getSafeActivity().runOnUiThread(() -> {
                            if (currentlyPlayingPosition >= 0 && currentlyPlayingPosition < ttsItems.size()) {
                                ttsItems.get(currentlyPlayingPosition).isPlaying = false;
                                currentlyPlayingPosition = -1;
                                refreshTTSItemsView();
                            }
                        });
                    }
                }
            });
        }

        ttsItems = new ArrayList<>();
        visualizerViews = new ArrayList<>();

        if (btnAddTTS != null) {
            btnAddTTS.setOnClickListener(v -> addTTSItem());
        }

        if (btnEditFloatingList != null) {
            btnEditFloatingList.setOnClickListener(v -> showFloatingListEditor());
        }
    }

    @Override
    protected void initData() {
        viewModel = new ViewModelProvider(this).get(TTSViewModel.class);

        // 在观察前先检查并记录悬浮窗编辑器打开请求
        // 避免 LiveData 多次发射时请求被提前消费
        if (getActivity() != null && getActivity().getIntent() != null
                && getActivity().getIntent().getBooleanExtra("open_floating_editor", false)) {
            pendingFloatingEditor = true;
            getActivity().getIntent().removeExtra("open_floating_editor");
        }

        viewModel.getTTSItems().observe(getViewLifecycleOwner(), items -> {
            if (items != null) {
                ttsItems = items;
                refreshTTSItemsView();

                // 数据加载完成且有悬浮窗编辑器打开请求时，弹出编辑对话框
                if (pendingFloatingEditor && !items.isEmpty()) {
                    pendingFloatingEditor = false;
                    if (getView() != null) {
                        getView().post(() -> showFloatingListEditor());
                    }
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ttsManager != null) {
            ttsManager.setProgressListener(null);
        }
        ttsItemsContainer = null;
        ttsInput = null;
        btnAddTTS = null;
        ttsItems = null;
        visualizerViews = null;
        viewModel = null;
        currentlyPlayingPosition = -1;
    }

    @Override
    protected void initListeners() {
    }

    private void addTTSItem() {
        if (ttsInput != null) {
            String text = ttsInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(getSafeContext(), "请输入文本", Toast.LENGTH_SHORT).show();
                return;
            }
            if (text.length() > MAX_TEXT_LENGTH) {
                Toast.makeText(getSafeContext(), "文本不能超过100个字符", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ttsItems.size() >= MAX_ITEMS) {
                Toast.makeText(getSafeContext(), "最多只能添加" + MAX_ITEMS + "条文本", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.addTTSItem(text);
            ttsInput.setText("");
            Toast.makeText(getSafeContext(), "添加成功", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteTTSItem(int position) {
        if (position >= 0 && position < ttsItems.size()) {
            if (ttsItems.size() == 1) {
                if (currentlyPlayingPosition >= 0) {
                    stopTTS();
                    currentlyPlayingPosition = -1;
                }
                viewModel.restoreDefaultTTSItems();
                Toast.makeText(getSafeContext(), "已恢复默认列表", Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentlyPlayingPosition == position) {
                stopTTS();
                currentlyPlayingPosition = -1;
            } else if (currentlyPlayingPosition > position) {
                currentlyPlayingPosition--;
            }
            viewModel.removeTTSItem(position);
            Toast.makeText(getSafeContext(), "已删除", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshTTSItemsView() {
        if (ttsItemsContainer == null) return;
        // 复用统一的停止逻辑，避免重复遍历代码
        stopAllVisualizers();
        ttsItemsContainer.removeAllViews();
        visualizerViews.clear();
        for (int i = 0; i < ttsItems.size(); i++) {
            addTTSItemToView(ttsItems.get(i), i);
        }
    }

    /**
     * 停止所有可视化动画。
     * <p>由 {@link #refreshTTSItemsView()}（刷新前清理旧视图动画）与
     * {@link #onPause()}（退后台时停止无效动画）共用同一遍历停止逻辑。
     */
    private void stopAllVisualizers() {
        if (visualizerViews == null) return;
        // 遍历当前持有的所有 AudioVisualizerView，逐个取消其无限 ValueAnimator
        for (AudioVisualizerView visualizer : visualizerViews) {
            visualizer.stopAnimation();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // 【性能优化】App/页面退后台时停止所有可视化动画，
        // 避免无限 ValueAnimator 在不可见时持续 invalidate 烧 CPU
        stopAllVisualizers();
    }

    private void addTTSItemToView(TTSItem item, int position) {
        if (ttsItemsContainer == null || getSafeActivity() == null) return;

        LinearLayout ttsItemLayout = new LinearLayout(getSafeActivity());
        ttsItemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        ttsItemLayout.setOrientation(LinearLayout.HORIZONTAL);
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        int paddingVertical = (int) (16 * getResources().getDisplayMetrics().density);
        ttsItemLayout.setPadding(padding, paddingVertical, padding, paddingVertical);
        ttsItemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        if (position % 2 == 0) {
            ttsItemLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.item_background_even));
        } else {
            ttsItemLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.item_background_odd));
        }

        Button btnPlayPause = new Button(getSafeActivity());
        int buttonWidth = (int) (120 * getResources().getDisplayMetrics().density);
        int buttonHeight = (int) (70 * getResources().getDisplayMetrics().density);
        int marginEnd = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                buttonWidth,
                buttonHeight
        );
        playParams.setMarginEnd(marginEnd);
        btnPlayPause.setLayoutParams(playParams);
        btnPlayPause.setText(item.isPlaying ? "\u23F8" : "\u25B6");
        btnPlayPause.setTextSize(20);

        if (item.isPlaying) {
            btnPlayPause.setBackgroundTintList(ContextCompat.getColorStateList(getSafeActivity(), R.color.colorAccent));
        } else {
            btnPlayPause.setBackgroundTintList(ContextCompat.getColorStateList(getSafeActivity(), R.color.button_background));
        }

        btnPlayPause.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text));
        btnPlayPause.setPadding(8, 8, 8, 8);
        final int finalPosition = position;
        btnPlayPause.setOnClickListener(v -> togglePlayPause(finalPosition));

        LinearLayout textContainer = new LinearLayout(getSafeActivity());
        textContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView ttsText = new TextView(getSafeActivity());
        ttsText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        ttsText.setText(item.text);
        ttsText.setTextSize(16);
        ttsText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        ttsText.setPadding(0, 0, 12, 8);
        ttsText.setOnClickListener(v -> togglePlayPause(finalPosition));
        textContainer.addView(ttsText);

        AudioVisualizerView visualizerView = new AudioVisualizerView(getSafeActivity());
        visualizerView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (32 * getResources().getDisplayMetrics().density)
        ));
        visualizerView.setVisibility(item.isPlaying ? View.VISIBLE : View.GONE);
        if (item.isPlaying) {
            visualizerView.startAnimation();
        }
        textContainer.addView(visualizerView);

        visualizerViews.add(visualizerView);

        ImageButton btnDelete = new ImageButton(getSafeActivity(), null, 0);
        int deleteButtonSize = (int) (32 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(deleteButtonSize, deleteButtonSize);
        deleteParams.setMarginStart((int) (12 * getResources().getDisplayMetrics().density));
        btnDelete.setLayoutParams(deleteParams);
        btnDelete.setImageResource(android.R.drawable.ic_menu_delete);
        android.util.TypedValue outValue = new android.util.TypedValue();
        if (getSafeActivity() != null && getSafeActivity().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)) {
            btnDelete.setBackgroundResource(outValue.resourceId);
        }
        btnDelete.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        btnDelete.setPadding(0, 0, 0, 0);
        btnDelete.setMinimumWidth(0);
        btnDelete.setMinimumHeight(0);
        btnDelete.setContentDescription("删除");
        btnDelete.setOnClickListener(v -> deleteTTSItem(finalPosition));

        ttsItemLayout.addView(btnPlayPause);
        ttsItemLayout.addView(textContainer);
        ttsItemLayout.addView(btnDelete);
        ttsItemsContainer.addView(ttsItemLayout);
    }

    private void togglePlayPause(int position) {
        if (position < 0 || position >= ttsItems.size()) return;

        TTSItem item = ttsItems.get(position);

        if (currentlyPlayingPosition == position && item.isPlaying) {
            stopTTS();
            item.isPlaying = false;
            currentlyPlayingPosition = -1;
        } else {
            if (currentlyPlayingPosition >= 0 && currentlyPlayingPosition < ttsItems.size()) {
                TTSItem previousItem = ttsItems.get(currentlyPlayingPosition);
                previousItem.isPlaying = false;
            }
            stopTTS();
            boolean playSuccess = playTTS(item.text);
            if (playSuccess) {
                item.isPlaying = true;
                currentlyPlayingPosition = position;
            } else {
                item.isPlaying = false;
                currentlyPlayingPosition = -1;
                Toast.makeText(getSafeContext(), "播放失败，请检查TTS引擎", Toast.LENGTH_SHORT).show();
            }
        }
        refreshTTSItemsView();
    }

    private boolean playTTS(String text) {
        if (ttsManager != null) {
            return ttsManager.speak(text);
        }
        return false;
    }

    private void stopTTS() {
        if (ttsManager != null) {
            ttsManager.stop();
        }
    }

    private void showFloatingListEditor() {
        if (getSafeActivity() == null) return;

        FloatingWindowConfig fwConfig = new FloatingWindowConfig(
                getSafeActivity().getSharedPreferences(
                        getSafeActivity().getPackageName() + "_preferences",
                        android.content.Context.MODE_PRIVATE));

        Gson gson = new Gson();

        // 读新 sp key（uid 方案），旧 indices/names 直接无视
        String uidsJson = fwConfig.getTTSSelectedUids();
        String namesJson = fwConfig.getTTSNamesByUid();

        List<String> selectedUids = new ArrayList<>();
        Map<String, String> customNamesByUid = new HashMap<>();

        try {
            List<String> savedUids = gson.fromJson(uidsJson, new TypeToken<List<String>>(){}.getType());
            if (savedUids != null) {
                selectedUids.addAll(savedUids);
            }
            Map<String, String> savedNames = gson.fromJson(namesJson, new TypeToken<Map<String, String>>(){}.getType());
            if (savedNames != null) {
                customNamesByUid.putAll(savedNames);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to load floating window config", e);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getSafeActivity());
        builder.setTitle("编辑悬浮窗列表");

        ScrollView scrollView = new ScrollView(getSafeActivity());
        LinearLayout container = new LinearLayout(getSafeActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        List<CheckBox> checkBoxes = new ArrayList<>();
        List<EditText> nameInputs = new ArrayList<>();
        List<String> itemUids = new ArrayList<>();

        for (int i = 0; i < ttsItems.size(); i++) {
            final TTSItem item = ttsItems.get(i);

            LinearLayout itemLayout = new LinearLayout(getSafeActivity());
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(0, 0, 0, padding);

            CheckBox checkBox = new CheckBox(getSafeActivity());
            checkBox.setText(item.text);
            checkBox.setTextSize(16);
            checkBox.setChecked(item.uid != null && selectedUids.contains(item.uid));
            checkBoxes.add(checkBox);
            itemUids.add(item.uid);

            EditText nameInput = new EditText(getSafeActivity());
            nameInput.setHint("自定义名称（可选）");
            nameInput.setTextSize(14);
            if (item.uid != null && customNamesByUid.containsKey(item.uid)) {
                nameInput.setText(customNamesByUid.get(item.uid));
            }
            nameInputs.add(nameInput);

            itemLayout.addView(checkBox);
            itemLayout.addView(nameInput);
            container.addView(itemLayout);
        }

        scrollView.addView(container);
        builder.setView(scrollView);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            positiveBtn.setTextColor(getResources().getColor(android.R.color.white));
            positiveBtn.setBackgroundColor(getResources().getColor(R.color.button_background_selected));
            positiveBtn.setTextSize(16);
            positiveBtn.setPadding(32, 16, 32, 16);

            negativeBtn.setTextColor(getResources().getColor(android.R.color.white));
            negativeBtn.setBackgroundColor(getResources().getColor(R.color.button_background));
            negativeBtn.setTextSize(16);
            negativeBtn.setPadding(32, 16, 32, 16);
        });

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "保存", (dialogInterface, which) -> {
            List<String> newSelectedUids = new ArrayList<>();
            Map<String, String> newCustomNames = new HashMap<>();

            for (int i = 0; i < checkBoxes.size(); i++) {
                if (checkBoxes.get(i).isChecked()) {
                    String uid = itemUids.get(i);
                    if (uid != null) {
                        newSelectedUids.add(uid);
                        String customName = nameInputs.get(i).getText().toString().trim();
                        if (!customName.isEmpty()) {
                            newCustomNames.put(uid, customName);
                        }
                    }
                }
            }

            fwConfig.setTTSSelectedUids(gson.toJson(newSelectedUids));
            fwConfig.setTTSNamesByUid(gson.toJson(newCustomNames));

            Toast.makeText(getSafeContext(), "已保存", Toast.LENGTH_SHORT).show();
        });

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "取消", (dialogInterface, which) -> {
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) {
            ttsManager.stop();
        }
    }
}
