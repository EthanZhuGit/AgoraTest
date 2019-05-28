
package io.agora.rtc.mediaio.app.videoSource.ui;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import io.agora.rtc.gl.RendererCommon;
import io.agora.rtc.mediaio.IVideoFrameConsumer;
import io.agora.rtc.mediaio.MediaIO.PixelFormat;
import io.agora.rtc.mediaio.TextureSource;
import io.agora.rtc.mediaio.app.monitor.MediaCodecManager;
import io.agora.rtc.mediaio.app.utils.Logger1;


public class MyAgoraTextureCamera extends TextureSource {
    private static final String TAG = MyAgoraTextureCamera.class.getSimpleName();
    private Context mContext;
    private Camera camera;
    private CameraInfo info;

    private CameraPreviewCallback mCameraPreviewCallback;

    private Camera.Parameters mCameraParamters;

    private SurfaceView mSurfaceView;

    public void setSurfaceView(SurfaceView surfaceView) {
        mSurfaceView = surfaceView;
    }

    public MyAgoraTextureCamera(Context context, int width, int height) {
        super((io.agora.rtc.gl.EglBase.Context) null, width, height);
        this.mContext = context;
        mCameraPreviewCallback = new CameraPreviewCallback();
    }

    public void onTextureFrameAvailable(int oesTextureId, float[] transformMatrix,
                                        long timestampNs) {
        super.onTextureFrameAvailable(oesTextureId, transformMatrix, timestampNs);
        int rotation = this.getFrameOrientation();
        if (this.info.facing == 1) {
            transformMatrix = RendererCommon.multiplyMatrices(transformMatrix,
                    RendererCommon.horizontalFlipMatrix());
        }

        if (this.mConsumer != null && this.mConsumer.get() != null) {
            ((IVideoFrameConsumer) this.mConsumer.get()).consumeTextureFrame(oesTextureId,
                    PixelFormat.TEXTURE_OES.intValue(), this.mWidth, this.mHeight, rotation,
                    System.currentTimeMillis(), transformMatrix);
        }

    }

    protected boolean onCapturerOpened() {
        Log.d(TAG, "onCapturerOpened: ");
        try {
//            openCamera();
            doOpenCamera(mSurfaceView);
//            camera.setPreviewTexture(getSurfaceTexture());
//            this.camera.startPreview();
            MediaCodecManager.getInstance().setSurface(new Surface(getSurfaceTexture()));

            MediaCodecManager.getInstance().initCodecManager(mWidth, mHeight, 0);
            return true;
        } catch (Exception var2) {
            Log.e(TAG, "initialize: failed to initalize camera device");
            return false;
        }
    }

    protected boolean onCapturerStarted() {
        Log.d(TAG, "onCapturerStarted: ");

        MediaCodecManager.getInstance().startMediaCodec();
        camera.setPreviewCallbackWithBuffer(mCameraPreviewCallback);
        camera.startPreview();

        return true;
    }

    protected void onCapturerStopped() {
        this.camera.stopPreview();
    }

    protected void onCapturerClosed() {
        this.releaseCamera();
    }

    private void openCamera() {
        if (this.camera != null) {
            throw new RuntimeException("camera already initialized");
        } else {
            this.info = new CameraInfo();
            int numCameras = Camera.getNumberOfCameras();

            for (int i = 0; i < numCameras; ++i) {
                Camera.getCameraInfo(i, this.info);
                if (this.info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                    this.camera = Camera.open(i);
                    break;
                }
            }

            if (this.camera == null) {
                Log.d(TAG, "No front-facing camera found; opening default");
                this.camera = Camera.open();
            }

            if (this.camera == null) {
                throw new RuntimeException("Unable to open camera");
            } else {
                Parameters parms = camera.getParameters();
                List<int[]> frameRates = parms.getSupportedPreviewFpsRange();
                int minFps = ((int[]) frameRates.get(frameRates.size() - 1))[0];
                int maxFps = ((int[]) frameRates.get(frameRates.size() - 1))[1];
                parms.setPreviewFpsRange(minFps, maxFps);
                parms.setPreviewSize(mWidth, mHeight);
                parms.setRecordingHint(true);
                camera.setParameters(parms);
                Size cameraPreviewSize = parms.getPreviewSize();
                String previewFacts = cameraPreviewSize.width + "x" + cameraPreviewSize.height;

                Log.i(TAG, "Camera config: " + previewFacts);
                int bufSize = cameraPreviewSize.width * cameraPreviewSize.height * 3 / 2;
                camera.addCallbackBuffer(new byte[bufSize]);


            }
        }
    }

    private int getDeviceOrientation() {
        int orientation;
        WindowManager wm = (WindowManager) this.mContext.getSystemService(Context.WINDOW_SERVICE);
        switch (wm.getDefaultDisplay().getRotation()) {
            case 0:
            default:
                orientation = 0;
                break;
            case 1:
                orientation = 90;
                break;
            case 2:
                orientation = 180;
                break;
            case 3:
                orientation = 270;
        }

        return orientation;
    }

    private int getFrameOrientation() {
        int rotation = this.getDeviceOrientation();
        if (this.info.facing == 0) {
            rotation = 360 - rotation;
        }

        return (this.info.orientation + rotation) % 360;
    }

    private void releaseCamera() {
        if (this.camera != null) {
            this.camera.stopPreview();

            try {
                this.camera.setPreviewTexture((SurfaceTexture) null);
            } catch (Exception var2) {
                Log.e(TAG, "failed to set Preview Texture");
            }

            this.camera.release();
            this.camera = null;
            Log.d(TAG, "releaseCamera -- done");
        }

    }


    class CameraPreviewCallback implements Camera.PreviewCallback {

        private CameraPreviewCallback() {
            //startRecording();
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Log.d(TAG, "onPreviewFrame: ");
            //当启动录制的视频把视频源数据加入编码中
            MediaCodecManager.getInstance().addFrameData(data);

            camera.addCallbackBuffer(data);

        }
    }

    public boolean doOpenCamera(SurfaceView surfaceView) {

        this.info = new CameraInfo();
        int numCameras = Camera.getNumberOfCameras();

        for (int i = 0; i < numCameras; ++i) {
            Camera.getCameraInfo(i, this.info);
            if (this.info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                this.camera = Camera.open(i);
                break;
            }
        }

        if (this.camera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            this.camera = Camera.open();
        }

        try {
//            camera.setPreviewDisplay(surfaceView.getHolder());
            camera.setPreviewTexture(getSurfaceTexture());
        } catch (IOException e) {
            e.printStackTrace();
        }
        camera.setErrorCallback(new Camera.ErrorCallback() {
            @Override
            public void onError(int error, Camera camera) {
                Logger1.e(TAG, "onError: %s", error);
            }
        });
        Logger1.i(TAG, "Camera open over....");
        return initCamera();
    }

    private boolean initCamera() {
        if (this.camera != null) {
            this.mCameraParamters = this.camera.getParameters();
            List<Camera.Size> sizes = mCameraParamters.getSupportedPreviewSizes();

            for (Camera.Size size : this.mCameraParamters.getSupportedPreviewSizes()) {
                Logger1.d(TAG, "support preview width=" + size.width + "," + size.height);
            }

            this.mCameraParamters.setPreviewFormat(ImageFormat.NV21);
            this.camera.setDisplayOrientation(0);
            this.mCameraParamters.setPreviewSize(mWidth, mHeight);

            this.camera.setParameters(this.mCameraParamters);

            Camera.Parameters parameters = camera.getParameters();
            Camera.Size sz = parameters.getPreviewSize();
            Logger1.i(TAG, "camera width : " + sz.width + "  height  : " + sz.height);
            int bufSize = sz.width * sz.height * 3 / 2;
            camera.addCallbackBuffer(new byte[bufSize]);


            Logger1.d(TAG,
                    "getMaxNumDetectedFaces =" + this.mCameraParamters.getSupportedVideoSizes());
            Logger1.d(TAG,
                    "getMaxNumDetectedFaces =" + this.mCameraParamters.getMaxNumDetectedFaces());
            Logger1.d(TAG, "getMaxNumFocusAreas =" + this.mCameraParamters.getMaxNumFocusAreas());
            Logger1.d(TAG,
                    "getMaxNumMeteringAreas =" + this.mCameraParamters.getMaxNumMeteringAreas());
            int[] range = new int[2];
            this.mCameraParamters.getPreviewFpsRange(range);
            Logger1.d(TAG, "getPreviewFpsRange =" + Arrays.toString(range));
            List<int[]> fps = this.mCameraParamters.getSupportedPreviewFpsRange();
            if (fps != null)
                for (int[] f : fps) {
                    Logger1.i(TAG, "initCamera: fps %s", Arrays.toString(f));
                }

            Logger1.d(TAG,
                    "getSupportedPreviewFormats =" + this.mCameraParamters.getSupportedPreviewFormats());
            Logger1.d(TAG,
                    "getSupportedSceneModes =" + this.mCameraParamters.getSupportedSceneModes());

            return true;
        }
        return false;
    }
}
