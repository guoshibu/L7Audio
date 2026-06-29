package com.aug32.l7audio.ui.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.aug32.l7audio.data.local.config.TTSConfig;
import com.aug32.l7audio.data.model.TTSItem;
import com.aug32.l7audio.data.repository.TTSRepository;

import java.util.List;

/**
 * TTS ViewModel
 *
 * 职责：
 * - 管理 TTS 列表状态（LiveData）
 * - 管理语速/音调参数
 * - 通过 Repository 与持久层交互
 *
 * 架构：UI 层（Fragment）→ ViewModel → Repository
 */
public class TTSViewModel extends AndroidViewModel {

    private final TTSRepository ttsRepository;
    private final TTSConfig ttsConfig;

    // LiveData 状态（直接使用 Repository 的 LiveData，避免双重 LiveData）
    private final MutableLiveData<Float> ttsSpeed = new MutableLiveData<>(1.0f);
    private final MutableLiveData<Float> ttsPitch = new MutableLiveData<>(1.0f);

    public TTSViewModel(@NonNull Application application) {
        super(application);
        this.ttsRepository = TTSRepository.getInstance(application);
        this.ttsConfig = new TTSConfig(
                application.getSharedPreferences(application.getPackageName() + "_preferences", Context.MODE_PRIVATE));
        // Repository 构造时已同步初始化 LiveData，无需再次加载
        ttsSpeed.setValue(ttsConfig.getTTSSpeed());
        ttsPitch.setValue(ttsConfig.getTTSPitch());
    }

    // ==================== TTS 列表操作 ====================

    public void addTTSItem(String text) {
        if (text == null || text.trim().isEmpty()) return;
        ttsRepository.addTTSItem(new TTSItem(text.trim()));
    }

    public void addTTSItem(String text, String customName) {
        if (text == null || text.trim().isEmpty()) return;
        ttsRepository.addTTSItem(new TTSItem(text.trim(), customName));
    }

    public void removeTTSItem(int position) {
        ttsRepository.removeTTSItem(position);
    }

    /** 恢复默认 TTS 列表 */
    public void restoreDefaultTTSItems() {
        ttsRepository.restoreDefaultTTSItems();
    }

    /** 获取默认 TTS 列表（同步） */
    public List<TTSItem> getDefaultTTSItems() {
        return ttsRepository.getDefaultTTSItemsSync();
    }

    /** 判断列表是否为空 */
    public boolean isTTSListEmpty() {
        return ttsRepository.isEmpty();
    }

    // ==================== TTS 参数 ====================

    public void setTTSSpeed(float speed) {
        ttsRepository.setTTSSpeed(speed);
        ttsSpeed.setValue(speed);
    }

    public void setTTSPitch(float pitch) {
        ttsRepository.setTTSPitch(pitch);
        ttsPitch.setValue(pitch);
    }

    public float getCurrentSpeed() {
        return ttsConfig.getTTSSpeed();
    }

    public float getCurrentPitch() {
        return ttsConfig.getTTSPitch();
    }

    // ==================== LiveData Getter（直接返回 Repository 的 LiveData）====================

    public LiveData<List<TTSItem>> getTTSItems() { return ttsRepository.getTTSItemsLiveData(); }
    public LiveData<Float> getTtsSpeed() { return ttsSpeed; }
    public LiveData<Float> getTtsPitch() { return ttsPitch; }
}
