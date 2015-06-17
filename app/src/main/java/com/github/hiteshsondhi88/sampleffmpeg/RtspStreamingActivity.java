package com.github.hiteshsondhi88.sampleffmpeg;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.video.VideoQuality;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

/**
 * A straightforward example of how to use the RTSP server included in libstreaming.
 */
public class RtspStreamingActivity extends Activity {

	private final static String TAG = "MainActivity";

	private SurfaceView mSurfaceView;
	FFmpeg ffmpeg;
	private boolean broadcasting = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_rtsp_streaming);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		mSurfaceView = (SurfaceView) findViewById(R.id.surface);
		
		// Sets the port of the RTSP server to 1234
		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putString(RtspServer.KEY_PORT, String.valueOf(19093));
		editor.commit();

		// Configures the SessionBuilder
		SessionBuilder.getInstance()
		.setSurfaceView(mSurfaceView)
		.setPreviewOrientation(90)
		.setContext(getApplicationContext())
				// TODO: figure out width and height using camera
				.setVideoQuality(new VideoQuality(352, 288, 15, 600000))
				.setAudioQuality(new AudioQuality(44100, 64000))
		.setAudioEncoder(SessionBuilder.AUDIO_AAC)
		.setVideoEncoder(SessionBuilder.VIDEO_H264);
		
		// Starts the RTSP server
		this.startService(new Intent(this, RtspServer.class));

		final Button startButton = (Button)findViewById(R.id.start_stream);
		startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!broadcasting) {
					broadcastStream();
					broadcasting = true;
					startButton.setText("Stop");
				} else {
					ffmpeg.killRunningProcesses();
					broadcasting = false;
					startButton.setText("Start");
				}
			}
		});

		loadFFMpegBinary();
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

	protected void broadcastStream() {
		String origCommand = "-y -min_port 19100 -max_port 19999 -reorder_queue_size 15 -max_delay 500000 -rtsp_transport udp -i ";
		origCommand += "rtsp://127.0.0.1:19093/live.sdp";
		origCommand += " -acodec copy -vcodec copy -metadata:s:v:0 rotate=90 -f flv ";
		origCommand += "rtmp://host.example.com:1935/live/stream_name";
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

					if (s.contains("libpostproc")) {
						// TODO: start stream?
					}
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
}
