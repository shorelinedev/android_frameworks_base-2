/*
 * Copyright (C) 2013 ParanoidAndroid.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.halo;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.INotificationManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.animation.Keyframe;
import android.animation.PropertyValuesHolder;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.Vibrator;
import android.os.ServiceManager;
import android.provider.Settings;
import android.service.notification.INotificationListener;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.TranslateAnimation;
import android.animation.TimeInterpolator;
import android.view.Display;
import android.view.View;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.SoundEffectConstants;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar.NotificationClicker;
import android.service.notification.StatusBarNotification;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.notification.NotificationHelper;
import com.android.systemui.statusbar.phone.Ticker;

public class Halo extends FrameLayout implements Ticker.TickerCallback {

    public static final String TAG = "HaloLauncher";

    private static final int STATE_FIRST_RUN = 0;
    private static final int STATE_IDLE = 1;
    private static final int STATE_HIDDEN = 2;
    private static final int STATE_SILENT = 3;
    private static final int STATE_DRAG = 4;
    private static final int STATE_GESTURES = 5;

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_TASK = 1;
    private static final int GESTURE_UP1 = 2;
    private static final int GESTURE_UP2 = 3;
    private static final int GESTURE_DOWN1 = 4;
    private static final int GESTURE_DOWN2 = 5;

    private Context mContext;
    private PackageManager mPm;
    private final PowerManager mBoost;

    private Handler mHandler;
    private BaseStatusBar mBar;
    private WindowManager mWindowManager;
    private Display mDisplay;
    private Vibrator mVibrator;
    private LayoutInflater mInflater;
    private INotificationManager mNotificationManager;
    private SettingsObserver mSettingsObserver;
    private GestureDetector mGestureDetector;
    private KeyguardManager mKeyguardManager;
    private BroadcastReceiver mReceiver;
    private NotificationHelper mNotificationHelper;

    private HaloEffect mEffect;
    private WindowManager.LayoutParams mTriggerPos;
    private int mState = STATE_IDLE;
    private int mGesture = GESTURE_NONE;

    private View mRoot;
    private View mContent, mHaloContent;
    private INotificationListener mHaloListener;
    private ComponentName mHaloComponent;
    private NotificationData.Entry mLastNotificationEntry = null;
    private NotificationData.Entry mCurrentNotficationEntry = null;
    private NotificationData.Entry mTaskEntry = null;
    private NotificationData mNotificationData;
    private String mNotificationText = "";

    private Paint mPaintHoloGrey = new Paint();
    private Paint mPaintWhite = new Paint();
    private Paint mPaintHoloRed = new Paint();

    private boolean mAttached = false;
    private boolean isBeingDragged = false;
    private boolean mHapticFeedback;
    private boolean mHaloHide;
    private boolean mFirstStart = true;
    private boolean mInitialized = false;
    private boolean mTickerLeft = true;
    private boolean mTickerUpdated = false;
    private boolean mIsNotificationNew = true;
    private boolean mPingNewcomer = false;
    private boolean mOverX = false;
    private boolean mOverSetting = false;
    private boolean hiddenState = false;
    private boolean statusAnimation = false;
    private boolean mNotifySilent = false;

    private int mIconSize, mIconHalfSize;
    private int mScreenWidth, mScreenHeight;
    private int mMarkerIndex = -1;
    private int mDismissDelay = 100;

    private int oldIconIndex = -1;
    private float initialX = 0;
    private float initialY = 0;
    private float mHaloSize = 1.0f;

    // Halo dock position
    SharedPreferences preferences;
    private String KEY_HALO_POSITION_Y = "halo_position_y";
    private String KEY_HALO_POSITION_X = "halo_position_x";
    private String KEY_HALO_FIRST_RUN = "halo_first_run";


    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.PAC.getUriFor(
                    Settings.PAC.HALO_HIDE), false, this);
            resolver.registerContentObserver(Settings.PAC.getUriFor(
                    Settings.PAC.HALO_NOTIFY_COUNT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HAPTIC_FEEDBACK_ENABLED), false, this);
            resolver.registerContentObserver(Settings.PAC.getUriFor(
                    Settings.PAC.HALO_NOTIFY_SILENT), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            ContentResolver resolver = mContext.getContentResolver();
            mHaloHide =
                    Settings.PAC.getInt(resolver,
                                Settings.PAC.HALO_HIDE, 0) == 1;
            if (!selfChange) {
                //mEffect.wake();
                mBar.restartHalo();
                //mEffect.ping(mPaintHoloGrey, HaloEffect.WAKE_TIME);
                mEffect.nap(HaloEffect.SNAP_TIME + 1000);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.observe();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAttached) {
            mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
            mAttached = false;
        }
    }

    public Halo(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Halo(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mPm = mContext.getPackageManager();
        mBoost = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mNotificationManager = INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        mDisplay = mWindowManager.getDefaultDisplay();
        mGestureDetector = new GestureDetector(mContext, new GestureListener());
        mHandler = new Handler();
        mRoot = this;
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);

        filter.addAction(Intent.ACTION_USER_PRESENT);

        mReceiver = new ScreenReceiver();
        mContext.registerReceiver(mReceiver, filter);

        ContentResolver resolver = mContext.getContentResolver();

        // Init variables
        mHaloHide =
                Settings.PAC.getInt(resolver, Settings.PAC.HALO_HIDE, 0) == 1;
        mHaloSize = Settings.PAC.getFloat(resolver, Settings.PAC.HALO_SIZE, 1.0f);
        mHapticFeedback = Settings.System.getInt(resolver,
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;
        mIconSize = (int)(mContext.getResources()
                                  .getDimensionPixelSize(R.dimen.halo_bubble_size) * mHaloSize);
        mIconHalfSize = mIconSize / 2;
        mTriggerPos = getWMParams();
        mNotifySilent = Settings.PAC.getInt(resolver,
                    Settings.PAC.HALO_NOTIFY_SILENT, 0) == 1;

        // Init colors
        mPaintHoloGrey.setAntiAlias(true);
        mPaintHoloGrey.setColor(0xffbbbbbb);
        mPaintWhite.setAntiAlias(true);
        mPaintWhite.setColor(0xfff0f0f0);
        mPaintHoloRed.setAntiAlias(true);
        mPaintHoloRed.setColor(0xffcc0000);

        // Create effect layer
        mEffect = new HaloEffect(mContext);
        mEffect.setLayerType (View.LAYER_TYPE_HARDWARE, null);
        mEffect.pingMinRadius = mIconHalfSize;
        mEffect.pingMaxRadius = (int)(mIconSize * 1.1f);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                      | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                      | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                      | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
              PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT|Gravity.TOP;
        mWindowManager.addView(mEffect, lp);
    }

    private void initControl() {
        if (mInitialized) return;

        mInitialized = true;

        // Get actual screen size
        mScreenWidth = mEffect.getWidth();
        mScreenHeight = mEffect.getHeight();

        // Halo dock position
        preferences = mContext.getSharedPreferences("Halo", 0);
        int msavePositionX = preferences.getInt(KEY_HALO_POSITION_X, 0);
        int msavePositionY = preferences.getInt(KEY_HALO_POSITION_Y,
                                                    mScreenHeight / 2 - mIconHalfSize);

        if (preferences.getBoolean(KEY_HALO_FIRST_RUN, true)) {
            mState = STATE_FIRST_RUN;
            preferences.edit().putBoolean(KEY_HALO_FIRST_RUN, false).apply();
        }

        if (!mFirstStart) {
            if (msavePositionY < 0) mEffect.setHaloY(0);
            float mTmpHaloY = (float) msavePositionY / mScreenWidth * (mScreenHeight);
            if (msavePositionY > mScreenHeight-mIconSize) {
                mEffect.setHaloY((int)mTmpHaloY);
            } else {
                mEffect.setHaloY(isLandscapeMod() ? msavePositionY : (int)mTmpHaloY);
            }

            if (mState == STATE_HIDDEN || mState == STATE_SILENT) {
                int triggerWidth;
                int newPos = (int)(mTickerLeft
                                ? -mIconSize*0.8f
                                : mScreenWidth - mIconSize*0.2f);
                if (getHaloMsgCount()-getHidden() < 1) {
                    newPos = mTickerLeft ? -mIconSize : mScreenWidth;
                    if (getHidden()==0) {
                        triggerWidth = newPos;
                    } else {
                        triggerWidth = (int)(mTickerLeft
                                                ? -mIconSize*0.8f
                                                : mScreenWidth - mIconSize*0.2f);
                    }
                } else {
                    triggerWidth = newPos;
                }
                updateTriggerPosition(triggerWidth, mEffect.mHaloY);
            } else {
                mEffect.setHaloX(mTickerLeft ? -mIconSize : mScreenWidth);
                mEffect.nap(500);
            }
        } else {
            // Do the startup animations only once
            mFirstStart = false;
            // Halo dock position
            mTickerLeft = msavePositionX == 0 ? true : false;
            updateTriggerPosition(msavePositionX, msavePositionY);
            mEffect.updateResources(mTickerLeft);
            mEffect.setHaloY(msavePositionY);

            // TODO: clean up the nested timers
            // run only once so a low priority
            if (mState == STATE_FIRST_RUN) {
                mEffect.setHaloX(msavePositionX + (mTickerLeft ? -mIconSize : mIconSize));
                mEffect.setHaloOverlay(HaloProperties.Overlay.MESSAGE, 1f);
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        mEffect.wake();
                        mEffect.ticker(mContext.getResources()
                                                .getString(R.string.halo_tutorial1), 0, 3000);
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                mEffect.ticker(mContext.getResources()
                                                .getString(R.string.halo_tutorial2), 0, 3000);
                                mHandler.postDelayed(new Runnable() {
                                    public void run() {
                                        mEffect.ticker(mContext.getResources()
                                                .getString(R.string.halo_tutorial3), 0, 3000);
                                        mHandler.postDelayed(new Runnable() {
                                            public void run() {
                                                mState = STATE_IDLE;
                                                mEffect.nap(0);
                                                mEffect.setHaloOverlay
                                                        (HaloProperties.Overlay.NONE, 0f);
                                            }}, 6000);
                                    }}, 6000);
                            }}, 6000);
                    }}, 1000);
            } else {
                mEffect.setHaloX(msavePositionX);
                mEffect.nap(500);
            }
        }
    }

    private boolean isLandscapeMod() {
        return mScreenWidth < mScreenHeight;
    }

    public void update() {
        if (mEffect != null) mEffect.invalidate();
    }

    private void updateTriggerPosition(int x, int y) {
        try {
            mTriggerPos.x = x;
            mTriggerPos.y = y;
            mWindowManager.updateViewLayout(mRoot, mTriggerPos);
        } catch(Exception e) {
            // Probably some animation still looking to move stuff around
        }
    }

    private void loadLastNotification(boolean includeCurrentDismissible) {
        if (getHaloMsgCount() > 0) {
            mLastNotificationEntry = mNotificationData
                                        .get(getHaloMsgIndex(getHaloMsgCount() - 1, false));
            // If the current notification is dismissible we might want to skip it if so desired
            if (!includeCurrentDismissible) {
                if (getHaloMsgCount() > 1 && mLastNotificationEntry != null &&
                        mCurrentNotficationEntry != null &&
                        mLastNotificationEntry.notification ==
                        mCurrentNotficationEntry.notification) {
                    if (mLastNotificationEntry.notification.isClearable()) {
                        mLastNotificationEntry = mNotificationData
                                                .get(getHaloMsgIndex(getHaloMsgCount()-2, false));
                    }
                } else if (getHaloMsgCount() == 1) {
                    if (mLastNotificationEntry.notification.isClearable()) {
                        // We have one notification left and it is dismissible, clear it...
                        clearTicker();
                        return;
                    }
                }
            }

            if (mLastNotificationEntry.notification != null
                    && mLastNotificationEntry.notification.getNotification() != null
                    && mLastNotificationEntry.notification.getNotification().tickerText != null) {
                mNotificationText = mLastNotificationEntry.notification.getNotification()
                                                          .tickerText.toString();
            }

            tick(mLastNotificationEntry, 0, 0, false, false);
        } else {
            clearTicker();
        }
    }

    public void setStatusBar(BaseStatusBar bar) {
        mBar = bar;
        mHaloComponent = new ComponentName("HaloComponent", "Halo.java");
        mHaloListener = new HaloReceiver();
        try {
            mNotificationManager.registerListener(mHaloListener, mHaloComponent, 0);
        } catch (android.os.RemoteException ex) {
            // failed to register listener
        }
        if (mBar.getTicker() != null) mBar.getTicker().setUpdateEvent(this);
        mNotificationData = mBar.getNotificationData();
        loadLastNotification(true);
        mNotificationHelper = new NotificationHelper(mBar, mContext);
    }

    void launchTask(NotificationData.Entry entry) {
        // Do not launch tasks in hidden state or protected lock screen
        if (mState == STATE_HIDDEN
            || mState == STATE_SILENT
            || (mKeyguardManager.isKeyguardLocked() && mKeyguardManager.isKeyguardSecure())) {
            return;
        }

        if (entry.notification.getNotification().contentIntent == null) {
            String text = mContext.getResources().getString(R.string.status_bar_floating_no_interface);
            int duration = Toast.LENGTH_SHORT;
            Toast.makeText(mContext, text, duration).show();
            return;
        }

        // warm up boost hints
        mBoost.cpuBoost(1500000);

        try {
            ActivityManagerNative.getDefault().resumeAppSwitches();
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
            // ...
        }
        mDismissDelay = 1500;

        NotificationClicker intent = mNotificationHelper.getNotificationClickListenerForHalo(entry);
        intent.onClick(mRoot);
    }

    class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp (MotionEvent event) {
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                float velocityX, float velocityY) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event) {
            if (mState != STATE_DRAG) {
                if (mCurrentNotficationEntry != null) {
                    launchTask(mCurrentNotficationEntry);
                }
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            // Move
            mState = STATE_DRAG;
            mBoost.cpuBoost(1500000);
            return true;
        }
    }

    void resetIcons() {
        final float originalAlpha = mContext.getResources()
                                    .getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
        for (int i = 0; i < mNotificationData.size(); i++) {
            NotificationData.Entry entry = mNotificationData.get(i);
            entry.icon.setAlpha(originalAlpha);
        }
    }

    void setIcon(int index) {
        float originalAlpha = mContext.getResources()
                                    .getFraction(R.dimen.status_bar_icon_drawing_alpha, 1, 1);
        for (int i = 0; i < mNotificationData.size(); i++) {
            NotificationData.Entry entry = mNotificationData.get(i);
            float alpha = index == i ? 1f : originalAlpha;

            // Persistent notification appear muted
            if (!entry.notification.isClearable() && index != i) alpha /= 2;
            entry.icon.setAlpha(alpha);
        }
    }

    private boolean verticalGesture() {
        return (mGesture == GESTURE_UP1
                || mGesture == GESTURE_DOWN1
                || mGesture == GESTURE_UP2
                || mGesture == GESTURE_DOWN2);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // Prevent any kind of interaction while HALO explains itself
        if (mState == STATE_FIRST_RUN) return true;

        mEffect.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);

        final int action = event.getAction();
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                // Stop HALO from moving around, unschedule sleeping patterns
                if (mState != STATE_GESTURES) mEffect.unscheduleSleep();

                mMarkerIndex = -1;
                oldIconIndex = -1;

                resetIcons();

                mGesture = GESTURE_NONE;
                hiddenState = (mState == STATE_HIDDEN || mState == STATE_SILENT);

                if (hiddenState) {
                    mEffect.wake();
                    mEffect.nap(2500);
                    return true;
                }

                initialX = event.getRawX();
                initialY = event.getRawY();
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (hiddenState) break;

                if (mHaloHide) {
                    int c = getHaloMsgCount()-getHidden() < 0
                                                    ? 0
                                                    : getHaloMsgCount()-getHidden();
                    mEffect.animateHaloBatch(0, c, false, 0,
                                            HaloProperties.MessageType.MESSAGE);
                }

                resetIcons();
                mBar.setHaloTaskerActive(false, true);
                mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                updateTriggerPosition(mEffect.getHaloX(), mEffect.getHaloY());

                mEffect.killTicker();
                mEffect.unscheduleSleep();

                // Halo dock position
                float mTmpHaloY = (float) mEffect.mHaloY / mScreenHeight * mScreenWidth;
                preferences.edit().putInt(KEY_HALO_POSITION_X, mTickerLeft
                                            ? 0
                                            : mScreenWidth - mIconSize)
                                  .putInt(KEY_HALO_POSITION_Y, isLandscapeMod()
                                            ? mEffect.mHaloY
                                            : (int)mTmpHaloY).apply();

                if (mGesture == GESTURE_TASK) {
                    // Launch tasks
                    if (mTaskEntry != null) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        launchTask(mTaskEntry);
                    }
                    mEffect.nap(100);
                } else if (mGesture == GESTURE_DOWN2) {
                    // Hide & silence
                    playSoundEffect(SoundEffectConstants.CLICK);
                    mEffect.sleep(0, HaloEffect.NAP_TIME / 2, true);

                } else if (mGesture == GESTURE_DOWN1) {
                    // Hide from sight
                    playSoundEffect(SoundEffectConstants.CLICK);
                    mEffect.sleep(0, HaloEffect.NAP_TIME / 2, false);

                } else if (mGesture == GESTURE_UP2) {
                    // Clear all notifications
                    playSoundEffect(SoundEffectConstants.CLICK);
                    try {
                        mDismissDelay = 0;
                        mBar.getService().onClearAllNotifications();
                    } catch (RemoteException ex) {
                        // system process is dead if we're here.
                    }
                    mEffect.nap(HaloEffect.NAP_TIME);
                } else if (mGesture == GESTURE_UP1) {
                    // Dismiss notification
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (mCurrentNotficationEntry != null) {
                        try {
                            mDismissDelay = 0;
                            mBar.getService().onNotificationClear
                                                (mCurrentNotficationEntry.notification.getPackageName(),
                                                 mCurrentNotficationEntry.notification.getTag(),
                                                 mCurrentNotficationEntry.notification.getId());
                        } catch (RemoteException ex) {
                            // system process is dead if we're here.
                        }
                    }
                    mEffect.nap(HaloEffect.NAP_TIME);
                } else {
                    // No gesture, just snap HALO
                    mEffect.snap(0);
                    mEffect.nap(HaloEffect.SNAP_TIME + 1000);
                }

                mState = STATE_IDLE;
                mGesture = GESTURE_NONE;
                break;

            case MotionEvent.ACTION_MOVE:
                if (hiddenState) break;

                float distanceX = initialX-event.getRawX();
                float distanceY = initialY-event.getRawY();
                float initialDistance = (float)Math.sqrt(Math.pow(distanceX, 2)
                                                + Math.pow(distanceY, 2));

                if (mState != STATE_GESTURES) {
                    // Drag
                    if (mState != STATE_DRAG) {
                        if (initialDistance > mIconSize * 0.7f) {
                            mState = STATE_GESTURES;
                            mEffect.wake();
                            mBar.setHaloTaskerActive(true, true);
                            mBoost.cpuBoost(1500000);
                        }
                    } else {
                        int posX = (int)event.getRawX() - mIconHalfSize;
                        int posY = (int)event.getRawY() - mIconHalfSize;
                        if (posX < 0) posX = 0;
                        if (posY < 0) posY = 0;
                        if (posX > mScreenWidth-mIconSize) posX = mScreenWidth-mIconSize;
                        if (posY > mScreenHeight-mIconSize) posY = mScreenHeight-mIconSize;
                        mEffect.setHaloX(posX);
                        mEffect.setHaloY(posY);

                        // Update resources when the side changes
                        boolean oldTickerPos = mTickerLeft;
                        mTickerLeft = (posX + mIconHalfSize < mScreenWidth / 2);
                        if (oldTickerPos != mTickerLeft) {
                            mEffect.updateResources(mTickerLeft);
                        }
                    }
                } else {
                    // We have three basic gestures, one horizontal for switching through tasks and
                    // two vertical for dismissing tasks or making HALO fall asleep
                    int deltaX = (int)(mTickerLeft
                                        ? event.getRawX()
                                        : mScreenWidth - event.getRawX());
                    int deltaY = (int)(mEffect.getHaloY() - event.getRawY() + mIconSize);
                    int horizontalThreshold = (int)(mIconSize * 1.5f);
                    int verticalThreshold = (int)(mIconSize * 0.25f);
                    int verticalSteps = (int)(mIconSize * 0.7f);
                    String gestureText = mNotificationText;
                    int oldGesture = GESTURE_NONE;

                    // Switch icons
                    if (deltaX > horizontalThreshold) {
                        if (mGesture != GESTURE_TASK)
                                mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);

                        oldGesture = mGesture;
                        mGesture = GESTURE_TASK;

                        deltaX -= horizontalThreshold;
                        if (mNotificationData != null && getHaloMsgCount() > 0) {
                            int items = getHaloMsgCount();

                            // This will be the length we are going to use
                            int indexLength = ((int)(mScreenWidth * 0.85f) - mIconSize) / items;

                            // Set a standard (max) distance for markers.
                            indexLength = indexLength > 120 ? 120 : indexLength;

                            // Calculate index
                            mMarkerIndex = mTickerLeft
                                            ? (items - deltaX / indexLength) - 1
                                            : (deltaX / indexLength);

                            // Watch out for margins!
                            if (mMarkerIndex >= items) mMarkerIndex = items - 1;
                            if (mMarkerIndex < 0) mMarkerIndex = 0;
                        }

                    // Up & down gestures
                    } else if (Math.abs(deltaY) > verticalThreshold * 2) {
                        mMarkerIndex = -1;

                        boolean gestureChanged = false;
                        final int deltaIndex =
                                    (Math.abs(deltaY) - verticalThreshold) / verticalSteps;

                        if (deltaIndex < 1 && mGesture != GESTURE_NONE) {
                            // Dead zone buffer to prevent accidental notifiction dismissal
                            gestureChanged = true;
                            mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                            if (verticalGesture()) gestureText = "";
                            oldGesture = mGesture;
                            mGesture = GESTURE_NONE;
                        } else if (deltaY > 0) {
                            if (deltaIndex == 1 && mGesture != GESTURE_UP1) {
                                oldGesture = mGesture;
                                mGesture = GESTURE_UP1;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(HaloProperties.Overlay.DISMISS, 1f);
                                gestureText = mContext.getResources()
                                                        .getString(R.string.halo_dismiss);
                            } else if (deltaIndex > 1 && mGesture != GESTURE_UP2) {
                                oldGesture = mGesture;
                                mGesture = GESTURE_UP2;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(HaloProperties.Overlay.CLEAR_ALL, 1f);
                                gestureText = mContext.getResources()
                                                        .getString(R.string.halo_clear_all);
                            }
                        } else {
                            if (deltaIndex == 1 && mGesture != GESTURE_DOWN1) {
                                oldGesture = mGesture;
                                mGesture = GESTURE_DOWN1;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(mTickerLeft
                                        ? HaloProperties.Overlay.BACK_LEFT
                                        : HaloProperties.Overlay.BACK_RIGHT, 1f);
                                gestureText = mContext.getResources()
                                                        .getString(R.string.halo_hide);
                            } else if (deltaIndex > 1 && mGesture != GESTURE_DOWN2) {
                                oldGesture = mGesture;
                                mGesture = GESTURE_DOWN2;
                                gestureChanged = true;
                                mEffect.setHaloOverlay(mTickerLeft
                                        ? HaloProperties.Overlay.SILENCE_LEFT
                                        : HaloProperties.Overlay.SILENCE_RIGHT, 1f);
                                gestureText = mContext.getResources()
                                                        .getString(R.string.halo_silence);
                            }
                        }

                        if (gestureChanged) {
                            mMarkerIndex = -1;
                            mEffect.ticker(gestureText, 0, 250);
                            if (mHapticFeedback) mVibrator.vibrate(10);
                            gestureChanged = false;
                        }

                    } else {
                        mMarkerIndex = -1;

                        if (mGesture != GESTURE_NONE) {
                            mEffect.setHaloOverlay(HaloProperties.Overlay.NONE, 0f);
                            if (verticalGesture()) mEffect.killTicker();
                        }
                        oldGesture = mGesture;
                        mGesture = GESTURE_NONE;
                    }
                    // If the marker index changed, tick
                    if (mMarkerIndex != oldIconIndex) {
                        oldIconIndex = mMarkerIndex;

                        // Make a tiny pop if not so many icons are present
                        if (mHapticFeedback && getHaloMsgCount() < 10) mVibrator.vibrate(10);

                        int iconIndex = getHaloMsgIndex(mMarkerIndex, false);
                        try {
                            // Tick the first item only if we were tasking before
                            if (iconIndex == -1
                                && !verticalGesture()
                                && oldGesture == GESTURE_TASK) {
                                mTaskEntry = null;
                                resetIcons();
                                tick(mLastNotificationEntry, 0, -1, false, true);
                            } else {
                                setIcon(iconIndex);
                                mTaskEntry = mNotificationData.get(iconIndex);
                                tick(mTaskEntry, 0, -1, false, true);
                            }
                        } catch (Exception e) {
                            // IndexOutOfBoundsException
                        }
                    }
                }
                mEffect.invalidate();
                break;
        }
        return false;
    }

    public void cleanUp() {
        // Remove pending tasks, if we can
        mEffect.unscheduleSleep();
        mHandler.removeCallbacksAndMessages(null);
        // Kill callback
        mBar.getTicker().setUpdateEvent(null);
        // Flag tasker
        mBar.setHaloTaskerActive(false, false);
        // Kill the effect layer
        if (mEffect != null) mWindowManager.removeView(mEffect);
        // Remove resolver
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        mContext.unregisterReceiver(mReceiver);
    }

    class HaloEffect extends HaloProperties {

        public static final int WAKE_TIME = 300;
        public static final int SNAP_TIME = 300;
        public static final int NAP_TIME = 1000;
        public static final int SLEEP_TIME = 2000;
        public static final int PING_TIME = 1500;
        public static final int TICKER_HIDE_TIME = 2500;
        public static final int NAP_DELAY = 4500;
        public static final int SLEEP_DELAY = 6500;
        public static final int EXTRA_SLEEP_TIME = 2500;

        private Context mContext;
        private Paint mPingPaint;
        private int pingRadius = 0;
        private int mPingX, mPingY;
        protected int pingMinRadius = 0;
        protected int pingMaxRadius = 0;
        private boolean mPingAllowed = true;

        private Bitmap mMarker, mMarkerT, mMarkerB;
        private Bitmap mBigRed, mHaloSetting;
        private Bitmap mStatusBubbleT, mStatusBubbleB, mStatusBubbleS;
        private Paint mMarkerPaint = new Paint();
        private Paint xPaint = new Paint();
        private Paint sPaint = new Paint();
        private Paint mHaloTime = new Paint();
        private Paint mHaloStatusText = new Paint();
        private Paint mHaloBattery = new Paint();
        private Paint mHaloSignal = new Paint();

        CustomObjectAnimator xAnimator = new CustomObjectAnimator(this);
        CustomObjectAnimator sAnimator = new CustomObjectAnimator(this);
        CustomObjectAnimator timeAnimator = new CustomObjectAnimator(this);
        CustomObjectAnimator batteryAnimator = new CustomObjectAnimator(this);
        CustomObjectAnimator signalAnimator = new CustomObjectAnimator(this);
        CustomObjectAnimator tickerAnimator = new CustomObjectAnimator(this);

        public HaloEffect(Context context) {
            super(context);

            mContext = context;
            setWillNotDraw(false);
            setDrawingCacheEnabled(false);

            mMarker = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker);
            mMarkerT = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_t);
            mMarkerB = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.halo_marker_b);

            // TODO: cache bitmaps
            if (mHaloSize != 1.0f) {
                mMarker = Bitmap.createScaledBitmap(mMarker,
                        (int)(mMarker.getWidth() * mHaloSize),
                        (int)(mMarker.getHeight() * mHaloSize), true);
                mMarkerT = Bitmap.createScaledBitmap(mMarkerT,
                        (int)(mMarkerT.getWidth() * mHaloSize),
                        (int)(mMarkerT.getHeight() * mHaloSize), true);
                mMarkerB = Bitmap.createScaledBitmap(mMarkerB,
                        (int)(mMarkerB.getWidth() * mHaloSize),
                        (int)(mMarkerB.getHeight() * mHaloSize), true);
            }

            mMarkerPaint.setAntiAlias(true);
            mMarkerPaint.setAlpha(0);

            updateResources(mTickerLeft);
        }

        void getRawPoint(MotionEvent ev, int index, PointF point) {
            final int location[] = { 0, 0 };
            mRoot.getLocationOnScreen(location);

            final int location_effect[] = { 0, 0 };
            getLocationOnScreen(location_effect);

            float x=ev.getX(index);
            float y=ev.getY(index);

            double angle=Math.toDegrees(Math.atan2(y, x));
            angle+=mRoot.getRotation();

            final float length=PointF.length(x,y);

            x=(float)(length*Math.cos(Math.toRadians(angle)))+location[0];
            y=(float)(length*Math.sin(Math.toRadians(angle)))+location[1];

            point.set((int)x,(int)y - location_effect[1]);
        }

        boolean browseView(PointF loc, Rect parent, View v) {
            int posX = (int)loc.x;
            int posY = (int)loc.y; // - mIconHalfSize / 2;

            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup)v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View sv = vg.getChildAt(i);
                    if (browseView(loc, parent, sv)) return true;
                }
            } else {
                if (v.isClickable()) {
                    Rect r = new Rect();
                    v.getHitRect(r);

                    int left = tickerX + parent.left + r.left;
                    int top = tickerY + parent.top + r.top;
                    int right = tickerX + parent.left + r.right;
                    int bottom = tickerY + parent.top + r.bottom;

                    if (posX > left && posX < right && posY > top && posY < bottom) {
                        v.performClick();
                        playSoundEffect(SoundEffectConstants.CLICK);
                        if (mHapticFeedback) mVibrator.vibrate(25);
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            int index = event.getActionIndex();
            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP
                    && index != 0 ) {
                if (mCurrentNotficationEntry != null
                    && mCurrentNotficationEntry.haloContent != null) {
                    Rect rootRect = new Rect();
                    mHaloTickerContent.getHitRect(rootRect);

                    PointF point = new PointF();
                    getRawPoint(event, index, point);
                    browseView(point, rootRect, mCurrentNotficationEntry.haloContent);
                }
            }
            return false;
        }

        @Override
        public void onSizeChanged(int w, int h, int oldw, int oldh) {
            onConfigurationChanged(null);
        }

        @Override
        public void onConfigurationChanged(Configuration newConfiguration) {
            // This will reset the initialization flag
            mInitialized = false;
            // Generate a new content bubble
            updateResources(mTickerLeft);
        }

        @Override
        protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
            super.onLayout (changed, left, top, right, bottom);
            // We have our effect-layer, now let's kickstart HALO
            initControl();
        }

        public void killTicker() {
            tickerAnimator.animate(ObjectAnimator.ofFloat(this, "haloContentAlpha", 0f)
                                                 .setDuration(250),
                                                 new DecelerateInterpolator(), null);
        }

        public void ticker(String tickerText, int delay, int startDuration) {
            if (tickerText == null || tickerText.equals("")) {
                killTicker();
                return;
            }

            setHaloContentHeight((int)(mContext.getResources()
                                               .getDimensionPixelSize
                                                    (R.dimen.notification_min_height) * 0.6f));
            mHaloTickerContent.setVisibility(View.GONE);
            mHaloTextView.setVisibility(View.VISIBLE);
            mHaloTextView.setText(tickerText);
            updateResources(mTickerLeft);

            float total = TICKER_HIDE_TIME + startDuration + 1000;
            PropertyValuesHolder tickerUpFrames = PropertyValuesHolder
                                                    .ofKeyframe("haloContentAlpha",
                                                    Keyframe.ofFloat(0f, mHaloTextView.getAlpha()),
                                                    Keyframe.ofFloat(0.1f, 1f),
                                                    Keyframe.ofFloat(0.95f, 1f),
                                                    Keyframe.ofFloat(1f, 0f));
            tickerAnimator.animate(ObjectAnimator.ofPropertyValuesHolder(this, tickerUpFrames)
                                                 .setDuration((int)total),
                                                 new DecelerateInterpolator(), null, delay, null);
        }

        public void ticker(int delay, int startDuration) {

            setHaloContentHeight(mContext.getResources()
                                        .getDimensionPixelSize(R.dimen.notification_min_height));
            mHaloTickerContent.setVisibility(View.VISIBLE);
            mHaloTextView.setVisibility(View.GONE);
            updateResources(mTickerLeft);

            if (startDuration != -1) {
                // Finite tiker
                float total = TICKER_HIDE_TIME + startDuration + 1000;
                PropertyValuesHolder tickerUpFrames = PropertyValuesHolder
                                                    .ofKeyframe("haloContentAlpha",
                                                    Keyframe.ofFloat(0f, mHaloTextView.getAlpha()),
                                                    Keyframe.ofFloat(0.1f, 1f),
                                                    Keyframe.ofFloat(0.95f, 1f),
                                                    Keyframe.ofFloat(1f, 0f));
                tickerAnimator.animate(ObjectAnimator.ofPropertyValuesHolder(this, tickerUpFrames)
                                                    .setDuration((int)total),
                                                    new DecelerateInterpolator(),
                                                    null, delay, null);
            } else {
                // Infinite ticker (until killTicker() is called)
                tickerAnimator.animate(ObjectAnimator.ofFloat(this, "haloContentAlpha", 1f)
                                                    .setDuration(250),
                    new DecelerateInterpolator(), null, delay, null);
            }
        }

        public void ping(final Paint paint, final long delay) {
            if ((!mPingAllowed && paint != mPaintHoloRed)
                    && mGesture != GESTURE_TASK) return;

            mHandler.postDelayed(new Runnable() {
                public void run() {
                    mPingAllowed = false;

                    mPingX = mHaloX + mIconHalfSize;
                    mPingY = mHaloY + mIconHalfSize;

                    mPingPaint = paint;

                    CustomObjectAnimator pingAnimator = new CustomObjectAnimator(mEffect);
                    pingAnimator.animate(ObjectAnimator.ofInt(mPingPaint, "alpha", 200, 0)
                                                       .setDuration(PING_TIME),
                            new DecelerateInterpolator(), new AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    pingRadius = (int)((pingMaxRadius - pingMinRadius) *
                                            animation.getAnimatedFraction()) + pingMinRadius;
                                    postInvalidate();
                                }});

                    // prevent ping spam
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            mPingAllowed = true;
                        }}, PING_TIME / 2);

                }}, delay);
        }

        CustomObjectAnimator snapAnimator = new CustomObjectAnimator(this);

        public void wake() {
            unscheduleSleep();
            if (mState == STATE_HIDDEN || mState == STATE_SILENT) mState = STATE_IDLE;
            int newPos = mTickerLeft ? 0 : mScreenWidth - mIconSize;
            updateTriggerPosition(newPos, mHaloY);
            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos)
                                               .setDuration(WAKE_TIME),
                    new DecelerateInterpolator(), null);
        }

        public void snap(long delay) {
            int newPos = mTickerLeft ? 0 : mScreenWidth - mIconSize;
            updateTriggerPosition(newPos, mHaloY);
            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos)
                                               .setDuration(SNAP_TIME),
                    new DecelerateInterpolator(), null, delay, null);
        }

        public void refresh() {
            int newPos;
            final int triggerWidth;

            int haloCounterType = Settings.PAC.getInt(mContext.getContentResolver(),
                    Settings.PAC.HALO_NOTIFY_COUNT, 4);

            if (haloCounterType == 2 || haloCounterType == 4) {
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        if (mState != STATE_GESTURES && mState != STATE_DRAG) {
                            final int c = getHaloMsgCount()-getHidden() < 0
                                                ? 0
                                                : getHaloMsgCount()-getHidden();
                            mEffect.animateHaloBatch(0, mHaloHide ? 0 : c, false, 3000,
                                                    HaloProperties.MessageType.MESSAGE);
                        }
                    }
                }, 2000);
            }

            // Halo is hidden
            if (mHaloX == -mIconSize || mHaloX == mScreenWidth) {
                newPos = (int)(mTickerLeft
                                    ? -mIconSize * 0.8f
                                    : mScreenWidth - mIconSize * 0.2f);
                triggerWidth = newPos;
                if (getHaloMsgCount() - getHidden() < 1) {
                    updateTriggerPosition(triggerWidth, mHaloY);
                    return;
                }
                snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(NAP_TIME),
                        new DecelerateInterpolator(), null, 0, new Runnable() {
                    public void run() {
                        updateTriggerPosition(triggerWidth, mHaloY);
                    }});
            }
        }

        public void nap(long delay) {
            int newPos;
            final int triggerWidth;

            if (mHaloHide) {
                newPos = (int)(mTickerLeft
                                    ? -mIconSize*0.8f
                                    : mScreenWidth - mIconSize*0.2f);
                if (getHaloMsgCount()-getHidden() < 1) {
                    newPos = mTickerLeft ? -mIconSize : mScreenWidth;
                    if (getHidden()==0) {
                        triggerWidth = newPos;
                    } else {
                        triggerWidth = (int)(mTickerLeft
                                                ? -mIconSize*0.8f
                                                : mScreenWidth - mIconSize*0.2f);
                    }
                } else {
                    triggerWidth = newPos;
                }
            } else {
                newPos = mTickerLeft ? -mIconHalfSize : mScreenWidth - mIconHalfSize;
                triggerWidth = newPos;
            }

            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(NAP_TIME),
                    new DecelerateInterpolator(), null, delay, new Runnable() {
                public void run() {
                    updateTriggerPosition(triggerWidth, mHaloY);
                }});
            int haloCounterType = Settings.PAC.getInt(mContext.getContentResolver(),
                    Settings.PAC.HALO_NOTIFY_COUNT, 4);

            if (haloCounterType == 2 || haloCounterType == 4) {
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        if (mState != STATE_GESTURES && mState != STATE_DRAG) {
                            final int c = getHaloMsgCount()-getHidden() < 0
                                                ? 0
                                                : getHaloMsgCount()-getHidden();
                            mEffect.animateHaloBatch(0, mHaloHide ? 0 : c, false, 3000,
                                                    HaloProperties.MessageType.MESSAGE);
                        }
                    }
                }, 2000);
            }
        }

        public void sleep(long delay, int speed, final boolean silent) {
            int newPos;
            final int triggerWidth;

            newPos = (int)(mTickerLeft
                                ? -mIconSize*0.8f
                                : mScreenWidth - mIconSize*0.2f);
            if (getHaloMsgCount()-getHidden() < 1) {
                newPos = mTickerLeft ? -mIconSize : mScreenWidth;
                if (getHidden()==0) {
                    triggerWidth = newPos;
                } else {
                    triggerWidth = (int)(mTickerLeft
                                            ? -mIconSize*0.8f
                                            : mScreenWidth - mIconSize*0.2f);
                }
            } else {
                triggerWidth = newPos;
            }

            snapAnimator.animate(ObjectAnimator.ofInt(this, "haloX", newPos).setDuration(speed),
                    new DecelerateInterpolator(), null, delay, new Runnable() {
                public void run() {
                    mState = silent ? STATE_SILENT : STATE_HIDDEN;
                    updateTriggerPosition(triggerWidth, mHaloY);
                }
            });
        }

        public void unscheduleSleep() {
            snapAnimator.cancel(true);
        }

        CustomObjectAnimator contentYAnimator = new CustomObjectAnimator(this);
        public void slideContent(int duration, int y) {
            contentYAnimator.animate(ObjectAnimator.ofInt(this, "HaloContentY", y)
                            .setDuration(duration),
                    new DecelerateInterpolator(), null);
        }

        int tickerX, tickerY;

        @Override
        protected void onDraw(Canvas canvas) {
            int state;

            // Ping
            if (mPingPaint != null) {
                canvas.drawCircle(mPingX, mPingY, pingRadius, mPingPaint);
            }

            // Content
            final int tickerHeight = mHaloTickerWrapper.getMeasuredHeight();
            int ch = mGesture == GESTURE_TASK ? 0 : tickerHeight / 2;
            int cw = mHaloTickerWrapper.getMeasuredWidth();
            int y = mHaloY + mIconHalfSize - ch;

            if (mGesture == GESTURE_TASK) {
                if (mHaloY < mIconHalfSize) {
                    y = y + (int)(mIconSize * 0.20f);
                } else {
                    y = y - mIconSize;
                }
            }

            int x = mHaloX + mIconSize + (int)(mIconSize * 0.1f);
            if (!mTickerLeft) {
                x = mHaloX - cw - (int)(mIconSize * 0.1f);
            }

            // Horizontal Marker
            if (mGesture == GESTURE_TASK) {
                if (y > 0 && mNotificationData != null && getHaloMsgCount() > 0) {
                    int pulseY = mHaloY + mIconHalfSize - mMarker.getHeight() / 2;
                    int items = getHaloMsgCount();
                    int indexLength = ((int)(mScreenWidth * 0.85f) - mIconSize) / items;

                    indexLength = indexLength > 120 ? 120 : indexLength;

                    for (int i = 0; i < items; i++) {
                        float pulseX = mTickerLeft
                                ? (mIconSize * 1.3f + indexLength * i)
                                : (mScreenWidth - mIconSize
                                    * 1.3f - indexLength
                                    * i - mMarker.getWidth());
                        boolean markerState = mTickerLeft
                                                ? mMarkerIndex >= 0 && i < items-mMarkerIndex
                                                : i <= mMarkerIndex;
                        mMarkerPaint.setAlpha(markerState ? 255 : 100);
                        canvas.drawBitmap(mMarker, pulseX, pulseY, mMarkerPaint);
                    }
                }
            }

            // Vertical Markers
            if (verticalGesture()) {
                int xPos = mHaloX + mIconHalfSize - mMarkerT.getWidth() / 2;

                mMarkerPaint.setAlpha(mGesture == GESTURE_UP1 ? 255 : 100);
                int yTop = (int)(mHaloY - (mIconSize * 0.25f) - mMarkerT.getHeight() / 2);
                canvas.drawBitmap(mMarkerT, xPos, yTop, mMarkerPaint);

                mMarkerPaint.setAlpha(mGesture == GESTURE_UP2 ? 255 : 100);
                yTop = yTop - (int)(mIconSize * 0.7f);
                canvas.drawBitmap(mMarkerT, xPos, yTop, mMarkerPaint);

                mMarkerPaint.setAlpha(mGesture == GESTURE_DOWN1 ? 255 : 100);
                int yButtom = (int)(mHaloY + mIconSize
                                    + (mIconSize * 0.25f) - mMarkerT.getHeight() / 2);
                canvas.drawBitmap(mMarkerB, xPos, yButtom, mMarkerPaint);

                mMarkerPaint.setAlpha(mGesture == GESTURE_DOWN2 ? 255 : 100);
                yButtom = yButtom + (int)(mIconSize * 0.7f);
                canvas.drawBitmap(mMarkerB, xPos, yButtom, mMarkerPaint);
            }

            if (mState == STATE_DRAG) {
                setHaloContentY(y);
            } else {
                // Move content when ...
                // 1. the calculated Y position is off
                // 2. the content-animator is not running or we're in tasking state
                if (y != getHaloContentY()
                    && (!contentYAnimator.isRunning() || verticalGesture())) {
                    setHaloContentBackground(mTickerLeft,
                    mGesture == GESTURE_TASK && mHaloY > mIconHalfSize
                            ? HaloProperties.ContentStyle.CONTENT_DOWN
                            : HaloProperties.ContentStyle.CONTENT_UP);
                    int duration = !verticalGesture() ? 300 : 0;
                    slideContent(duration, y);
                    int msgAnimation = Settings.PAC.getInt(mContext.getContentResolver(),
                                            Settings.PAC.HALO_MSGBOX_ANIMATION, 2);
                }
            }

            if (getHaloContentAlpha() > 0.0f) {
                state = canvas.save();
                tickerX = x;
                tickerY = getHaloContentY();
                canvas.translate(x, getHaloContentY());
                mHaloContentView.draw(canvas);
                canvas.restoreToCount(state);
            }

            // Bubble
            state = canvas.save();
            canvas.translate(mHaloX, mHaloY);
            mHaloBubble.draw(canvas);
            canvas.restoreToCount(state);

            // Number
            if (mState == STATE_IDLE || mState == STATE_GESTURES) {
                state = canvas.save();
                canvas.translate(mTickerLeft
                        ? mHaloX + mIconSize - mHaloNumber.getMeasuredWidth()
                        : mHaloX, mHaloY);
                mHaloNumberView.draw(canvas);
                canvas.restoreToCount(state);
            }
        }
    }

    public WindowManager.LayoutParams getWMParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mIconSize,
                mIconSize,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT|Gravity.TOP;
        return lp;
    }

    void clearTicker() {
        mEffect.mHaloIcon.setImageDrawable(null);
        mEffect.msgNumberAlphaAnimator.cancel(true);
        mEffect.msgNumberFlipAnimator.cancel(true);
        mEffect.tickerAnimator.cancel(true);
        mEffect.mHaloNumber.setAlpha(0f);
        mEffect.mHaloNumberIcon.setAlpha(0f);
        mEffect.mHaloNumberContainer.setAlpha(0f);
        mCurrentNotficationEntry = null;
        mEffect.killTicker();
        mEffect.updateResources(mTickerLeft);
        mEffect.invalidate();
    }

    void tick(NotificationData.Entry entry, int delay, int duration,
                boolean alwaysFlip, boolean showContent) {
        if (entry == null) {
            clearTicker();
            return;
        }

        StatusBarNotification notification = entry.notification;
        Notification n = notification.getNotification();

        // Deal with the intent
        mCurrentNotficationEntry = entry;

        // set the avatar
        mEffect.setHaloOverlay(HaloProperties.Overlay.NONE,0f);
        mEffect.mHaloIcon.setImageDrawable(new BitmapDrawable(mContext.getResources(),
                                                    entry.getRoundIcon()));

        if (showContent && mState != STATE_SILENT) {
            if (entry.haloContent != null) {
                try {
                    ((ViewGroup)mEffect.mHaloTickerContent).removeAllViews();
                    ((ViewGroup)mEffect.mHaloTickerContent).addView(entry.haloContent);
                    mEffect.ticker(delay, duration);
                } catch(Exception e) {
                    // haloContent had a view already? Let's give it one last chance ...
                    try {
                        mBar.prepareHaloNotification(entry, notification, false);
                        if (entry.haloContent != null) {
                            ((ViewGroup)mEffect.mHaloTickerContent).addView(entry.haloContent);
                        }
                        mEffect.ticker(delay, duration);
                    } catch(Exception ex) {
                        // Screw it, we're going with a simple text
                        mEffect.ticker(mNotificationText, delay, duration);
                    }
                }
            }
        }

        mEffect.invalidate();
        if (!mPingNewcomer) return;

        // Set Number
        HaloProperties.MessageType msgType;
        if (entry.notification.getPackageName().equals("com.paranoid.halo")) {
            msgType = HaloProperties.MessageType.PINNED;
        } else if (!entry.notification.isClearable()) {
            msgType = HaloProperties.MessageType.PINNED;
        } else {
            msgType = HaloProperties.MessageType.MESSAGE;
        }

        mEffect.animateHaloBatch(n.number, -1, alwaysFlip, delay, msgType);
    }

    public void updateTicker(StatusBarNotification notification) {
        loadLastNotification(true);
    }

    // This is the android ticker callback
    public void updateTicker(StatusBarNotification notification, String text) {
        mTickerUpdated = true;
        boolean allowed = isPackageAllowedForHalo(notification);
        if (allowed) {
            for (int i = 0; i < mNotificationData.size(); i++) {
                NotificationData.Entry entry = mNotificationData.get(i);
                if (entry.notification.toString().equals(notification.toString())) {
                    // No intent, no tick ...
                    if (entry.notification.getNotification().contentIntent == null) return;

                    mIsNotificationNew = true;
                    if (mLastNotificationEntry != null
                        && notification.toString()
                                        .equals(mLastNotificationEntry.notification.toString())) {
                        // Ok, this is the same notification
                        // Let's give it a chance though, if the text has changed we allow it
                        mIsNotificationNew = !mNotificationText.equals(text);
                    }
                    if (mIsNotificationNew) {
                        mNotificationText = text;
                        mLastNotificationEntry = entry;
                        if (mState != STATE_FIRST_RUN) {
                            if (mState == STATE_IDLE || mState == STATE_HIDDEN) {
                                if (mState == STATE_HIDDEN) clearTicker();
                                mEffect.wake();
                                mEffect.nap(HaloEffect.NAP_DELAY + HaloEffect.WAKE_TIME * 2);
                            } else if (mHaloHide && mState == STATE_SILENT) {
                                mEffect.sleep(HaloEffect.WAKE_TIME * 3,
                                                HaloEffect.SLEEP_TIME, true);
                            }
                            boolean showMsgBox = Settings.PAC
                                                         .getInt(mContext.getContentResolver(),
                                                         Settings.PAC.HALO_MSGBOX, 1) == 1;
                            mPingNewcomer = true;
                            tick(entry, HaloEffect.WAKE_TIME * 2, 1000, true, showMsgBox);

                            // Pop while not tasking, only if notification is certified fresh
                            if (mGesture != GESTURE_TASK && mState != STATE_SILENT) {
                                mEffect.ping(mPaintHoloGrey, HaloEffect.WAKE_TIME * 2);
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    public int getHaloMsgCount() {
        int msgs = 0;
        StatusBarNotification notification;

        for (int i = 0; i < mNotificationData.size(); i++) {
            notification = mNotificationData.get(i).notification;
            if (!isPackageAllowedForHalo(notification)) continue;
            msgs += 1;
        }
        return msgs;
    }

    public int getHaloMsgIndex(int index, boolean notifyOnUnlock) {
        int msgIndex = 0;
        StatusBarNotification notification;

        for (int i = 0; i < mNotificationData.size(); i++) {
            notification = mNotificationData.get(i).notification;
            //ignore blacklisted notifications
            if (!isPackageAllowedForHalo(notification)) continue;
            //if notifying the user on unlock, ignore persistent notifications
            if (notifyOnUnlock && !notification.isClearable()) continue;

            if (msgIndex == index) return i;

            msgIndex += 1;
        }
        return -1;
    }

    public int getHidden() {
        int ignore = 0;
        boolean allowed = false;
        boolean persistent = false;

        for (int i = 0; i < mNotificationData.size(); i++ ) {
            NotificationData.Entry entry = mNotificationData.get(i);
            StatusBarNotification statusNotify = entry.notification;
            if (statusNotify == null) continue;

            allowed = isPackageAllowedForHalo(mNotificationData.get(i).notification);
            persistent = !mNotificationData.get(i).notification.isClearable();
            // persistent notifications that were not blacklisted and pinned apps
            boolean hide = (statusNotify.getPackageName().equals("com.paranoid.halo")
                            || (allowed && persistent));
            if (hide) ignore++;
        }
        return ignore;
    }

    private boolean isPackageAllowedForHalo(StatusBarNotification notification) {
        try {
            return mNotificationManager
                        .isPackageAllowedForHalo(notification.getPackageName());
        } catch (android.os.RemoteException ex) {
            // System is dead
            return false;
        }
    }

    private class HaloReceiver extends INotificationListener.Stub {

        public HaloReceiver() {
        }

        @Override
        public void onNotificationPosted(StatusBarNotification notification) throws RemoteException {
            final StatusBarNotification n = notification;
            final boolean allowed = isPackageAllowedForHalo(n);

            if (mKeyguardManager.isKeyguardLocked() && n.isClearable() && allowed)
                mPingNewcomer = true;

            mHandler.postDelayed(new Runnable() {
                public void run() {
                    ApplicationInfo ai;
                    NotificationData.Entry entry = null;

                    // if notification received and not registered by HALO ...
                    if (!mTickerUpdated && allowed) {
                        for (int i = 0; i < mNotificationData.size(); i++) {
                            if (mNotificationData.get(i).notification.toString().equals(n.toString()))
                                entry = mNotificationData.get(i);
                        }

                        if (entry != null) {
                            if (mNotifySilent) {
                                try {
                                    ai = mPm.getApplicationInfo(entry.notification.getPackageName(), 0);
                                } catch (final NameNotFoundException e) {
                                    ai = null;
                                }
                                String text = (String) (ai != null ? mPm.getApplicationLabel(ai) : "...");

                                if (entry.notification.getNotification().tickerText != null) {
                                    text = entry.notification.getNotification().tickerText.toString();
                                }
                                updateTicker(n, text);
                            } else {
                                mPingNewcomer = true;
                                mLastNotificationEntry = entry;
                                tick(entry, 0, 0, false, false);
                                mEffect.refresh();
                            }
                        }
                    }
                    mTickerUpdated = false;
                }
            }, 300);
        }

        @Override
        public void onNotificationRemoved(StatusBarNotification notification) throws RemoteException {
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    // Find next entry
                    clearTicker();
                    mEffect.clearAnimation();
                    mNotificationText = "";
                    NotificationData.Entry entry = null;
                    if (getHaloMsgCount() > 0) {
                        for (int i = getHaloMsgCount()-1; i >= 0; i--) {
                            NotificationData.Entry item = mNotificationData
                                                            .get(getHaloMsgIndex(i, false));
                            if (mCurrentNotficationEntry != null
                                && mCurrentNotficationEntry.notification == item.notification) {
                                continue;
                            }
                            if (item.notification.isClearable()) {
                                entry = item;
                                break;
                            }
                        }
                    }
                    // When no entry was found, take the last one
                    if (entry == null && getHaloMsgCount() > 0) {
                        loadLastNotification(false);
                    } else {
                        tick(entry, 0, 0, false, false);
                        // prevent halo showing removed notification after gesture
                        mLastNotificationEntry = entry;

                        // no notification left, reset mTaskEntry
                        if (entry == null) mTaskEntry = null;
                    }
                    final int c = getHaloMsgCount()-getHidden() < 0
                                        ? 0
                                        : getHaloMsgCount()-getHidden();
                    mEffect.setHaloMessageNumber(c);
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            if (mState != STATE_SILENT) mEffect.nap(1500);
                            if (mState == STATE_SILENT) {
                                mEffect.sleep(HaloEffect.WAKE_TIME * 3,
                                HaloEffect.SLEEP_TIME, mState == STATE_SILENT);
                            }
                        }
                    }, 3000);
                }
            }, mDismissDelay);

            mDismissDelay = 100;
        }
    }

    public class ScreenReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            final ContentResolver resolver = mContext.getContentResolver();
            if (intent.getAction().equals(Intent.ACTION_USER_PRESENT) &&
                    Settings.PAC.getInt(resolver, Settings.PAC.HALO_ACTIVE, 0) == 1 &&
                    Settings.PAC.getInt(resolver, Settings.PAC.HALO_UNLOCK_PING, 0) == 1 &&
                    mState != STATE_SILENT && mPingNewcomer) {
                    mEffect.animateHaloBatch(0, 0, false, 0, HaloProperties.MessageType.MESSAGE);
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            int lastMsg = getHaloMsgCount() - getHidden();
                            if (lastMsg > 0) {
                                NotificationData.Entry entry =
                                        mNotificationData.get(getHaloMsgIndex(lastMsg - 1, true));
                                mEffect.wake();
                                mEffect.nap(HaloEffect.NAP_DELAY + HaloEffect.WAKE_TIME * 2);
                                boolean showMsgBox = Settings.PAC.getInt(resolver,
                                                            Settings.PAC.HALO_MSGBOX, 1) == 1;
                                tick(entry, HaloEffect.WAKE_TIME * 2, 1000, false, showMsgBox);
                                mEffect.ping(mPaintHoloGrey, HaloEffect.WAKE_TIME * 2);
                                mPingNewcomer = false;
                            }
                    }
                    }, 400);
            } else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT) &&
                    Settings.PAC.getInt(resolver, Settings.PAC.HALO_ACTIVE, 0) == 1 &&
                    mKeyguardManager.isKeyguardSecure() && mPingNewcomer) {
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        int lastMsg = getHaloMsgCount() - getHidden();
                        if (lastMsg > 0) {
                            NotificationData.Entry entry =
                                    mNotificationData.get(getHaloMsgIndex(lastMsg - 1, true));
                            mEffect.sleep(HaloEffect.WAKE_TIME,
                                                HaloEffect.WAKE_TIME, mState == STATE_SILENT);
                            tick(entry, HaloEffect.WAKE_TIME * 2, 1000, false, false);
                            mPingNewcomer = false;
                        }
                    }
                }, 200);
            }
        }
    }
}
