package com.onlylady.beautyapp.exlib.pili;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.onlylady.beautyapp.R;
import com.onlylady.beautyapp.configs.Configs;
import com.onlylady.beautyapp.configs.EventBusC;
import com.onlylady.beautyapp.engines.BaseEngine;
import com.onlylady.beautyapp.exlib.pili.ui.RotateLayout;
import com.onlylady.beautyapp.urls.UrlsHolder;
import com.onlylady.beautyapp.utils.ClickUtils;
import com.onlylady.beautyapp.utils.LogUtils;
import com.onlylady.beautyapp.utils.TimeUtils;
import com.pili.pldroid.streaming.CameraStreamingManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import de.greenrobot.event.EventBus;

/**
 * Created by jerikc on 15/7/6.
 */
public class StreamingBaseActivity extends Activity implements
        CameraPreviewFrameView.Listener,
        CameraStreamingManager.StreamingSessionListener,
        CameraStreamingManager.StreamingStateListener {

    private static final String TAG = "StreamingBaseActivity";

    protected Button mShutterButton;
//    protected Button mMuteButton;
    protected boolean mShutterButtonPressed = false;
    private boolean mIsNeedMute = false;

    private static final int ZOOM_MINIMUM_WAIT_MILLIS = 33; //ms

    protected static final int MSG_START_STREAMING = 0;
    protected static final int MSG_STOP_STREAMING = 1;
    private static final int MSG_SET_ZOOM = 2;
    private static final int MSG_MUTE = 3;

    protected String mStatusMsgContent;
//    protected TextView mSatusTextView;
    protected String mLogContent = "\n";

    protected CameraStreamingManager mCameraStreamingManager;
    protected boolean mIsReady = false;

    protected RotateLayout mRotateLayout;

    protected JSONObject mJSONObject;
    protected boolean isEncOrientationPort = true;

    private int mCurrentZoom = 0;
    private int mMaxZoom = 0;
    private String lid;

    private Timer timer = new Timer();
    private int time;
    private TextView textTimer;
    private View goback;
    private TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textTimer.setText(TimeUtils.getInstance().secToTime(time++));
                    goback.setVisibility(View.GONE);
                }
            });
        }
    };

    protected Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_STREAMING:
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            // disable the shutter button before startStreaming
                            setShutterButtonEnabled(false);
                            boolean res = mCameraStreamingManager.startStreaming();
                            mShutterButtonPressed = true;
                            Log.i(TAG, "res:" + res);
                            if (!res) {
                                mShutterButtonPressed = false;
                                setShutterButtonEnabled(true);
                            }
                            setShutterButtonPressed(mShutterButtonPressed);
                        }
                    }).start();
                    break;
                case MSG_STOP_STREAMING:
                    // disable the shutter button before stopStreaming
                    setShutterButtonEnabled(false);
                    boolean res = mCameraStreamingManager.stopStreaming();
                    if (!res) {
                        mShutterButtonPressed = true;
                        setShutterButtonEnabled(true);
                    }
                    setShutterButtonPressed(mShutterButtonPressed);
                    StreamingBaseActivity.this.finish();//add cnn
                    break;
                case MSG_SET_ZOOM:
                    mCameraStreamingManager.setZoomValue(mCurrentZoom);
                    break;
                case MSG_MUTE:
                    mIsNeedMute = !mIsNeedMute;
                    mCameraStreamingManager.mute(mIsNeedMute);
                    updateMuteButtonText();
                    break;
                default:
                    Log.e(TAG, "Invalid message");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Config.SCREEN_ORIENTATION == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            isEncOrientationPort = true;
        } else if (Config.SCREEN_ORIENTATION == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            isEncOrientationPort = false;
        }
        setRequestedOrientation(Config.SCREEN_ORIENTATION);

        setContentView(R.layout.activity_camera_streaming);

        textTimer = (TextView) findViewById(R.id.text_timer);
        goback = findViewById(R.id.buttonback);
        ClickUtils.getInstance().setClosedWindowLisener(this,R.id.buttonback);

//
//        SharedLibraryNameHelper.getInstance().renameSharedLibrary(
//                SharedLibraryNameHelper.PLSharedLibraryType.PL_SO_TYPE_AAC,
//                getApplicationInfo().nativeLibraryDir + "/libpldroid_streaming_aac_encoder_v7a.so");
//
//        SharedLibraryNameHelper.getInstance().renameSharedLibrary(
//                SharedLibraryNameHelper.PLSharedLibraryType.PL_SO_TYPE_CORE, "pldroid_streaming_core");
//
//        SharedLibraryNameHelper.getInstance().renameSharedLibrary(
//                SharedLibraryNameHelper.PLSharedLibraryType.PL_SO_TYPE_H264, "pldroid_streaming_h264_encoder_v7a");

        String streamJsonStrFromServer = getIntent().getStringExtra(Config.EXTRA_KEY_STREAM_JSON);
        lid = getIntent().getStringExtra("lid");
        try {
            mJSONObject = new JSONObject(streamJsonStrFromServer);
        } catch (JSONException e) {
            e.printStackTrace();
        }

//        mMuteButton = (Button) findViewById(R.id.mute_btn);
//        mMuteButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (!mHandler.hasMessages(MSG_MUTE)) {
//                    mHandler.sendEmptyMessage(MSG_MUTE);
//                }
//            }
//        });
        updateMuteButtonText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraStreamingManager.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
//        timer.cancel();
        mIsReady = false;
        mShutterButtonPressed = false;
        mCameraStreamingManager.pause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraStreamingManager.destroy();
    }

    @Override
    public void onStateChanged(final int state, Object extra) {
        Log.i(TAG, "onStateChanged state:" + state);
        switch (state) {
            case CameraStreamingManager.STATE.PREPARING:
                mStatusMsgContent = getString(R.string.string_state_preparing);
                break;
            case CameraStreamingManager.STATE.READY:
                mIsReady = true;
                mMaxZoom = mCameraStreamingManager.getMaxZoom();
                mStatusMsgContent = getString(R.string.string_state_ready);
                // start streaming when READY
//                startStreaming();//cnn 注释
                break;
            case CameraStreamingManager.STATE.CONNECTING:
                mStatusMsgContent = getString(R.string.string_state_connecting);
                break;
            case CameraStreamingManager.STATE.STREAMING:
                begintimer();
                mStatusMsgContent = getString(R.string.string_state_streaming);
                setShutterButtonEnabled(true);
                setShutterButtonPressed(true);
                break;
            case CameraStreamingManager.STATE.SHUTDOWN:
                timer.cancel();//add cnn
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        goback.setVisibility(View.VISIBLE);
                    }
                });
                mStatusMsgContent = getString(R.string.string_state_ready);
                setShutterButtonEnabled(true);
                setShutterButtonPressed(false);
                break;
            case CameraStreamingManager.STATE.IOERROR:
                mLogContent += "IOERROR\n";
                mStatusMsgContent = getString(R.string.string_state_ready);
                setShutterButtonEnabled(true);
                break;
            case CameraStreamingManager.STATE.UNKNOWN:
                mStatusMsgContent = getString(R.string.string_state_ready);
                break;
            case CameraStreamingManager.STATE.SENDING_BUFFER_EMPTY:
                break;
            case CameraStreamingManager.STATE.SENDING_BUFFER_FULL:
                break;
            case CameraStreamingManager.STATE.AUDIO_RECORDING_FAIL:
                break;
            case CameraStreamingManager.STATE.OPEN_CAMERA_FAIL:
                Log.e(TAG, "Open Camera Fail. id:" + extra);
                break;
            case CameraStreamingManager.STATE.DISCONNECTED:
                mLogContent += "DISCONNECTED\n";
                break;
        }
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                mSatusTextView.setText(mStatusMsgContent);
//            }
//        });
    }

    /**
     * 开始计时
     */
    private void begintimer() {
        try {
            timer.schedule(timerTask, 0, 1000);  // timeTask
        } catch (IllegalStateException e) {
            LogUtils.Log("重连");
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textTimer.setText(TimeUtils.getInstance().secToTime(time++));
                            goback.setVisibility(View.GONE);
                        }
                    });
                }
            }, 0, 1000);  // timeTask
        }
    }


    @Override
    public boolean onStateHandled(final int state, Object extra) {
        Log.i(TAG, "onStateHandled state:" + state);
        return false;
    }

    protected void setShutterButtonPressed(final boolean pressed) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShutterButtonPressed = pressed;
                mShutterButton.setPressed(pressed);
            }
        });
    }

    protected void setShutterButtonEnabled(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShutterButton.setFocusable(enable);
                mShutterButton.setClickable(enable);
                mShutterButton.setEnabled(enable);
            }
        });
    }

    protected void startStreaming() {
        BaseEngine.getInstance().getStringPost(Configs.getDOMAINV110(), UrlsHolder.getInstance().getParams1103(getIntent().getStringExtra("lid")), new BaseEngine.CallbackForT<String>() {
            @Override
            public void finish(String bean) {
                JSONObject jsonObject = null;
                try {
                    jsonObject = new JSONObject(bean);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if (jsonObject.optInt("errcode") == 0) {
                    //String rtmp = jsonObject.optJSONObject("data").optString("pu") + "/" + jsonObject.optJSONObject("data").optString("ps");
                    mHandler.removeCallbacksAndMessages(null);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_START_STREAMING), 50);
                    EventBus.getDefault().post(EventBusC.getInstance(EventBusC.MINEREFRESS, null));//开始后刷新
                }
            }

            @Override
            public void finish(List<String> listT) {

            }
        });


    }

    protected void stopStreaming() {
        BaseEngine.getInstance().getStringPost(Configs.getDOMAINV110(), UrlsHolder.getInstance().getParams1104(lid), new BaseEngine.CallbackForT<String>() {
                @Override
                public void finish(String bean) {
                    JSONObject jsonObject = null;
                    try {
                        jsonObject = new JSONObject(bean);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (jsonObject.optInt("errcode") == 0) {
                        mHandler.removeCallbacksAndMessages(null);
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_STOP_STREAMING), 50);
                    }
                }

                @Override
                public void finish(List<String> listT) {

                }
            });

    }

    @Override
    public boolean onRecordAudioFailedHandled(int err) {
        mCameraStreamingManager.updateEncodingType(CameraStreamingManager.EncodingType.SW_VIDEO_CODEC);
        mCameraStreamingManager.startStreaming();
        return true;
    }

    @Override
    public boolean onRestartStreamingHandled(int err) {
        Log.i(TAG, "onRestartStreamingHandled");
        return mCameraStreamingManager.startStreaming();
    }

    @Override
    public Camera.Size onPreviewSizeSelected(List<Camera.Size> list) {
        Camera.Size size = null;
//        if (list != null) {
//            for (Camera.Size s : list) {
//                Log.i(TAG, "w:" + s.width + ", h:" + s.height);
//            }
//        }
//        Log.e(TAG, "selected size :" + size.width + "x" + size.height);
        return size;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        Log.i(TAG, "onSingleTapUp X:" + e.getX() + ",Y:" + e.getY());

        if (mIsReady) {
            setFocusAreaIndicator();
            mCameraStreamingManager.doSingleTapUp((int) e.getX(), (int) e.getY());
            return true;
        }
        return false;
    }

    @Override
    public boolean onZoomValueChanged(float factor) {
        if (mIsReady && mCameraStreamingManager.isZoomSupported()) {
            mCurrentZoom = (int) (mMaxZoom * factor);
            mCurrentZoom = Math.min(mCurrentZoom, mMaxZoom);
            mCurrentZoom = Math.max(0, mCurrentZoom);

            Log.d(TAG, "zoom ongoing, scale: " + mCurrentZoom + ",factor:" + factor + ",maxZoom:" + mMaxZoom);
            if (!mHandler.hasMessages(MSG_SET_ZOOM)) {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SET_ZOOM), ZOOM_MINIMUM_WAIT_MILLIS);
                return true;
            }
        }
        return false;
    }

    protected void setFocusAreaIndicator() {
        if (mRotateLayout == null) {
            mRotateLayout = (RotateLayout) findViewById(R.id.focus_indicator_rotate_layout);
            mCameraStreamingManager.setFocusAreaIndicator(mRotateLayout,
                    mRotateLayout.findViewById(R.id.focus_indicator));
        }
    }

    private void updateMuteButtonText() {
//        if (mMuteButton != null) {
//            mMuteButton.setText(mIsNeedMute ? "unmute" : "mute");
//        }
    }

//    public void onEvent(EventBusC c) {
//        if (c.getFrom() == EventBusC.STOPZHIBO) {
////            stopAndRelese();
//            //mLiveSession.stopPreview();
//        }
//
//    }

}
