package com.github.hiteshsondhi88.sampleffmpeg;

/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 *  This activity uses the camera/camcorder as the A/V source for the {@link android.media.MediaRecorder} API.
 *  A {@link android.view.TextureView} is used as the camera preview which limits the code to API 14+. This
 *  can be easily replaced with a {@link android.view.SurfaceView} to run on older devices.
 */
public class MainActivity extends Activity {

    private Camera mCamera;
    private TextureView mPreview;
    private MediaRecorder mMediaRecorder;

    private boolean isRecording = false;
    private static final String TAG = "Recorder";
    private Button captureButton;
    private String outputFile;
    private TcpServer httpServer;

    FFmpeg ffmpeg;
    private boolean ffmpegReady = false;
    private int orientationDegrees;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_main);

        mPreview = (TextureView) findViewById(R.id.surface_view);
        captureButton = (Button) findViewById(R.id.button_capture);

        loadFFMpegBinary();

        httpServer = new TcpServer();
    }

    private void loadFFMpegBinary() {
        ffmpeg = FFmpeg.getInstance(this);
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onFailure() {
                    Log.d("record", "ffmpeg binary not supported");
                }
            });
        } catch (FFmpegNotSupportedException e) {
            Log.d("record", "exception loading ffmpeg binary");
        }
    }

    /**
     * The capture button controls all user interaction. When recording, the button click
     * stops recording, releases {@link android.media.MediaRecorder} and {@link android.hardware.Camera}. When not recording,
     * it prepares the {@link android.media.MediaRecorder} and starts recording.
     *
     * @param view the view generating the event.
     */
    public void onCaptureClick(View view) {
        if (isRecording) {

            // stop recording and release camera
            mMediaRecorder.stop();  // stop the recording
            releaseMediaRecorder(); // release the MediaRecorder object
            mCamera.lock();         // take camera access back from MediaRecorder

            // inform the user that recording has stopped
            setCaptureButtonText("Capture");
            isRecording = false;
            releaseCamera();
            ffmpegReady = false;
            httpServer.streaming = false;
        } else {

            new MediaPrepareTask().execute(null, null, null);


        }
    }

    private void setCaptureButtonText(String title) {
        captureButton.setText(title);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // if we are using MediaRecorder, release it first
        releaseMediaRecorder();
        // release the camera immediately on pause event
        releaseCamera();
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            // clear recorder configuration
            mMediaRecorder.reset();
            // release the recorder object
            mMediaRecorder.release();
            mMediaRecorder = null;
            // Lock camera for later use i.e taking it back from MediaRecorder.
            // MediaRecorder doesn't need it anymore and we will release it if the activity pauses.
            mCamera.lock();
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            // release the camera for other applications
            mCamera.release();
            mCamera = null;
        }
    }

    private void setCameraDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(CameraHelper.getCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT), info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        Log.d(TAG, "    degrees = " + degrees);

        int result = 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        }
        else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        mCamera.setDisplayOrientation(result);
        orientationDegrees = result;
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private boolean prepareVideoRecorder(){

        mCamera = CameraHelper.getDefaultFrontFacingCameraInstance();
        setCameraDisplayOrientation();

        // We need to make sure that our preview and recording video size are supported by the
        // camera. Query camera to find all the sizes and choose the optimal size given the
        // dimensions of our preview surface.
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size optimalSize = CameraHelper.getOptimalPreviewSize(mSupportedPreviewSizes,
                mPreview.getWidth(), mPreview.getHeight());

        // Use the same size for recording profile.
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        profile.videoFrameWidth = optimalSize.width;
        profile.videoFrameHeight = optimalSize.height;

        // likewise for the camera object itself.
        parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mCamera.setParameters(parameters);
        try {
            // Requires API level 11+, For backward compatibility use {@link setPreviewDisplay}
            // with {@link SurfaceView}
            mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
        } catch (IOException e) {
            Log.e(TAG, "Surface texture is unavailable or unsuitable" + e.getMessage());
            return false;
        }


        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);
        return configureRecorder();
    }

    private boolean configureRecorder() {
        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoSize(720, 480);
        mMediaRecorder.setVideoFrameRate(15);
        //mMediaRecorder.setCaptureRate(15);
        mMediaRecorder.setAudioChannels(1);
        mMediaRecorder.setAudioSamplingRate(22050);
        mMediaRecorder.setVideoEncodingBitRate(400000);
        mMediaRecorder.setAudioEncodingBitRate(64000);
        mMediaRecorder.setOrientationHint(orientationDegrees);

        outputFile = CameraHelper.getOutputMediaFile(
                CameraHelper.MEDIA_TYPE_VIDEO).toString();
        mMediaRecorder.setOutputFile(outputFile);
        // Step 4: Set output file
        Log.d(TAG, "Recording video to " + outputFile);

        // Step 5: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    protected void broadcastStream() {
        String origCommand = "-y -re -i ";
        //origCommand += "http://127.0.0.1:" + StaticHttpServer.HTTP_PORT + "/file.mp4";
        origCommand += "tcp://127.0.0.1:" + TcpServer.HTTP_PORT;
        origCommand += " -acodec copy -vcodec copy -f flv ";
        origCommand += "rtmp://54.235.248.30:1935/LikesLiveShows/nicktest";
        final String[] command = origCommand.split(" ");
        final String ffmpegCommand = origCommand;
        try {
            ffmpeg.execute(command, new ExecuteBinaryResponseHandler() {
                @Override
                public void onFailure(String s) {
                    Log.d("ffmpeg", "Failure: " + s);
                }

                @Override
                public void onSuccess(String s) {
                    Log.d("ffmpeg", "Success: " + s);
                }

                @Override
                public void onProgress(String s) {
                    //Log.d(TAG, "Started command : ffmpeg " + command);
                    Log.d("ffmpeg", "Progress: " + s);

                    if (s.contains("libpostproc")) ffmpegReady = true;
                }

                @Override
                public void onStart() {
                    Log.d(TAG, "Started command : ffmpeg " + ffmpegCommand);
                }

                @Override
                public void onFinish() {
                    Log.d(TAG, "Finished command : ffmpeg " + ffmpegCommand);
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // do nothing for now
        }
    }


    /**
     * Asynchronous task for preparing the {@link android.media.MediaRecorder} since it's a long blocking
     * operation.
     */
    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            // initialize video camera
            if (prepareVideoRecorder()) {
                // Camera is available and unlocked, MediaRecorder is prepared,
                // now you can start recording
                mMediaRecorder.start();

                /*
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "Stop test video");
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                if (configureRecorder()) {
                    mMediaRecorder.start();
                } else {
                    return false;
                }
                */

                isRecording = true;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                writeVideoData();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                broadcastStream();

            } else {
                // prepare didn't work, release the camera
                releaseMediaRecorder();
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                MainActivity.this.finish();
            }
            // inform the user that recording has started
            setCaptureButtonText("Stop");

        }
    }

    private void writeVideoData() {
        Thread writingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Video streaming thread started");
                    /*
                    if (ffmpegReady) {
                        byte[] data = streamingData.read();
                        if (data != null && data.length > 0) {
                            //Log.d(TAG, "Got " + data.length + " bytes");
                            ffmpeg.writeBuffer(data);
                        } else {
                            Thread.sleep(30);
                        }
                    } else {
                        Thread.sleep(50);
                    }
                    */
                httpServer.run(MainActivity.this, outputFile);
            }
        });
        writingThread.start();
    }

}