package com.aug32.l7audio;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.aug32.l7audio.audio.AudioVisualizerView;
import com.aug32.l7audio.audio.TTSManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class TTSFragment extends Fragment {
    private static final String TAG = "TTSFragment";
    private static final int MAX_ITEMS = 20;
    private static final int MAX_TEXT_LENGTH = 100;

    private LinearLayout ttsItemsContainer;
    private EditText ttsInput;
    private Button btnAddTTS;
    private TTSManager ttsManager;
    private List<TTSItem> ttsItems;
    private List<AudioVisualizerView> visualizerViews;
    private int currentlyPlayingPosition = -1;

    private static class TTSItem {
        String text;
        boolean isPlaying;

        TTSItem(String text) {
            this.text = text;
            this.isPlaying = false;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tts, container, false);

        ttsItemsContainer = view.findViewById(R.id.tts_items_container);
        ttsInput = view.findViewById(R.id.tts_input);
        btnAddTTS = view.findViewById(R.id.btn_add_tts);
        Button btnEditFloatingList = view.findViewById(R.id.btn_edit_floating_list);

        if (getActivity() != null) {
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                ttsManager = mainActivity.getTTSManager();
                if (ttsManager != null) {
                    ttsManager.setProgressListener(new TTSManager.TTSProgressListener() {
                    @Override
                    public void onTTSStart() {
                        
                    }

                    @Override
                    public void onTTSDone() {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
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
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (currentlyPlayingPosition >= 0 && currentlyPlayingPosition < ttsItems.size()) {
                                    ttsItems.get(currentlyPlayingPosition).isPlaying = false;
                                    currentlyPlayingPosition = -1;
                                    refreshTTSItemsView();
                                }
                            });
                        }
                    }

                    @Override
                    public void onTTSProgress(int progress) {
                        
                    }
                });
                }
            }
        }

        ttsItems = new ArrayList<>();
        visualizerViews = new ArrayList<>();
        loadTTSItems();

        if (btnAddTTS != null) {
            btnAddTTS.setOnClickListener(v -> addTTSItem());
        }
        
        if (btnEditFloatingList != null) {
            btnEditFloatingList.setOnClickListener(v -> showFloatingListEditor());
        }

        return view;
    }

    private void loadTTSItems() {
        if (getActivity() != null) {
            AppConfig appConfig = new AppConfig(getActivity());
            String ttsItemsJson = appConfig.getTTSItems();
            if (!ttsItemsJson.isEmpty()) {
                try {
                    Gson gson = new Gson();
                    List<String> savedTexts = gson.fromJson(ttsItemsJson, new TypeToken<List<String>>(){}.getType());
                    if (savedTexts != null && !savedTexts.isEmpty()) {
                        for (String text : savedTexts) {
                            ttsItems.add(new TTSItem(text));
                        }
                        refreshTTSItemsView();
                        return;
                    }
                } catch (Exception e) {
                    AppLog.e(TAG, "Failed to load TTS items", e);
                }
            }
        }
        loadDefaultTTSItems();// 加载默认值并保存
        saveTTSItems(); // 保存默认值到SharedPreferences
    }

    private void saveTTSItems() {
        if (getActivity() != null) {
            AppConfig appConfig = new AppConfig(getActivity());
            List<String> texts = new ArrayList<>();
            for (TTSItem item : ttsItems) {
                texts.add(item.text);
            }
            Gson gson = new Gson();
            String ttsItemsJson = gson.toJson(texts);
            appConfig.setTTSItems(ttsItemsJson);
        }
    }

    private void loadDefaultTTSItems() {
        addDefaultTTSItem("欢迎使用L7 Audio");
        addDefaultTTSItem("窄路会车，请减速慢行");
        addDefaultTTSItem("车辆正在倒车，请保持安全距离");
        addDefaultTTSItem("车辆临时停靠，请小心避让");
        addDefaultTTSItem("恭喜发财，我要左转，谢谢");
        addDefaultTTSItem("恭喜发财，我要右转，谢谢");
        addDefaultTTSItem("路口礼让行人，请耐心等待");
        addDefaultTTSItem("后备箱已开启，请周围行人注意");
        addDefaultTTSItem("时速低于100请勿占用最左侧车道");
        addDefaultTTSItem("文明行车，跟车行驶，请保持车距");
    }

    private void addDefaultTTSItem(String text) {
        ttsItems.add(new TTSItem(text));
        refreshTTSItemsView();
    }

    private void addTTSItem() {
        if (ttsInput != null) {
            String text = ttsInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(getActivity(), "请输入文本", Toast.LENGTH_SHORT).show();
                return;
            }
            if (text.length() > MAX_TEXT_LENGTH) {
                Toast.makeText(getActivity(), "文本不能超过100个字符", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ttsItems.size() >= MAX_ITEMS) {
                Toast.makeText(getActivity(), "最多只能添加" + MAX_ITEMS + "条文本", Toast.LENGTH_SHORT).show();
                return;
            }
            ttsItems.add(new TTSItem(text));
            ttsInput.setText("");
            refreshTTSItemsView();
            saveTTSItems();
            Toast.makeText(getActivity(), "添加成功", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshTTSItemsView() {
        if (ttsItemsContainer == null) return;
        for (AudioVisualizerView visualizer : visualizerViews) {
            visualizer.stopAnimation();
        }
        ttsItemsContainer.removeAllViews();
        visualizerViews.clear();
        for (int i = 0; i < ttsItems.size(); i++) {
            addTTSItemToView(ttsItems.get(i), i);
        }
    }

    private void addTTSItemToView(TTSItem item, int position) {
        if (ttsItemsContainer == null || getActivity() == null) return;

        LinearLayout ttsItemLayout = new LinearLayout(getActivity());
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

        Button btnPlayPause = new Button(getActivity());
        int buttonWidth = (int) (120 * getResources().getDisplayMetrics().density);
        int buttonHeight = (int) (70 * getResources().getDisplayMetrics().density);
        int marginEnd = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                buttonWidth,
                buttonHeight
        );
        playParams.setMarginEnd(marginEnd);
        btnPlayPause.setLayoutParams(playParams);
        btnPlayPause.setText(item.isPlaying ? "⏸" : "▶");
        btnPlayPause.setTextSize(20);
        
        if (item.isPlaying) {
            btnPlayPause.setBackgroundTintList(ContextCompat.getColorStateList(getActivity(), R.color.colorAccent));
        } else {
            btnPlayPause.setBackgroundTintList(ContextCompat.getColorStateList(getActivity(), R.color.button_background));
        }
        
        btnPlayPause.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text));
        btnPlayPause.setPadding(8, 8, 8, 8);
        final int finalPosition = position;
        btnPlayPause.setOnClickListener(v -> togglePlayPause(finalPosition));

        // 创建垂直LinearLayout来容纳文本和进度条
        LinearLayout textContainer = new LinearLayout(getActivity());
        textContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        // 添加文本
        TextView ttsText = new TextView(getActivity());
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
        
        // 添加音频可视化视图
        AudioVisualizerView visualizerView = new AudioVisualizerView(getActivity());
        visualizerView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (32 * getResources().getDisplayMetrics().density)
        ));
        visualizerView.setVisibility(item.isPlaying ? View.VISIBLE : View.GONE);
        if (item.isPlaying) {
            visualizerView.startAnimation();
        }
        textContainer.addView(visualizerView);
        
        // 将可视化视图添加到列表中
        visualizerViews.add(visualizerView);

        Button btnDelete = new Button(getActivity());
        int deleteButtonWidth = (int) (100 * getResources().getDisplayMetrics().density);
        int deleteButtonHeight = (int) (70 * getResources().getDisplayMetrics().density);
        btnDelete.setLayoutParams(new LinearLayout.LayoutParams(
                deleteButtonWidth,
                deleteButtonHeight
        ));
        btnDelete.setText("删除");
        btnDelete.setTextSize(16);
        btnDelete.setBackgroundTintList(ContextCompat.getColorStateList(getActivity(), R.color.button_background));
        btnDelete.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_text));
        btnDelete.setPadding(8, 8, 8, 8);
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
                Toast.makeText(getActivity(), "播放失败，请检查TTS引擎", Toast.LENGTH_SHORT).show();
            }
        }
        refreshTTSItemsView();
    }

    private void deleteTTSItem(int position) {
        if (position >= 0 && position < ttsItems.size()) {
            if (currentlyPlayingPosition == position) {
                stopTTS();
                currentlyPlayingPosition = -1;
            } else if (currentlyPlayingPosition > position) {
                currentlyPlayingPosition--;
            }
            ttsItems.remove(position);
            refreshTTSItemsView();
            saveTTSItems();
            Toast.makeText(getActivity(), "已删除", Toast.LENGTH_SHORT).show();
        }
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) {
            ttsManager.stop();
        }
    }
    
    private void showFloatingListEditor() {
        if (getActivity() == null) return;
        
        AppConfig appConfig = new AppConfig(getActivity());
        Gson gson = new Gson();
        
        String indicesJson = appConfig.getFloatingWindowTTSIndices();
        String namesJson = appConfig.getFloatingWindowTTSNames();
        
        List<Integer> selectedIndices = new ArrayList<>();
        Map<Integer, String> customNames = new HashMap<>();
        
        try {
            List<Integer> savedIndices = gson.fromJson(indicesJson, new TypeToken<List<Integer>>(){}.getType());
            if (savedIndices != null) {
                selectedIndices.addAll(savedIndices);
            }
            
            Map<Integer, String> savedNames = gson.fromJson(namesJson, new TypeToken<Map<Integer, String>>(){}.getType());
            if (savedNames != null) {
                customNames.putAll(savedNames);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to load floating window config", e);
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("编辑悬浮窗列表");
        
        ScrollView scrollView = new ScrollView(getActivity());
        LinearLayout container = new LinearLayout(getActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);
        
        List<CheckBox> checkBoxes = new ArrayList<>();
        List<EditText> nameInputs = new ArrayList<>();
        
        for (int i = 0; i < ttsItems.size(); i++) {
            final int index = i;
            TTSItem item = ttsItems.get(i);
            
            LinearLayout itemLayout = new LinearLayout(getActivity());
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(0, 0, 0, padding);
            
            CheckBox checkBox = new CheckBox(getActivity());
            checkBox.setText(item.text);
            checkBox.setTextSize(16);
            checkBox.setChecked(selectedIndices.contains(index));
            checkBoxes.add(checkBox);
            
            EditText nameInput = new EditText(getActivity());
            nameInput.setHint("自定义名称（可选）");
            nameInput.setTextSize(14);
            if (customNames.containsKey(index)) {
                nameInput.setText(customNames.get(index));
            }
            nameInputs.add(nameInput);
            
            itemLayout.addView(checkBox);
            itemLayout.addView(nameInput);
            container.addView(itemLayout);
        }
        
        scrollView.addView(container);
        builder.setView(scrollView);
        
        final List<Integer> finalSelectedIndices = selectedIndices;
        final Map<Integer, String> finalCustomNames = customNames;
        
        AlertDialog dialog = builder.create();
        
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            
            // 设置保存按钮样式
            positiveBtn.setTextColor(getResources().getColor(android.R.color.white));
            positiveBtn.setBackgroundColor(getResources().getColor(R.color.button_background_selected));
            positiveBtn.setTextSize(16);
            positiveBtn.setPadding(32, 16, 32, 16);
            
            // 设置取消按钮样式
            negativeBtn.setTextColor(getResources().getColor(android.R.color.white));
            negativeBtn.setBackgroundColor(getResources().getColor(R.color.button_background));
            negativeBtn.setTextSize(16);
            negativeBtn.setPadding(32, 16, 32, 16);
        });
        
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "保存", (dialogInterface, which) -> {
            List<Integer> newSelectedIndices = new ArrayList<>();
            Map<Integer, String> newCustomNames = new HashMap<>();
            
            for (int i = 0; i < checkBoxes.size(); i++) {
                if (checkBoxes.get(i).isChecked()) {
                    newSelectedIndices.add(i);
                    String customName = nameInputs.get(i).getText().toString().trim();
                    if (!customName.isEmpty()) {
                        newCustomNames.put(i, customName);
                    }
                }
            }
            
            String newIndicesJson = gson.toJson(newSelectedIndices);
            String newNamesJson = gson.toJson(newCustomNames);
            appConfig.setFloatingWindowTTSIndices(newIndicesJson);
            appConfig.setFloatingWindowTTSNames(newNamesJson);
            
            Toast.makeText(getActivity(), "已保存", Toast.LENGTH_SHORT).show();
        });
        
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "取消", (dialogInterface, which) -> {
            dialog.dismiss();
        });
        
        dialog.show();
    }
}
