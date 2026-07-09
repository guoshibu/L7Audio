package com.aug32.l7audio.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import com.aug32.l7audio.data.model.TTSItem;
import com.aug32.l7audio.data.repository.TTSRepository;

/**
 * TTS 语音合成 ViewModel
 *
 * <p>职责：
 * <ul>
 *   <li>管理 TTS 播报列表数据（通过 LiveData 驱动 UI 更新）</li>
 *   <li>作为 UI 层与数据层的中间层，通过 Repository 与持久层交互</li>
 * </ul>
 *
 * <p>架构：UI 层（Fragment）→ ViewModel → Repository
 *
 * <p>设计说明：
 * <ul>
 *   <li>继承 AndroidViewModel，持有 Application 引用以便访问全局资源</li>
 *   <li>TTS 列表 LiveData 直接代理 Repository 的 LiveData，避免双重包装</li>
 * </ul>
 *
 * @author L7Audio Team
 */
public class TTSViewModel extends AndroidViewModel {

    /** TTS 数据仓库，负责数据持久化和业务逻辑 */
    private final TTSRepository ttsRepository;

    /**
     * 构造函数，初始化 Repository
     *
     * @param application Application 上下文
     */
    public TTSViewModel(@NonNull Application application) {
        super(application);
        this.ttsRepository = TTSRepository.getInstance(application);
    }

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

    /**
     * 获取 TTS 列表的 LiveData 观察对象
     * 直接返回 Repository 的 LiveData，避免双重包装
     *
     * @return TTS 列表 LiveData
     */
    public LiveData<List<TTSItem>> getTTSItems() { return ttsRepository.getTTSItemsLiveData(); }
}
