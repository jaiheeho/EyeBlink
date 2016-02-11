package com.example.android.eyeblink;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class VideoActivity extends AppCompatActivity {

    private Camera mCamera;
    private CameraPreview mPreview;
    private MediaRecorder mMediaRecorder;

    private Context myContext;
    private boolean isRecording;
    private int frontCameraId;

    private SurfaceView transparentView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        myContext = this;

        // Create second surface with another holder (holderTransparent)
//        transparentView = (SurfaceView)findViewById(R.id.TransparentView);
//        transparentView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
//        transparentView.setZOrderMediaOverlay(true);
    }

    public void onResume() {
        super.onResume();
        //mPreview.refreshCamera(mCamera);
        startRendering();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRecording=false;
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private void startRendering() {

        if (!checkCameraHardware(myContext)) {
            showToast("No Camera Detected");
            finish();
        }
        if (mCamera == null) {
            // if the front facing camera does not exist
            if ((frontCameraId = findFrontFacingCamera()) < 0) {
                Toast.makeText(this, "No front facing camera found.", Toast.LENGTH_LONG).show();
                return;
            }

            if ((mCamera = Camera.open(frontCameraId)) == null) {
                Toast.makeText(this, "Fail to access Camera.", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Rendering Start.", Toast.LENGTH_LONG).show();

            mCamera.setDisplayOrientation(90);
            mCamera.setFaceDetectionListener(new MyFaceDetectionListener());

            mPreview = new CameraPreview(this, mCamera);
            FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.addView(mPreview);
        }
    }

    public void startRecording(View view) {

        if (isRecording) {
            showToast("Stop Recording");
            mMediaRecorder.stop();
            releaseMediaRecorder();
            mCamera.lock();         // take camera access back from MediaRecorder
            ((Button) view).setText(getString(R.string.start_record));
            isRecording = false;
        } else {
            // initialize video camera
            if (prepareVideoRecorder()) {
                // Camera is available and unlocked, MediaRecorder is prepared,
                // now you can start recording
                showToast("Start Recording");
                mMediaRecorder.start();
                ((Button) view).setText(getString(R.string.stop_record));
                // inform the user that recording has startedd
                isRecording = true;
            } else {
                // prepare didn't work, release the camera
                releaseMediaRecorder();
                showToast("Fail to Start Recording");
                // inform user
            }

        }
    }
    public void startFaceDetection() {
        // Try starting Face Detection
        Camera.Parameters params = mCamera.getParameters();

        // start face detection only *after* preview has started
        if (params.getMaxNumDetectedFaces() > 0) {
            // camera supports face detection, so can start it:
            mCamera.startFaceDetection();
        }
    }


    private boolean prepareVideoRecorder() {


        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.reset();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(CamcorderProfile.get(frontCameraId,CamcorderProfile.QUALITY_HIGH));

        // Step 4: Set output file
        mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());
        //mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());

        // Step 5: Set the preview output
        mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            //Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            //Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private void showToast(String text) {
        int duration = Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(myContext, text, duration);
        toast.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL, 0, 0);
        toast.show();
    }

    /**
     * Check if this device has a camera
     */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    public static Camera getCameraInstance(int camID) {
        Camera c = null;
        try {
            c = Camera.open(camID); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }


    private int findFrontFacingCamera() {
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    /**
     * Create a file Uri for saving an image or video
     */
    private static Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * Create a File for saving an image or video
     */
    private static File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "EyeBlink");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("EyeBlink", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        } else {
            return null;
        }
        return mediaFile;
    }

    private void drawCircleSurface(Surface surface, int x, int y, int radius) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(radius / 4 + 1, 0, 0, Color.RED);

        Canvas canvas = surface.lockCanvas(null);
        try {
            Log.v("EyeBlicnk", "drawCircleSurface: isHwAcc=" + canvas.isHardwareAccelerated());
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvas.drawCircle(x, y, radius, paint);
        } finally {
            surface.unlockCanvasAndPost(canvas);
        }

    }



    class MyFaceDetectionListener implements Camera.FaceDetectionListener {

        MyFaceDetectionListener() {
        }

        @Override
        public void onFaceDetection(Camera.Face[] faces, Camera camera) {
            if (faces.length > 0) {
                String facePosition = "face detected: " + faces.length +
                        " Face 1 Location X: " + faces[0].rect.centerX() +
                        "Y: " + faces[0].rect.centerY();
                facePosition += "\n left eye : (" + faces[0].leftEye.x +  "," +faces[0].leftEye.y;
                facePosition += "\n right eye : (" + faces[0].rightEye.x +  "," +faces[0].rightEye.y;

                Log.d("FaceDetection", facePosition);

                TextView textView = (TextView) findViewById(R.id.face_position);
                textView.setText(facePosition);

//                FaceAreaView mfaceArea = new FaceAreaView(myContext,
//                        faces[0].rect.centerX(), faces[0].rect.centerY());
            }
        }

        public class FaceAreaView extends View {
            private ShapeDrawable mDrawable;

            public FaceAreaView(Context context, int x, int y) {
                super(context);

                int X = 10;
                int Y = 10;
                int width = 50;
                int height = 50;

                mDrawable = new ShapeDrawable(new OvalShape());
                mDrawable.getPaint().setColor(0xff74AC23);
                mDrawable.setBounds(X, Y, X + width, Y + height);

            }

            protected void onDraw(Canvas canvas) {
                mDrawable.draw(canvas);
            }
        }
    }
}