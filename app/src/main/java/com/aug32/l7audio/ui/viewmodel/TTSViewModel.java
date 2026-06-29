package com.aug32.l7audio.ui.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import com.aug32.l7audio.data.local.config.TTSConfig;
import com.aug32.l7audio.data.model.TTSItem;
import com.aug32.l7audio.data.repository.TTSRepository;

/**
 * TTS 语音合成 ViewModel
 *
 * 职责：
 * - 管理 TTS 播报列表数据（通过 LiveData 驱动 UI 更新）
 * - 管理 TTS 语速、音调参数的状态持久化
 * - 作为 UI 层与数据层的中间层，通过 Repository 与持久层交互
 *
 * 架构：UI 层（Fragment）→ ViewModel → Repository
 *
 * 设计说明：
 * - 继承 AndroidViewModel，持有 Application 引用以便访问全局资源
 * - TTS 列表 LiveData 直接代理 Repository 的 LiveData，避免双重包装
 * - 语速/音调参数独立维护 MutableLiveData，同时同步到 Repository 和配置存储
 */
public class TTSViewModel extends AndroidViewModel {

    /** TTS 数据仓库，负责数据持久化和业务逻辑 */
    private final TTSRepository ttsRepository;
    /** TTS 配置存储，负责语速、音调等参数的持久化 */
    private final TTSConfig ttsConfig;

    /** TTS 语速 LiveData，默认值 1.0f */
    private final MutableLiveData<Float> ttsSpeed = new MutableLiveData<>(1.0f);
    /** TTS 音调 LiveData，默认值 1.0f */
    private final MutableLiveData<Float> ttsPitch = new MutableLiveData<>(1.0f);

    /**
     * 构造函数，初始化 Repository 和配置，并从配置中读取当前语速/音调
     *
     * @param application Application 上下文
     */
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

    /**
     * 添加一条 TTS 播报项
     * 文本为空或纯空格时忽略
     *
     * @param text 播报文本内容
     */
    public void addTTSItem(String text) {
        if (text == null || text.trim().isEmpty()) return;
        ttsRepository.addTTSItem(new TTSItem(text.trim()));
    }

    /**
     * 添加一条带自定义名称的 TTS 播报项
     * 文本为空或纯空格时忽略
     *
     * @param text       播报文本内容
     * @param customName 自定义显示名称
     */
    public void addTTSItem(String text, String customName) {
        if (text == null || text.trim().isEmpty()) return;
        ttsRepository.addTTSItem(new TTSItem(text.trim(), customName));
    }

    /**
     * 移除指定位置的 TTS 播报项
     *
     * @param position 要移除的位置索引
     */
    public void removeTTSItem(int position) {
        ttsRepository.removeTTSItem(position);
    }

    /**
     * 恢复默认 TTS 列表
     * 清除当前列表并恢复预置的默认播报项
     */
    public void restoreDefaultTTSItems() {
        ttsRepository.restoreDefaultTTSItems();
    }

    /**
     * 获取默认 TTS 列表（同步方法）
     *
     * @return 默认 TTS 项列表
     */
    public List<TTSItem> getDefaultTTSItems() {
        return ttsRepository.getDefaultTTSItemsSync();
    }

    /**
     * 判断 TTS 列表是否为空
     *
     * @return true 列表为空，false 列表不为空
     */
    public boolean isTTSListEmpty() {
        return ttsRepository.isEmpty();
    }

    // ==================== TTS 参数 ====================

    /**
     * 设置 TTS 语速
     * 同步更新 Repository 和本地 LiveData
     *
     * @param speed 语速值，1.0f 为正常语速
     */
    public void setTTSSpeed(float speed) {
        ttsRepository.setTTSSpeed(speed);
        ttsSpeed.setValue(speed);
    }

    /**
     * 设置 TTS 音调
     * 同步更新 Repository 和本地 LiveData
     *
     * @param pitch 音调值，1.0f 为正常音调
     */
    public void setTTSPitch(float pitch) {
        ttsRepository.setTTSPitch(pitch);
        ttsPitch.setValue(pitch);
    }

    /**
     * 获取当前语速（从配置中读取）
     *
     * @return 当前语速值
     */
    public float getCurrentSpeed() {
        return ttsConfig.getTTSSpeed();
    }

    /**
     * 获取当前音调（从配置中读取）
     *
     * @return 当前音调值
     */
    public float getCurrentPitch() {
        return ttsConfig.getTTSPitch();
    }

    // ==================== LiveData Getter ====================

    /**
     * 获取 TTS 列表的 LiveData 观察对象
     * 直接返回 Repository 的 LiveData，避免双重包装
     *
     * @return TTS 列表 LiveData
     */
    public LiveData<List<TTSItem>> getTTSItems() { return ttsRepository.getTTSItemsLiveData(); }

    /**
     * 获取语速的 LiveData 观察对象
     *
     * @return 语速 LiveData
     */
    public LiveData<Float> getTtsSpeed() { return ttsSpeed; }

    /**
     * 获取音调的 LiveData 观察对象
     *
     * @return 音调 LiveData
     */
    public LiveData<Float> getTtsPitch() { return ttsPitch; }
}
