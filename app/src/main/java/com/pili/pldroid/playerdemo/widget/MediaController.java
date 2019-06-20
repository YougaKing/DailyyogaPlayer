package com.pili.pldroid.playerdemo.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.pili.pldroid.player.IMediaController;
import com.pili.pldroid.player.widget.PLVideoView;
import com.pili.pldroid.playerdemo.R;
import com.pili.pldroid.playerdemo.utils.Utils;

import java.util.Locale;

/**
 * You can write a custom MediaController instead of this class
 * A MediaController widget must implement all the interface defined by com.pili.pldroid.player.IMediaController
 */
public class MediaController extends FrameLayout implements IMediaController {

    private static final String TAG = "PLMediaController";
    private IMediaController.MediaPlayerControl mPlayer;
    private Context mContext;
    private ProgressBar mProgress;
    private TextView mEndTime, mCurrentTime;
    private long mDuration;
    private boolean mShowing;
    private boolean mDragging;
    private boolean mInstantSeeking = true;
    private long mSeekPosition = 0l;
    private static int sDefaultTimeout = 3000;
    private static final int SEEK_TO_POST_DELAY_MILLIS = 200;

    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private ImageButton mPauseButton;
    private ImageButton mZoomButton;

    private boolean mUseFastForward = true;

    private AudioManager mAM;
    private Runnable mLastSeekBarRunnable;
    private boolean mDisableProgress = false;
    private OnClickSpeedAdjustListener mOnClickSpeedAdjustListener;
    private InteractionListener mInteractionListener;

    public interface OnClickSpeedAdjustListener {
        void onClickNormal();

        void onClickFaster();

        void onClickSlower();
    }

    public interface InteractionListener {
        void onZoom();
    }

    public MediaController(Context context) {
        this(context, null);
    }

    public MediaController(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MediaController(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context.getApplicationContext();
        mAM = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        LayoutInflater.from(context).inflate(R.layout.media_controller, this, true);
        initControllerView();
    }

    public void refreshProgress() {
        mProgress.setProgress(1000);
        mCurrentTime.setText(generateTime(mDuration));
    }

    public void setOnClickSpeedAdjustListener(OnClickSpeedAdjustListener listener) {
        mOnClickSpeedAdjustListener = listener;
    }

    public void setInteractionListener(InteractionListener interactionListener) {
        mInteractionListener = interactionListener;
    }

    private void initControllerView() {
        mZoomButton = (ImageButton) findViewById(R.id.ib_zoom);
        if (mZoomButton != null) {
            mZoomButton.requestFocus();
            mZoomButton.setOnClickListener(mZoomListener);
        }

        mPauseButton = (ImageButton) findViewById(R.id.iv_pause);
        if (mPauseButton != null) {
            mPauseButton.requestFocus();
            mPauseButton.setOnClickListener(mPauseListener);
        }

        mProgress = (ProgressBar) findViewById(R.id.mediacontroller_progress);
        if (mProgress != null) {
            if (mProgress instanceof SeekBar) {
                SeekBar seeker = (SeekBar) mProgress;
                seeker.setOnSeekBarChangeListener(mSeekListener);
                seeker.setThumbOffset(1);
            }
            mProgress.setMax(1000);
            mProgress.setEnabled(!mDisableProgress);
        }

        mEndTime = (TextView) findViewById(R.id.time);
        mCurrentTime = (TextView) findViewById(R.id.time_current);
    }

    /**
     * Control the action when the seekbar dragged by user
     *
     * @param seekWhenDragging True the media will seek periodically
     */
    public void setInstantSeeking(boolean seekWhenDragging) {
        mInstantSeeking = seekWhenDragging;
    }

    private void disableUnsupportedButtons() {
        try {
            if (mPauseButton != null && !mPlayer.canPause())
                mPauseButton.setEnabled(false);
        } catch (IncompatibleClassChangeError ex) {
        }
    }

    public interface OnShownListener {
        public void onShown();
    }

    private OnShownListener mShownListener;

    public void setOnShownListener(OnShownListener l) {
        mShownListener = l;
    }

    public interface OnHiddenListener {
        public void onHidden();
    }

    private OnHiddenListener mHiddenListener;

    public void setOnHiddenListener(OnHiddenListener l) {
        mHiddenListener = l;
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            long pos;
            switch (msg.what) {
                case FADE_OUT:
                    hide();
                    break;
                case SHOW_PROGRESS:
                    if (!mPlayer.isPlaying()) {
                        return;
                    }
                    pos = setProgress();
                    if (pos == -1) {
                        return;
                    }
                    if (!mDragging && mShowing) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 50);
                        updatePausePlay();
                    }
                    break;
            }
        }
    };

    private long setProgress() {
        if (mPlayer == null || mDragging) {
            return 0;
        }

        long position = mPlayer.getCurrentPosition();
        long duration = mPlayer.getDuration();
        if (mProgress != null) {
            if (duration > 0) {
                long pos = 1000L * position / duration;
                mProgress.setProgress((int) pos);
            }
            int percent = mPlayer.getBufferPercentage();
            mProgress.setSecondaryProgress(percent * 10);
        }

        mDuration = duration;

        if (mEndTime != null)
            mEndTime.setText(generateTime(mDuration));
        if (mCurrentTime != null)
            mCurrentTime.setText(generateTime(position));

        return position;
    }

    private static String generateTime(long position) {
        int totalSeconds = (int) (position / 1000);

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes,
                    seconds).toString();
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
                    .toString();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        show(sDefaultTimeout);
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        show(sDefaultTimeout);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getRepeatCount() == 0
                && (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_SPACE)) {
            doPauseResume();
            show(sDefaultTimeout);
            if (mPauseButton != null)
                mPauseButton.requestFocus();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
            if (mPlayer.isPlaying()) {
                mPlayer.pause();
                updatePausePlay();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_MENU) {
            hide();
            return true;
        } else {
            show(sDefaultTimeout);
        }
        return super.dispatchKeyEvent(event);
    }

    private OnClickListener mPauseListener = new OnClickListener() {
        public void onClick(View v) {
            if (mOnClickSpeedAdjustListener != null) {
                mOnClickSpeedAdjustListener.onClickNormal();
            }
            doPauseResume();
            show(sDefaultTimeout);
        }
    };

    private OnClickListener mZoomListener = new OnClickListener() {
        public void onClick(View v) {
            if (mInteractionListener != null) {
                mInteractionListener.onZoom();
            }
            doPauseResume();
            show(sDefaultTimeout);
        }
    };

    private void updatePausePlay() {
        if (mPauseButton == null)
            return;

        if (mPlayer.isPlaying())
            mPauseButton.setImageResource(R.drawable.icon_video_pause_middle);
        else
            mPauseButton.setImageResource(R.drawable.icon_video_play_middle);
    }

    private void doPauseResume() {
        if (mPlayer.isPlaying())
            mPlayer.pause();
        else
            mPlayer.start();
        updatePausePlay();
    }

    private SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onStartTrackingTouch(SeekBar bar) {
            mDragging = true;
            show(3600000);
            mHandler.removeMessages(SHOW_PROGRESS);
            if (mInstantSeeking)
                mAM.setStreamMute(AudioManager.STREAM_MUSIC, true);
        }

        @Override
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser) {
                return;
            }


            final long newposition = (long) (mDuration * progress) / 1000;
            String time = generateTime(newposition);
            if (mInstantSeeking) {
                mHandler.removeCallbacks(mLastSeekBarRunnable);
                mLastSeekBarRunnable = new Runnable() {
                    @Override
                    public void run() {
                        mPlayer.seekTo(newposition);
                    }
                };
                mHandler.postDelayed(mLastSeekBarRunnable, SEEK_TO_POST_DELAY_MILLIS);
            }
            if (mCurrentTime != null)
                mCurrentTime.setText(time);
        }

        @Override
        public void onStopTrackingTouch(SeekBar bar) {
            if (!mInstantSeeking)
                mPlayer.seekTo(mDuration * bar.getProgress() / 1000);

            show(sDefaultTimeout);
            mHandler.removeMessages(SHOW_PROGRESS);
            mAM.setStreamMute(AudioManager.STREAM_MUSIC, false);
            mDragging = false;
            mHandler.sendEmptyMessageDelayed(SHOW_PROGRESS, 1000);
        }
    };

    private OnClickListener mRewListener = new OnClickListener() {
        public void onClick(View v) {
            if (mOnClickSpeedAdjustListener != null) {
                mOnClickSpeedAdjustListener.onClickSlower();
            }
            show(sDefaultTimeout);
        }
    };

    private OnClickListener mFfwdListener = new OnClickListener() {
        public void onClick(View v) {
            if (mOnClickSpeedAdjustListener != null) {
                mOnClickSpeedAdjustListener.onClickFaster();
            }
            show(sDefaultTimeout);
        }
    };

    /**
     * Set the view that acts as the anchor for the control view.
     * <p>
     * - This can for example be a VideoView, or your Activity's main view.
     * - AudioPlayer has no anchor view, so the view parameter will be null.
     *
     * @param view The view to which to anchor the controller when it is visible.
     */
    @Override
    public void setAnchorView(View view) {
    }

    @Override
    public void setMediaPlayer(MediaPlayerControl player) {
        mPlayer = player;
        updatePausePlay();
    }

    @Override
    public void show() {
        show(sDefaultTimeout);
    }

    /**
     * Show the controller on screen. It will go away automatically after
     * 'timeout' milliseconds of inactivity.
     *
     * @param timeout The timeout in milliseconds. Use 0 to show the controller until hide() is called.
     */
    @Override
    public void show(int timeout) {
        if (!mShowing) {
            if (mPauseButton != null)
                mPauseButton.requestFocus();
            disableUnsupportedButtons();
            setVisibility(View.VISIBLE);
            mShowing = true;
            if (mShownListener != null)
                mShownListener.onShown();
        }
        updatePausePlay();
        mHandler.sendEmptyMessage(SHOW_PROGRESS);

        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.sendMessageDelayed(mHandler.obtainMessage(FADE_OUT),
                    timeout);
        }
    }

    @Override
    public boolean isShowing() {
        return mShowing;
    }

    @Override
    public void hide() {
        if (mShowing) {
            setVisibility(View.GONE);
            mShowing = false;
            if (mHiddenListener != null)
                mHiddenListener.onHidden();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mPauseButton != null) {
            mPauseButton.setEnabled(enabled);
        }
        if (mProgress != null && !mDisableProgress)
            mProgress.setEnabled(enabled);
        disableUnsupportedButtons();
        super.setEnabled(enabled);
    }

    public long getSeekPosition() {
        return mSeekPosition;
    }

}
