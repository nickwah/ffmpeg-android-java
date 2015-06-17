package com.github.hiteshsondhi88.sampleffmpeg;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.VideoView;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

/**
 * Created by nick on 6/16/15.
 */
public class WatchVideoActivity extends Activity {
    public static final String TAG = "WatchVideoActivity";
    ProgressBar spinny;
    TextView progressTextView;
    RelativeLayout progressContainer;
    FFmpeg ffmpeg;
    private VideoView video;
    private TcpToHttpServer httpServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_watch_video);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        loadFFMpegBinary();

        spinny = (ProgressBar) findViewById(R.id.stream_video_progress_bar);
        progressTextView = (TextView) findViewById(R.id.stream_video_progress_hint_text_view);
        progressContainer = (RelativeLayout) findViewById(R.id.stream_video_progress_container);
        video = (VideoView)findViewById(R.id.video);
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                Log.d(TAG, "Restarting video playre");
                video.start();
                return true;
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            video.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mediaPlayer, int i, int i1) {
                    Log.d("prathab", "onInfo called " + i + " " + i1);
                    switch (i) {
                        case MediaPlayer.MEDIA_INFO_UNKNOWN:
                            Log.d("prathab", "unknown error");
                            break;
                        case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                            Log.d("prathab", "video track lagging");
                            break;
                        case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                            Log.d("prathab", "video rendering start");
                            break;
                        case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                            Log.d("prathab", "buffering start");
                            // So feed back to user
                            progressTextView.setText("Buffering");
                            progressContainer.setVisibility(View.VISIBLE);
                            break;
                        case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                            Log.d("prathab", "buffering end");
                            progressContainer.setVisibility(View.GONE);
                            break;
                        case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                            Log.d("prathab", "bad interleaving ");
                            break;
                        case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                            Log.d("prathab", "not seekable");
                            break;
                        case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                            Log.d("prathab", "metadata update");
                            break;
                        case MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:
                            Log.d("prathab", "unsupported subtitle");
                            break;
                        case MediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:
                            Log.d("prathab", "subtitle timed out");
                            break;
                    }

                    return true;
                }
            });
        }

        httpServer = new TcpToHttpServer(19094, 19095);
        acceptTcp();
        broadcastStream();
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

    protected void acceptTcp() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                httpServer.acceptTcp();
            }
        });
        t.start();
    }

    protected void acceptHttp() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                httpServer.run();
            }
        });
        t.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ffmpeg.killRunningProcesses();
    }

    protected void broadcastStream() {
        // -avioflags direct -fflags nobuffer -max_delay 100000
        String origCommand = "-y -fflags nobuffer -max_delay 100000 -analyzeduration 600000 -rtmp_buffer 500 -f flv -i ";
        origCommand += "rtmp://stream.host.example.com:1935/live/stream_name";
        origCommand += " -strict experimental -acodec aac -ab 128k -vcodec copy -movflags +empty_moov -f mp4 ";
        origCommand += "tcp://127.0.0.1:19094";
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
                        Log.d(TAG, "Play video");
                        acceptHttp();
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        video.setVideoURI(Uri.parse("http://127.0.0.1:19095/test.mp4"));
                        video.start();
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