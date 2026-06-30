package com.aug32.l7audio.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.aug32.l7audio.data.local.config.TTSConfig;
import com.aug32.l7audio.data.model.TTSItem;
import com.aug32.l7audio.utils.AppExecutors;

/**
 * TTS 数据仓库
 *
 * <p>职责：TTS 列表的持久化加载/保存（JSON ↔ List<TTSItem>）。
 *
 * <p>设计意图：
 * <ul>
 *   <li>采用单例模式确保全局唯一的数据访问入口，避免多实例导致的数据不一致</li>
 *   <li>使用内存缓存（cachedItems）减少磁盘读取次数，提升性能</li>
 *   <li>通过 LiveData 实现数据驱动的 UI 更新，观察者模式解耦数据层与表现层</li>
 *   <li>区分同步/异步方法，同步方法用于立即获取数据，异步方法用于耗时 IO 操作</li>
 *   <li>持久化存储基于 SharedPreferences，通过 Gson 进行 JSON 序列化/反序列化</li>
 * </ul>
 *
 * @author L7Audio Team
 */
public class TTSRepository {

    /** 单例实例 */
    private static TTSRepository instance;

    /** TTS 配置持久化工具 */
    private final TTSConfig ttsConfig;
    /** Gson 序列化/反序列化工具 */
    private final Gson gson = new Gson();
    /** 内存缓存的 TTS 列表 */
    private List<TTSItem> cachedItems = null;

    /** TTS 列表的 LiveData 可观察数据 */
    private final MutableLiveData<List<TTSItem>> ttsItemsLiveData = new MutableLiveData<>();

    /**
     * 获取 TTSRepository 单例实例
     *
     * @param context 上下文对象，用于初始化 SharedPreferences
     * @return TTSRepository 单例实例
     */
    public static synchronized TTSRepository getInstance(Context context) {
        if (instance == null) {
            instance = new TTSRepository(context.getApplicationContext());
        }
        return instance;
    }

    private TTSRepository(Context context) {
        this.ttsConfig = new TTSConfig(
                context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE));
        List<TTSItem> initialItems = loadTTSItemsSync();
        ttsItemsLiveData.setValue(initialItems);
    }

    /**
     * 同步加载 TTS 列表
     *
     * <p>优先从内存缓存读取，缓存未命中则从 SharedPreferences 加载 JSON 数据并反序列化。
     * 首次加载（无数据）时自动创建默认列表并持久化保存。
     *
     * @return TTS 项目列表的副本（防止外部修改内部缓存）
     */
    public List<TTSItem> loadTTSItemsSync() {
        if (cachedItems != null) {
            return new ArrayList<>(cachedItems);
        }
        String json = ttsConfig.getTTSItems();
        if (json == null || json.isEmpty()) {
            cachedItems = createDefaultTTSItems();
            saveDefaultTTSItems(cachedItems);
            return new ArrayList<>(cachedItems);
        }
        try {
            Type listType = new TypeToken<List<TTSItem>>() {}.getType();
            List<TTSItem> items = gson.fromJson(json, listType);
            cachedItems = items != null ? items : new ArrayList<>();
        } catch (Exception e) {
            cachedItems = new ArrayList<>();
        }
        return new ArrayList<>(cachedItems);
    }

    /**
     * 创建默认 TTS 列表（车机常用语音播报）
     *
     * @return 默认 TTS 项目列表
     */
    private List<TTSItem> createDefaultTTSItems() {
        List<TTSItem> defaults = new ArrayList<>();
        defaults.add(new TTSItem("欢迎使用L7 Audio"));
        defaults.add(new TTSItem("窄路会车，请减速慢行"));
        defaults.add(new TTSItem("车辆正在倒车，请保持安全距离"));
        defaults.add(new TTSItem("车辆临时停靠，请小心避让"));
        defaults.add(new TTSItem("恭喜发财，我要左转，谢谢"));
        defaults.add(new TTSItem("恭喜发财，我要右转，谢谢"));
        defaults.add(new TTSItem("路口礼让行人，请耐心等待"));
        defaults.add(new TTSItem("后备箱已开启，请周围行人注意"));
        defaults.add(new TTSItem("时速低于100请勿占用最左侧车道"));
        defaults.add(new TTSItem("文明行车，跟车行驶，请保持车距"));
        return defaults;
    }

    /**
     * 恢复默认 TTS 列表（异步执行）
     *
     * <p>清空现有数据，重新生成默认 TTS 列表并持久化保存，同时通知 LiveData 观察者更新。
     * 通常在用户删除全部自定义条目后调用此方法恢复出厂默认配置。
     */
    public void restoreDefaultTTSItems() {
        AppExecutors.getInstance().executeOnIOThread(() -> {
            List<TTSItem> defaults = createDefaultTTSItems();
            cachedItems = new ArrayList<>(defaults);
            String json = gson.toJson(defaults);
            ttsConfig.setTTSItems(json);
            ttsItemsLiveData.postValue(new ArrayList<>(defaults));
        });
    }

    /**
     * 获取默认 TTS 列表（同步）
     *
     * <p>直接创建并返回默认 TTS 列表，不读取缓存也不进行持久化操作。
     * 适用于需要立即获取默认数据用于 UI 展示的场景。
     *
     * @return 默认 TTS 项目列表
     */
    public List<TTSItem> getDefaultTTSItemsSync() {
        return createDefaultTTSItems();
    }

    /**
     * 判断 TTS 列表是否为空
     *
     * <p>同步加载列表数据后判断列表是否为 null 或无元素。
     *
     * @return true 表示列表为空，false 表示列表包含至少一个元素
     */
    public boolean isEmpty() {
        List<TTSItem> items = loadTTSItemsSync();
        return items == null || items.isEmpty();
    }

    /**
     * 保存默认列表到 SharedPreferences
     *
     * @param items 要保存的 TTS 项目列表
     */
    private void saveDefaultTTSItems(List<TTSItem> items) {
        String json = gson.toJson(items);
        ttsConfig.setTTSItems(json);
    }

    /**
     * 异步加载 TTS 列表
     *
     * <p>在计算线程中加载 TTS 列表数据，加载完成后通过回调接口返回结果，
     * 同时更新 LiveData 通知观察者。
     *
     * @param callback 加载完成回调接口，可为 null（仅更新 LiveData，不触发回调）
     */
    public void loadTTSItems(OnTTSItemsLoadedCallback callback) {
        AppExecutors.getInstance().executeOnComputeThread(() -> {
            List<TTSItem> items = loadTTSItemsSync();
            ttsItemsLiveData.postValue(new ArrayList<>(items));
            if (callback != null) {
                callback.onLoaded(new ArrayList<>(items));
            }
        });
    }

    /**
     * 异步保存 TTS 列表
     *
     * <p>在 IO 线程中将 TTS 列表序列化为 JSON 并持久化到 SharedPreferences，
     * 同时更新内存缓存和 LiveData 通知观察者。
     *
     * @param items 要保存的 TTS 项目列表
     */
    public void saveTTSItems(List<TTSItem> items) {
        AppExecutors.getInstance().executeOnIOThread(() -> {
            cachedItems = new ArrayList<>(items);
            String json = gson.toJson(items);
            ttsConfig.setTTSItems(json);
            ttsItemsLiveData.postValue(new ArrayList<>(items));
        });
    }

    /**
     * 异步添加 TTS 项
     *
     * <p>先同步加载当前列表，将新项追加到列表末尾，然后异步保存更新后的列表。
     *
     * @param item 要添加的 TTS 项目
     */
    public void addTTSItem(TTSItem item) {
        List<TTSItem> items = loadTTSItemsSync();
        items.add(item);
        saveTTSItems(items);
    }

    /**
     * 异步移除指定位置的 TTS 项
     *
     * <p>先同步加载当前列表，移除指定位置的元素后异步保存更新后的列表。
     * 若位置越界则不执行任何操作。
     *
     * @param position 要移除的 TTS 项在列表中的索引位置
     */
    public void removeTTSItem(int position) {
        List<TTSItem> items = loadTTSItemsSync();
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            saveTTSItems(items);
        }
    }

    /**
     * 获取内存缓存的 TTS 列表副本
     *
     * @return TTS 项目列表的副本，若缓存为 null 则返回空列表
     */
    public List<TTSItem> getCachedItems() {
        return cachedItems != null ? new ArrayList<>(cachedItems) : new ArrayList<>();
    }

    /**
     * 获取 TTS 列表的 LiveData 可观察对象
     *
     * @return TTS 列表的 LiveData
     */
    public LiveData<List<TTSItem>> getTTSItemsLiveData() {
        return ttsItemsLiveData;
    }

    /**
     * TTS 列表加载完成回调接口
     */
    public interface OnTTSItemsLoadedCallback {
        /**
         * 加载完成时回调
         *
         * @param items 加载完成的 TTS 项目列表
         */
        void onLoaded(List<TTSItem> items);
    }
}
