package com.zxl.zxing.test;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import com.zxl.zxing.R;
import com.zxl.zxing.test.widget.QRScanView;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class QRScanActivity extends AppCompatActivity implements SurfaceHolder.Callback{

    static {
        System.loadLibrary("iconv");
    }
    private Camera mCamera;
    private SurfaceHolder mHolder;
    private SurfaceView mSurfaceView;
    private ImageScanner mScanner;
    private QRScanView mScanView;
    private Handler autoFocusHandler;
    private AsyncDecode asyncDecode;

    Camera.Parameters parameters;

    private MediaPlayer mediaPlayer;
    private boolean playBeep;
    private static final float BEEP_VOLUME = 0.10f;
    private boolean vibrate;


    private static final long VIBRATE_DURATION = 200L;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrscan);

        init();
    }

    @SuppressWarnings("deprecation")
    private void init() {
        mSurfaceView = (SurfaceView) findViewById(R.id.preview_view);
        mScanView = (QRScanView) findViewById(R.id.viewfinder_view);
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mScanner = new ImageScanner();
        mScanner.setConfig(0, Config.X_DENSITY, 3);
        mScanner.setConfig(0, Config.Y_DENSITY, 3);
        autoFocusHandler = new Handler();
        asyncDecode = new AsyncDecode();

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        if (mHolder.getSurface() == null) {
            return;
        }

        try {
            mCamera.stopPreview();
//            mCamera.setDisplayOrientation(90);
            setCameraDisplayOrientation(QRScanActivity.this,0,mCamera);
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(previewCallback);
            mCamera.startPreview();
            mCamera.autoFocus(autoFocusCallback);
        } catch (Exception e) {
            System.out.println("Error starting camera preview: " + e.getMessage());
        }
    }

    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }


    PreviewCallback previewCallback = new PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (asyncDecode.isStoped()) {
                try{
                    parameters = camera.getParameters();
                }catch (Exception e){
                    e.printStackTrace();
                }

                Size size = parameters.getPreviewSize();

                Image source = new Image(size.width, size.height, "Y800");

                Rect scanImageRect = mScanView.getFramingRect();
                WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                DisplayMetrics screenResolution = new DisplayMetrics();
                manager.getDefaultDisplay().getMetrics(screenResolution);

                int x = scanImageRect.left * size.height
                        / screenResolution.widthPixels;
                int y = scanImageRect.top * size.width
                        / screenResolution.heightPixels;

                int cropWidth = scanImageRect.width() * size.height
                        / screenResolution.widthPixels;
                int cropHeight = scanImageRect.height() * size.width
                        / screenResolution.heightPixels;

                source.setCrop(y, x, cropHeight + y, cropWidth + x);
                source.setData(data);

                asyncDecode = new AsyncDecode();
                asyncDecode.execute(source);

            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
        vibrate = true;
    }

    private void initBeepSound() {
        if (playBeep && mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep);
            try {
                mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
                file.close();
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e) {
                mediaPlayer = null;
            }
        }
    }

    private final MediaPlayer.OnCompletionListener beepListener = new MediaPlayer.OnCompletionListener() {
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.seekTo(0);
        }
    };

    private class AsyncDecode extends AsyncTask<Image, Object, SymbolSet> {
        private boolean stoped = true;

        @Override
        protected SymbolSet doInBackground(Image... params) {
            stoped = false;
            SymbolSet syms = null;
            Image barcode = params[0];
            int result = mScanner.scanImage(barcode);
            if (result != 0) {
                playBeepSoundAndVibrate();
                syms = mScanner.getResults();
            }
            return syms;
        }

        @Override
        protected void onPostExecute(SymbolSet syms) {
            super.onPostExecute(syms);
            stoped = true;
            if (syms != null) {
                handleCameraResult(syms);
            }

        }

        public boolean isStoped() {
            return stoped;
        }
    }

    private void playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer.start();
        }
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    private String recode(String str) {
        String formart = "";

        try {
            boolean ISO = Charset.forName("ISO-8859-1").newEncoder()
                    .canEncode(str);
            if (ISO) {
                formart = new String(str.getBytes("ISO-8859-1"), "GB2312");
            } else {
                formart = str;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return formart;
    }

    private void handleCameraResult(SymbolSet syms) {
        String result = null;

        if (syms != null) {
            for (Symbol sym : syms) {
                result = sym.getData();
            }
        }

        if (result != null) {
            handleResult(result);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();

    }

    private void handleResult(String result) {
        Toast.makeText(QRScanActivity.this,"result--->>>"+result,Toast.LENGTH_SHORT).show();
    }

    Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
        public void onAutoFocus(boolean success, Camera camera) {
            autoFocusHandler.postDelayed(doAutoFocus, 1000);
        }
    };

    private Runnable doAutoFocus = new Runnable() {
        public void run() {
            if (null == mCamera || null == autoFocusCallback) {
                return;
            }
            mCamera.autoFocus(autoFocusCallback);
        }
    };

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mCamera = Camera.open();
        } catch (Exception e) {
            mCamera = null;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        try {
            if (mCamera != null) {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            setResult(RESULT_CANCELED);
            finish();
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }
}
