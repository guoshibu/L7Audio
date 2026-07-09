package com.aug32.l7audio.domain.audio.player;

/**
 * 播放状态（不可变数据类）
 *
 * 职责：封装当前播放状态，避免外部直接修改内部状态。
 * 所有字段都是只读的，保证线程安全，使用Builder模式构建实例。
 *
 * 包含信息：播放状态枚举、当前播放索引、播放进度、歌曲时长、
 * 当前播放的音乐项、错误信息等。
 */
public class PlaybackState {

    /**
     * 播放状态枚举
     *
     * 定义播放器可能处于的所有状态，用于状态机管理和UI展示。
     */
    public enum State {
        /** 空闲状态，播放器已初始化但未加载任何音乐 */
        IDLE,
        /** 加载中，正在准备音乐数据 */
        LOADING,
        /** 正在播放 */
        PLAYING,
        /** 已暂停 */
        PAUSED,
        /** 已停止 */
        STOPPED,
        /** 播放出错 */
        ERROR
    }

    /** 当前播放状态 */
    private final State state;
    /** 当前播放歌曲在播放列表中的索引，-1表示无有效歌曲 */
    private final int currentIndex;
    /** 当前播放位置，单位毫秒 */
    private final long currentPosition;
    /** 当前歌曲总时长，单位毫秒 */
    private final long duration;
    /** 当前播放的音乐项 */
    private final MusicItem currentItem;
    /** 错误信息，仅在状态为ERROR时有意义 */
    private final String errorMessage;

    /**
     * 私有构造函数，通过Builder构建实例
     *
     * @param builder Builder对象，包含所有字段的初始值
     */
    private PlaybackState(Builder builder) {
        this.state = builder.state;
        this.currentIndex = builder.currentIndex;
        this.currentPosition = builder.currentPosition;
        this.duration = builder.duration;
        this.currentItem = builder.currentItem;
        this.errorMessage = builder.errorMessage;
    }

    public State getState() { return state; }
    public int getCurrentIndex() { return currentIndex; }
    public long getCurrentPosition() { return currentPosition; }
    public long getDuration() { return duration; }
    public MusicItem getCurrentItem() { return currentItem; }
    public String getErrorMessage() { return errorMessage; }

    /**
     * 判断是否正在播放
     *
     * @return 如果当前状态为PLAYING则返回true，否则返回false
     */
    public boolean isPlaying() { return state == State.PLAYING; }

    /**
     * 判断是否有当前播放的音乐项
     *
     * @return 如果currentItem不为null则返回true，否则返回false
     */
    public boolean hasCurrentItem() { return currentItem != null; }

    /**
     * 创建一个新的Builder实例
     *
     * @return 初始化为默认值的Builder对象
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 基于当前状态创建一个Builder，用于派生新的状态
     *
     * 常用于状态变更时保留其他字段不变，只修改部分字段。
     * 例如：暂停时只修改state，保留currentPosition、currentItem等。
     *
     * @return 包含当前状态所有字段值的Builder对象
     */
    public Builder buildUpon() {
        return new Builder()
                .state(state)
                .currentIndex(currentIndex)
                .currentPosition(currentPosition)
                .duration(duration)
                .currentItem(currentItem)
                .errorMessage(errorMessage);
    }

    /**
     * PlaybackState的构建器
     *
     * 使用Builder模式构建不可变的PlaybackState对象，
     * 支持链式调用，提高可读性和可维护性。
     */
    public static class Builder {
        /** 播放状态，默认IDLE */
        private State state = State.IDLE;
        /** 当前播放索引，默认-1（无有效歌曲） */
        private int currentIndex = -1;
        /** 当前播放位置，默认0 */
        private long currentPosition = 0;
        /** 歌曲总时长，默认0 */
        private long duration = 0;
        /** 当前音乐项，默认null */
        private MusicItem currentItem = null;
        /** 错误信息，默认null */
        private String errorMessage = null;

        /**
         * 设置播放状态
         *
         * @param state 播放状态枚举值
         * @return 当前Builder实例，支持链式调用
         */
        public Builder state(State state) { this.state = state; return this; }

        /**
         * 设置当前播放索引
         *
         * @param index 歌曲在播放列表中的索引
         * @return 当前Builder实例，支持链式调用
         */
        public Builder currentIndex(int index) { this.currentIndex = index; return this; }

        /**
         * 设置当前播放位置
         *
         * @param pos 播放位置，单位毫秒
         * @return 当前Builder实例，支持链式调用
         */
        public Builder currentPosition(long pos) { this.currentPosition = pos; return this; }

        /**
         * 设置歌曲总时长
         *
         * @param dur 歌曲时长，单位毫秒
         * @return 当前Builder实例，支持链式调用
         */
        public Builder duration(long dur) { this.duration = dur; return this; }

        /**
         * 设置当前播放的音乐项
         *
         * @param item 音乐项对象
         * @return 当前Builder实例，支持链式调用
         */
        public Builder currentItem(MusicItem item) { this.currentItem = item; return this; }

        /**
         * 设置错误信息
         *
         * @param msg 错误描述信息
         * @return 当前Builder实例，支持链式调用
         */
        public Builder errorMessage(String msg) { this.errorMessage = msg; return this; }

        /**
         * 构建PlaybackState实例
         *
         * @return 不可变的PlaybackState对象
         */
        public PlaybackState build() {
            return new PlaybackState(this);
        }
    }
}
