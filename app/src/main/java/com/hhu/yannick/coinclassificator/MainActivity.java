package com.hhu.yannick.coinclassificator;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import org.opencv.android.OpenCVLoader;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCharacteristics cameraCharacteristics;

    private int cameraFacing;
    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureRequest captureRequest;
    private CameraCaptureSession cameraCaptureSession;

    private TextureView.SurfaceTextureListener surfaceTextureListener;
    private TextureView.OnTouchListener surfaceTextureOnClickListener;
    private TextureView textureView;

    private Size previewSize;
    private String cameraId;
    private CameraDevice.StateCallback stateCallback;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private Intent classify_intent;
    private Intent ellipse_view_intent;
    private Intent trainings_data_intent;

    // static OpenCV load
    static {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Cannot load OpenCV library");
        }
    }

    private Mode mode = Mode.CLASSIFY;
    private enum Mode{
        CLASSIFY,
        ELLIPSE,
        TRAIN
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get views
        textureView = (TextureView) findViewById(R.id.texture_view);

        // Create the intents
        classify_intent = new Intent(this, ClassifyActivity.class);
        ellipse_view_intent = new Intent(this, EllipseActivity.class);
        trainings_data_intent = new Intent(this, TrainActivity.class);

        // Initialize camera
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        cameraFacing = CameraCharacteristics.LENS_FACING_BACK;

        // add an onClickListener for manual focus
        surfaceTextureOnClickListener = new TextureView.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN){
                    try {
                        doManualFocus(v, event);
                    }
                    catch (CameraAccessException e){
                        e.printStackTrace();
                    }
                }
                return true;
            }
        };
        textureView.setOnTouchListener(surfaceTextureOnClickListener);

        // Listener to wait for availability of my SurfaceTexture
        surfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                setUpCamera();
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        };

        // Initializing State-system
        stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice cameraDevice) {
                MainActivity.this.cameraDevice = cameraDevice;
                createPreviewSession();
            }

            @Override
            public void onDisconnected(CameraDevice cameraDevice) {
                cameraDevice.close();
                MainActivity.this.cameraDevice = null;
            }

            @Override
            public void onError(CameraDevice cameraDevice, int error) {
                cameraDevice.close();
                MainActivity.this.cameraDevice = null;
            }
        };


        // Add a listener to the Capture button
        FloatingActionButton captureButton = (FloatingActionButton)findViewById(R.id.fab_take_photo);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onTakePhotoButtonClicked();
                    }
                }
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // create the app menu bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        // get the spinner and fill with the options
        MenuItem item = menu.findItem(R.id.spinner);
        Spinner spinner = (Spinner) item.getActionView();
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.spinner_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // set spinner dropdown items
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // check for permissions
        if (ContextCompat.checkSelfPermission(this,  Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // request it if not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    0);
        }
        else {
            // open and set up the camera
            openBackgroundThread();
            if (textureView.isAvailable()) {
                setUpCamera();
                openCamera();
            } else {
                textureView.setSurfaceTextureListener(surfaceTextureListener);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 0: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // granted, resume
                    openBackgroundThread();
                    if (textureView.isAvailable()) {
                        setUpCamera();
                        openCamera();
                    } else {
                        textureView.setSurfaceTextureListener(surfaceTextureListener);
                    }
                }
            }
        }
    }

    private void setUpCamera() {
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                // get Camera-Info
                cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == cameraFacing) {
                    StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    previewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];
                    this.cameraId = cameraId;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openBackgroundThread() {
        backgroundThread = new HandlerThread("camera_background_thread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void createPreviewSession() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            if (cameraDevice == null) {
                                return;
                            }
                            try {
                                captureRequest = captureRequestBuilder.build();
                                MainActivity.this.cameraCaptureSession = cameraCaptureSession;
                                MainActivity.this.cameraCaptureSession.setRepeatingRequest(captureRequest,
                                        null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {

                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private boolean manualFocusEngaged;
    private void doManualFocus(View view, MotionEvent motionEvent) throws CameraAccessException {
        Log.d("FOCUS", "Manual Focus active");

        final Rect sensorArraySize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

        // TODO: here I just flip x,y, but this needs to correspond with the sensor orientation (via SENSOR_ORIENTATION)
        final int y = (int)((motionEvent.getX() / (float)view.getWidth())  * (float)sensorArraySize.height());
        final int x = (int)((motionEvent.getY() / (float)view.getHeight()) * (float)sensorArraySize.width());
        final int halfTouchWidth  = 150; //(int)motionEvent.getTouchMajor(); //TODO: this doesn't represent actual touch size in pixel. Values range in [3, 10]...
        final int halfTouchHeight = 150; //(int)motionEvent.getTouchMinor();
        MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(x - halfTouchWidth,  0),
                Math.max(y - halfTouchHeight, 0),
                halfTouchWidth  * 2,
                halfTouchHeight * 2,
                MeteringRectangle.METERING_WEIGHT_MAX - 1);

        CameraCaptureSession.CaptureCallback captureCallbackHandler = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                manualFocusEngaged = false;

                if (request.getTag() == "FOCUS_TAG") {
                    try {
                        captureRequest = captureRequestBuilder.build();
                        MainActivity.this.cameraCaptureSession = cameraCaptureSession;
                        MainActivity.this.cameraCaptureSession.setRepeatingRequest(captureRequest,
                                null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                Log.e("FOCUS", "Manual AF failure: " + failure);
                manualFocusEngaged = false;
            }
        };

        // first stop the existing repeating request
        cameraCaptureSession.stopRepeating();

        // cancel any existing AF trigger (repeated touches, etc.)
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallbackHandler, null);

        // Now add a new AF trigger with focus region
        if (cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        captureRequestBuilder.setTag("FOCUS_TAG"); //we'll capture this later for resuming the preview

        // then we ask for a single request (not repeating!)
        cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallbackHandler, null);
        manualFocusEngaged = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeCamera();
        closeBackgroundThread();
    }

    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void closeBackgroundThread() {
        if (backgroundHandler != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    public void onTakePhotoButtonClicked() {

        // wait a little until phone has stabilised after pressing capture button
        try {
            Thread.sleep(300);
        }
        catch (Exception e){
            e.printStackTrace();
        }

        FileOutputStream outputPhoto = null;
        String tempFileName = "temp";
        try {

            outputPhoto = openFileOutput(tempFileName, Context.MODE_PRIVATE);
            textureView.getBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, outputPhoto);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputPhoto != null) {
                    outputPhoto.close();

                    // Send over the file location
                    classify_intent.putExtra("File", tempFileName);
                    ellipse_view_intent.putExtra("File", tempFileName);
                    trainings_data_intent.putExtra("File", tempFileName);

                    // open new activity based on the selection
                    switch (mode){
                        case CLASSIFY: startActivity(classify_intent); break;
                        case ELLIPSE: startActivity(ellipse_view_intent); break;
                        case TRAIN: startActivity(trainings_data_intent); break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // An item was selected
        switch (pos){
            case 0: mode = Mode.CLASSIFY; break;
            case 1: mode = Mode.ELLIPSE; break;
            case 2: mode = Mode.TRAIN; break;
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing
    }
}
