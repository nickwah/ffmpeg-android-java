package com.github.hiteshsondhi88.sampleffmpeg;

import android.app.Activity;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by nick on 6/15/15.
 */
public class TcpServer {
    public static final int BUFFER_SIZE = 64 * 1024;
    public static final int HTTP_PORT = 19092;
    private ServerSocket serverSocket;
    public boolean streaming = true;

    public TcpServer() {
        try {
            serverSocket = new ServerSocket(HTTP_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("tcp server", "Server started on port "+ HTTP_PORT);
    }

    @Override
    protected void finalize() throws Throwable{
        super.finalize();
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run(final Activity activity, final String outputFile) {
        try {
            // Can only handle one connection at a time, lolz
            final Socket socket = serverSocket.accept();

            /*
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String s;
            while ((s = in.readLine()) != null) {
                Log.d("http request", s);
                if (s.isEmpty()) {
                    break;
                }
            }
            */
            Log.d("tcp", "all input read");

            OutputStream out = socket.getOutputStream();

            int bytesRead = 0;
            while (streaming) {
                //Log.d("http server", "Serving " + outputFile + " from byte " + bytesRead);
                FileInputStream inputFile = new FileInputStream(new File(outputFile));
                inputFile.skip(bytesRead);
                //BufferedReader fileReader = new BufferedReader(new InputStreamReader(inputFile));
                byte[] buffer = new byte[BUFFER_SIZE];
                int readSize;
                while ((readSize = inputFile.read(buffer)) > 0) {
                    int offset = 0;
                    if (readSize == 32 && bytesRead == 0) {
                        out.write(buffer, 0, 24);

                        Log.d("http server", "Writing moov atom");
                        InputStream moovStream = activity.getAssets().open("moov_atom");
                        int moovRead;
                        byte[] moovBuffer = new byte[16 * 1024];
                        while ((moovRead = moovStream.read(moovBuffer)) > 0) {
                            out.write(moovBuffer, 0, moovRead);
                        }
                        moovStream.close();
                        offset = 24;
                        out.write(0x0);
                        out.write(0x0);
                        out.write(0x0);
                        out.write(0x0);
                        out.write("mdat".getBytes());
                        bytesRead += readSize;
                    } else {

                        Log.d("http server", "Writing " + readSize + " bytes");
                        if (readSize < 100) {
                            Log.d("http server", new String(buffer, 0, readSize));
                        }
                        out.write(buffer, offset, readSize);
                        bytesRead += readSize;
                    }
                }
                inputFile.close();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.d("tcp", "server done");
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
