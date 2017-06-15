package com.dynamsoft.camera;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.dynamsoft.barcode.Barcode;
import com.dynamsoft.barcode.BarcodeReader;
import com.dynamsoft.barcode.FinishCallback;
import com.dynamsoft.barcode.ReadResult;
import com.dynamsoft.scan.barcode.R;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DBR extends Activity implements Camera.PreviewCallback {
    public static String TAG = "DBRDemo";
    public static String ACTION_BARCODE = "com.dynamsoft.dbr";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //apply for camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=  PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
            }
            else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
            }
        }

        mPreview = (FrameLayout) findViewById(R.id.camera_preview);
        mFlashImageView = (ImageView)findViewById(R.id.ivFlash);
        mFlashTextView = (TextView)findViewById(R.id.tvFlash);
        mRectLayer = (RectLayer)findViewById(R.id.rectLayer);

        mSurfaceHolder = new CameraPreview(DBR.this);
        mPreview.addView(mSurfaceHolder);

        String license = "";

        Intent intent = getIntent();
        if (intent.getAction().equals(ACTION_BARCODE)) {
            mIsIntent = true;
            license = intent.getStringExtra("license");
        }

        mBarcodeReader = new BarcodeReader(license);
    }

    static final int PERMISSIONS_REQUEST_CAMERA = 473;
    private FrameLayout mPreview = null;
    private CameraPreview mSurfaceHolder = null;
    private Camera mCamera = null;
    private BarcodeReader mBarcodeReader;
    private long mBarcodeFormat = Barcode.OneD | Barcode.QR_CODE | Barcode.PDF417 | Barcode.DATAMATRIX;
    private ImageView mFlashImageView;
    private TextView mFlashTextView;
    private RectLayer mRectLayer;
    private boolean mIsDialogShowing = false;
    private boolean mIsReleasing = false;
    final ReentrantReadWriteLock mRWLock = new ReentrantReadWriteLock();

    @Override protected void onResume() {
        super.onResume();
        waitForRelease();
        if (mCamera == null)
            openCamera();
        else
            mCamera.startPreview();
    }

    @Override protected void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (mCamera != null) {
            mSurfaceHolder.stopPreview();
            mCamera.setPreviewCallback(null);
            mIsReleasing = true;
            releaseCamera();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        waitForRelease();
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    public void showAbout(View v) {
        CustomDialog.Builder builder = new CustomDialog.Builder(this);
        builder.setTitle("About");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mIsDialogShowing = false;
            }
        });
        Spanned spanned = Html.fromHtml("<font color='#FF8F0D'><a href=\"http://www.dynamsoft.com\">Download Free Trial here</a></font><br/>");
        builder.setMessage(spanned);
        builder.create(R.layout.about, R.style.AboutDialog).show();
        mIsDialogShowing = true;
    }

    public void setFlash(View v) {
        if (mCamera != null) {
            Camera.Parameters p = mCamera.getParameters();
            String flashMode = p.getFlashMode();
             if (flashMode.equals(Camera.Parameters.FLASH_MODE_OFF)) {
                 p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                 mFlashImageView.setImageResource(R.mipmap.flash_on);
                 mFlashTextView.setText("Flash on");
             }
            else {
                 p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                 mFlashImageView.setImageResource(R.mipmap.flash_off);
                 mFlashTextView.setText("Flash off");
             }
            mCamera.setParameters(p);
            mCamera.startPreview();
        }
    }

    private static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            Log.i(TAG, "Camera is not available (in use or does not exist)");
        }
        return c; // returns null if camera is unavailable
    }

    private void openCamera()
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mCamera = getCameraInstance();
                if (mCamera != null) {
                    mCamera.setDisplayOrientation(90);
                    Camera.Parameters cameraParameters = mCamera.getParameters();
                    cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    mCamera.setParameters(cameraParameters);
                }

                Message message = handler.obtainMessage(OPEN_CAMERA, 1);
                message.sendToTarget();
            }
        }).start();
    }

    private void releaseCamera()
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mCamera.release();
                mCamera = null;
                mRWLock.writeLock().lock();
                mIsReleasing = false;
                mRWLock.writeLock().unlock();
            }
        }).start();
    }

    private void waitForRelease() {
        while (true) {
            mRWLock.readLock().lock();
            if (mIsReleasing) {
                mRWLock.readLock().unlock();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                mRWLock.readLock().unlock();
                break;
            }
        }
    }

    private boolean mFinished = true;
    private final static int READ_RESULT = 1;
    private final static int OPEN_CAMERA = 2;
    private final static int RELEASE_CAMERA = 3;
    private int mImageHeight = 0;
    private boolean mIsIntent = false;

    Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case READ_RESULT:
                    ReadResult result = (ReadResult)msg.obj;
                    Barcode barcode = result.barcodes == null ? null : result.barcodes[0];
                    if (barcode != null) {
                        if (mIsIntent) {
                            Intent data = new Intent();
                            data.putExtra("SCAN_RESULT", barcode.displayValue);
                            data.putExtra("SCAN_RESULT_FORMAT", barcode.formatString);
                            DBR.this.setResult(DBR.RESULT_OK, data);
                            DBR.this.finish();
                            mFinished = true;
                            return;
                        }

                        CustomDialog.Builder builder = new CustomDialog.Builder(DBR.this);
                        builder.setTitle("Result");
                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                mIsDialogShowing = false;
                            }
                        });
                        int y = Integer.MIN_VALUE;
                        //rotate cornerPoints by 90, NewLeft = H - Ymax, NewTop = Left, NewWidth = Height, NewHeight = Width
                        for (Point vertex : barcode.cornerPoints) {
                            if (y < vertex.y)
                                y = vertex.y;
                        }
                        int left = barcode.boundingBox.left;
                        int width = barcode.boundingBox.width();
                        int height = barcode.boundingBox.height();
                        barcode.boundingBox.left = mImageHeight - y;
                        barcode.boundingBox.top = left;
                        barcode.boundingBox.right = height + barcode.boundingBox.left;
                        barcode.boundingBox.bottom = width + barcode.boundingBox.top;
                        CustomDialog dialog = builder.create(R.layout.result, R.style.ResultDialog);
                        dialog.getWindow().setLayout(mRectLayer.getWidth() * 10 / 12, (mRectLayer.getHeight() >> 1) + 16);
                        dialog.show();
                        mIsDialogShowing = true;
                    } else {
                        if (result.errorCode != BarcodeReader.DBR_OK)
                            Log.i(TAG, "Error:" + result.errorString);
                    }
                    mFinished = true;
                    break;
                case OPEN_CAMERA:
                    if (mCamera != null) {
                        mCamera.setPreviewCallback(DBR.this);
                        mSurfaceHolder.setCamera(mCamera);
                        Camera.Parameters p = mCamera.getParameters();
                        if (mFlashTextView.getText().equals("Flash on"))
                            p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        mCamera.setParameters(p);
                        mSurfaceHolder.startPreview();
                    }
                    break;
                case RELEASE_CAMERA:
                    break;
            }
        }
    };

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (mFinished && !mIsDialogShowing) {
            mFinished = false;
            Camera.Size size = camera.getParameters().getPreviewSize();
            mImageHeight = size.height;
            mBarcodeReader.readSingleAsync(data, size.width, size.height, mBarcodeFormat, new FinishCallback() {
                @Override
                public void onFinish(ReadResult readResult) {
                    Message message = handler.obtainMessage(READ_RESULT, readResult);
                    message.sendToTarget();
                }
            });
        }
    }
}
