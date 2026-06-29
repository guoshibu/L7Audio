package com.aug32.l7audio.domain.audio;

/**
 * 播放状态（不可变数据类）
 *
 * 职责：封装当前播放状态，避免外部直接修改内部状态
 * 所有字段都是只读的，保证线程安全
 */
public class PlaybackState {

    public enum State {
        IDLE,
        LOADING,
        PLAYING,
        PAUSED,
        STOPPED,
        ERROR
    }

    private final State state;
    private final int currentIndex;
    private final long currentPosition;
    private final long duration;
    private final MusicItem currentItem;
    private final String errorMessage;

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

    public boolean isPlaying() { return state == State.PLAYING; }
    public boolean isPaused() { return state == State.PAUSED; }
    public boolean isStopped() { return state == State.STOPPED || state == State.IDLE; }
    public boolean hasError() { return state == State.ERROR; }
    public boolean hasCurrentItem() { return currentItem != null; }

    public static Builder builder() {
        return new Builder();
    }

    public Builder buildUpon() {
        return new Builder()
                .state(state)
                .currentIndex(currentIndex)
                .currentPosition(currentPosition)
                .duration(duration)
                .currentItem(currentItem)
                .errorMessage(errorMessage);
    }

    public static class Builder {
        private State state = State.IDLE;
        private int currentIndex = -1;
        private long currentPosition = 0;
        private long duration = 0;
        private MusicItem currentItem = null;
        private String errorMessage = null;

        public Builder state(State state) { this.state = state; return this; }
        public Builder currentIndex(int index) { this.currentIndex = index; return this; }
        public Builder currentPosition(long pos) { this.currentPosition = pos; return this; }
        public Builder duration(long dur) { this.duration = dur; return this; }
        public Builder currentItem(MusicItem item) { this.currentItem = item; return this; }
        public Builder errorMessage(String msg) { this.errorMessage = msg; return this; }

        public PlaybackState build() {
            return new PlaybackState(this);
        }
    }
}
