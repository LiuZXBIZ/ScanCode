package com.lenaeon.scancode.zxing;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;

import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.lenaeon.scancode.R;
import com.lenaeon.scancode.zxing.camera.CameraManager;
import com.lenaeon.scancode.zxing.decode.PhotoScanHandler;
import com.lenaeon.scancode.zxing.decode.RGBLuminanceSource;
import com.lenaeon.scancode.zxing.utils.BeepManager;
import com.lenaeon.scancode.zxing.utils.BitmapUtil;
import com.lenaeon.scancode.zxing.utils.CaptureActivityHandler;
import com.lenaeon.scancode.zxing.utils.InactivityTimer;


import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Hashtable;

public class ScanManager implements SurfaceHolder.Callback {
    private Context context;
    boolean isHasSurface = false;
    CameraManager cameraManager;
    //用于拍摄扫描的handler
    CaptureActivityHandler handler;
    //用于照片扫描的handler,不可共用，图片扫描是不需要摄像机的
    PhotoScanHandler photoScanHandler;
    Rect mCropRect = null;
    InactivityTimer inactivityTimer;
    public BeepManager beepManager;
    SurfaceView scanPreview = null;
    View scanContainer;
    View scanCropView;
    ImageView scanLine;
    final String TAG = ScanManager.class.getSimpleName();
    Activity activity;
    ScanListener listener;
    boolean isOpenLight = false;
    TranslateAnimation animation=null;

    private int scanMode;//扫描模型（条形，二维码，全部）

    /**
     * 用于启动照相机扫描二维码，在activity的onCreate里面构造出来
     * 在activity的生命周期中调用此类相对应的生命周期方法
     *
     * @param activity      扫描的activity
     * @param scanPreview   预览的SurfaceView
     * @param scanContainer 扫描的布局，全屏布局
     * @param scanCropView  扫描的矩形区域
     * @param scanLine      扫描线
     */
    public ScanManager(Activity activity, SurfaceView scanPreview, View scanContainer,
                       View scanCropView, ImageView scanLine, int scanMode, ScanListener listener) {
        this.activity = activity;
        this.scanPreview = scanPreview;
        this.scanContainer = scanContainer;
        this.scanCropView = scanCropView;
        this.scanLine = scanLine;
        this.listener = listener;
        this.scanMode = scanMode;
    }

    /**
     * 用于图片扫描的构造函数
     *
     * @param listener 结果的监听回调
     */
    public ScanManager(ScanListener listener) {
        this.listener = listener;
    }

    public void onResume() {
        // CameraManager must be initialized here, not in onCreate(). This is
        // necessary because we don't
        // want to open the camera driver and measure the screen size if we're
        // going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the
        // wrong size and partially
        // off screen.
        inactivityTimer = new InactivityTimer(activity);
        beepManager = new BeepManager(activity);
        cameraManager = new CameraManager(activity.getApplicationContext());

        handler = null;
        if (isHasSurface) {
            // The activity was paused but not stopped, so the surface still
            // exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(scanPreview.getHolder());
        } else {
            // Install the callback and wait for surfaceCreated() to init the
            // camera.
            scanPreview.getHolder().addCallback(this);
        }
        inactivityTimer.onResume();
    }

    public void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        beepManager.close();
        cameraManager.closeDriver();
        if (!isHasSurface) {
            scanPreview.getHolder().removeCallback(this);
        }
    }

    public void onDestroy() {
        inactivityTimer.shutdown();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!isHasSurface) {
            isHasSurface = true;
            initCamera(holder);

            int x = mCropRect.width();
            int y = mCropRect.height();

            //扫描线启动动画效果 by LiuZXBIZ 20170525
            //int starty = scanLine.getBackground().getIntrinsicHeight() / 2;
            int starty = scanLine.getHeight() / 2 + 10;
            int endedy = mCropRect.height() - 5 - starty;
            animation = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.ABSOLUTE, -starty, Animation.ABSOLUTE, endedy);
            animation.setDuration(1500);
            animation.setRepeatCount(-1);
            animation.setRepeatMode(Animation.REVERSE);
            scanLine.startAnimation(animation);
        }
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isHasSurface = false;
    }

    void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a
            // RuntimeException.
            if (handler == null) {
                handler = new CaptureActivityHandler(this, cameraManager, scanMode);
                Log.e("hongliang1", "handler new成功！:" + handler);
            }

            initCrop();
        } catch (IOException ioe) {
            Log.e(TAG, "hongliang", ioe);
            //弹出提示，报错
            ioe.printStackTrace();
            listener.scanError(new Exception("相机打开出错，请检查是否被禁止了该权限！"));
        } catch (RuntimeException e) {
            Log.e(TAG, "hongliang", e);
            //弹出提示，报错
            e.printStackTrace();
            listener.scanError(new Exception("相机打开出错，请检查是否被禁止了该权限！"));
        }
    }

    /**
     * 开关闪关灯
     */
    public void switchLight() {
        if (isOpenLight) {
            cameraManager.offLight();
        } else {
            cameraManager.openLight();
        }
        isOpenLight = !isOpenLight;
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }


    /**
     * 截取的矩形扫描区域
     */
    public Rect getCropRect() {
        return mCropRect;
    }

    /**
     * 扫描成功的结果回调
     *
     * @param rawResult
     * @param bundle
     */
    public void handleDecode(Result rawResult, Bundle bundle) {
        inactivityTimer.onActivity();
        //扫描成功播放声音滴一下，可根据需要自行确定什么时候播
        beepManager.playBeepSoundAndVibrate();
        bundle.putInt("width", mCropRect.width());
        bundle.putInt("height", mCropRect.height());
        bundle.putString("result", rawResult.getText());
        listener.scanResult(rawResult, bundle);
    }

    public void handleDecodeError(Exception e) {
        listener.scanError(e);
    }

    /**
     * 初始化截取的矩形区域
     */
    void initCrop() {
        int cameraWidth = cameraManager.getCameraResolution().y;
        int cameraHeight = cameraManager.getCameraResolution().x;


        /** 获取布局中扫描框的位置信息 */
        int[] location = new int[2];
        scanCropView.getLocationInWindow(location);

        int cropLeft = location[0];
        int cropTop = location[1];
        /**
         * 界面Activity模式采用全屏模式
         * android:theme="@android:style/Theme.NoTitleBar.Fullscreen"
         * 预览图片才不会压缩显示，否则当解码完毕后的截图存在变形错觉
         *
         * int statusBarHeight = getStatusBarHeight();
         * int cropTop = location[1] - statusBarHeight;
         *
         * */

        int cropWidth = scanCropView.getWidth();
        int cropHeight = scanCropView.getHeight();

        /** 获取布局容器的宽高 */
        int containerWidth = scanContainer.getWidth();
        int containerHeight = scanContainer.getHeight();

        /** 计算最终截取的矩形的左上角顶点x坐标 */
        int x = cropLeft * cameraWidth / containerWidth;
        /** 计算最终截取的矩形的左上角顶点y坐标 */
        int y = cropTop * cameraHeight / containerHeight;

        /** 计算最终截取的矩形的宽度 */
        //int width = cropWidth * cameraWidth / containerWidth;
        //** 计算最终截取的矩形的高度 */
        //int height = cropHeight * cameraHeight / containerHeight;
        /** 生成最终的截取的矩形 */
        //mCropRect = new Rect(x, y, width + x, height + y);

        /** 修改最终的截取的矩形 by LiuZXBIZ 20170525*/
        mCropRect = new Rect(x, y, x + cropWidth, y + cropHeight);
    }

    int getStatusBarHeight() {
        try {
            Class<?> c = Class.forName("com.android.internal.R$dimen");
            Object obj = c.newInstance();
            Field field = c.getField("status_bar_height");
            int x = Integer.parseInt(field.get(obj).toString());
            return activity.getResources().getDimensionPixelSize(x);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 用于扫描本地图片二维码或者一维码
     *
     * @param path 本地图片的所在位置
     * @return
     */
    public void scanningImage(final String path) {
        if (TextUtils.isEmpty(path)) {
            listener.scanError(new Exception("photo url is null!"));
        }
        photoScanHandler = new PhotoScanHandler(this);
        new Thread(new Runnable() {

            @Override
            public void run() {

                // 设置二维码内容的编码
                Hashtable<DecodeHintType, String> hints = new Hashtable<DecodeHintType, String>();
                hints.put(DecodeHintType.CHARACTER_SET, "UTF8");

                Bitmap scanBitmap=BitmapUtil.decodeBitmapFromPath(path, 200, 200);

                RGBLuminanceSource source = new RGBLuminanceSource(scanBitmap);
                Binarizer binarizer = new HybridBinarizer(source);
                BinaryBitmap bitmap1 = new BinaryBitmap(binarizer);

                MultiFormatReader multiFormatReader = new MultiFormatReader();
                try {
                    Message msg = Message.obtain();
                    msg.what = PhotoScanHandler.PHOTODECODESUCCESS;
                    msg.obj = multiFormatReader.decode(bitmap1, hints);
                    photoScanHandler.sendMessage(msg);
                } catch (Exception e) {
                    Message msg = Message.obtain();
                    msg.what = PhotoScanHandler.PHOTODECODEERROR;
                    msg.obj = new Exception("图片有误或者图片模糊");
                    photoScanHandler.sendMessage(msg);
                }
            }
        }).start();
    }

    /**
     * 扫描一次后，如需再次扫描，请调用这个方法
     */
    public void reScan() {
        if (handler != null) {
            handler.sendEmptyMessage(R.id.restart_preview);
        }
    }

    public boolean isScanning() {
        if (handler != null) {
            return handler.isScanning();
        }
        return false;
    }
}
