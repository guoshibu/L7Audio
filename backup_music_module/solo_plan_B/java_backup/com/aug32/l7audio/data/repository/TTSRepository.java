package com.aug32.l7audio.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.aug32.l7audio.data.local.config.TTSConfig;
import com.aug32.l7audio.data.model.TTSItem;
import com.aug32.l7audio.utils.AppExecutors;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * TTS 数据仓库
 *
 * 职责：TTS 列表的持久化加载/保存（JSON ↔ List<TTSItem>）
 */
public class TTSRepository {

    private static TTSRepository instance;

    private final TTSConfig ttsConfig;
    private final Gson gson = new Gson();
    private List<TTSItem> cachedItems = null;

    private final MutableLiveData<List<TTSItem>> ttsItemsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Float> speedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Float> pitchLiveData = new MutableLiveData<>();

    public static synchronized TTSRepository getInstance(Context context) {
        if (instance == null) {
            instance = new TTSRepository(context.getApplicationContext());
        }
        return instance;
    }

    private TTSRepository(Context context) {
        this.ttsConfig = new TTSConfig(
                context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE));
        speedLiveData.setValue(ttsConfig.getTTSSpeed());
        pitchLiveData.setValue(ttsConfig.getTTSPitch());
        // 构造时同步加载一次数据，确保 LiveData 有初始值
        List<TTSItem> initialItems = loadTTSItemsSync();
        ttsItemsLiveData.setValue(initialItems);
    }

    /** 同步加载 TTS 列表（从缓存或 SharedPreferences） */
    public List<TTSItem> loadTTSItemsSync() {
        if (cachedItems != null) {
            return new ArrayList<>(cachedItems);
        }
        String json = ttsConfig.getTTSItems();
        if (json == null || json.isEmpty()) {
            // 首次加载：初始化默认列表
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

    /** 创建默认 TTS 列表（车机常用语音播报） */
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

    /** 恢复默认 TTS 列表（删除全部后自动调用） */
    public void restoreDefaultTTSItems() {
        AppExecutors.getInstance().executeOnIOThread(() -> {
            List<TTSItem> defaults = createDefaultTTSItems();
            cachedItems = new ArrayList<>(defaults);
            String json = gson.toJson(defaults);
            ttsConfig.setTTSItems(json);
            ttsItemsLiveData.postValue(new ArrayList<>(defaults));
        });
    }

    /** 获取默认 TTS 列表（同步，用于立即更新 UI） */
    public List<TTSItem> getDefaultTTSItemsSync() {
        return createDefaultTTSItems();
    }

    /** 判断是否为空列表 */
    public boolean isEmpty() {
        List<TTSItem> items = loadTTSItemsSync();
        return items == null || items.isEmpty();
    }

    /** 保存默认列表到 SharedPreferences */
    private void saveDefaultTTSItems(List<TTSItem> items) {
        String json = gson.toJson(items);
        ttsConfig.setTTSItems(json);
    }

    /** 异步加载 TTS 列表 */
    public void loadTTSItems(OnTTSItemsLoadedCallback callback) {
        AppExecutors.getInstance().executeOnComputeThread(() -> {
            List<TTSItem> items = loadTTSItemsSync();
            ttsItemsLiveData.postValue(new ArrayList<>(items));
            if (callback != null) {
                callback.onLoaded(new ArrayList<>(items));
            }
        });
    }

    /** 异步保存 TTS 列表 */
    public void saveTTSItems(List<TTSItem> items) {
        AppExecutors.getInstance().executeOnIOThread(() -> {
            cachedItems = new ArrayList<>(items);
            String json = gson.toJson(items);
            ttsConfig.setTTSItems(json);
            ttsItemsLiveData.postValue(new ArrayList<>(items));
        });
    }

    /** 异步添加 TTS 项 */
    public void addTTSItem(TTSItem item) {
        List<TTSItem> items = loadTTSItemsSync();
        items.add(item);
        saveTTSItems(items);
    }

    /** 异步移除 TTS 项 */
    public void removeTTSItem(int position) {
        List<TTSItem> items = loadTTSItemsSync();
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            saveTTSItems(items);
        }
    }

    /** 保存语速 */
    public void setTTSSpeed(float speed) {
        AppExecutors.getInstance().executeOnIOThread(() -> {
            ttsConfig.setTTSSpeed(speed);
            speedLiveData.postValue(speed);
        });
    }

    /** 保存音调 */
    public void setTTSPitch(float pitch) {
        AppExecutors.getInstance().executeOnIOThread(() -> {
            ttsConfig.setTTSPitch(pitch);
            pitchLiveData.postValue(pitch);
        });
    }

    public List<TTSItem> getCachedItems() {
        return cachedItems != null ? new ArrayList<>(cachedItems) : new ArrayList<>();
    }

    public LiveData<List<TTSItem>> getTTSItemsLiveData() {
        return ttsItemsLiveData;
    }

    public LiveData<Float> getSpeedLiveData() {
        return speedLiveData;
    }

    public LiveData<Float> getPitchLiveData() {
        return pitchLiveData;
    }

    public interface OnTTSItemsLoadedCallback {
        void onLoaded(List<TTSItem> items);
    }
}
