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
public class StaticHttpServer {
    public static final int BUFFER_SIZE = 64 * 1024;
    public static final int HTTP_PORT = 19091;
    private ServerSocket serverSocket;
    public boolean streaming = true;

    public StaticHttpServer() {
        try {
            serverSocket = new ServerSocket(HTTP_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("http server", "Server started on port "+ HTTP_PORT);
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

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String s;
            while ((s = in.readLine()) != null) {
                Log.d("http request", s);
                if (s.isEmpty()) {
                    break;
                }
            }

            OutputStream out = socket.getOutputStream();
            write(out, "HTTP/1.1 200 OK\r\n");
            write(out, "Date: Fri, 31 Dec 1999 23:59:59 GMT\r\n");
            write(out, "Server: Apache/0.8.4\r\n");
            write(out, "Content-Type: video/mp4\r\n");
            write(out, "Transfer-Encoding: chunked\r\n");
            write(out, "Expires: Sat, 01 Jan 2000 00:59:59 GMT\r\n");
            //out.write("Last-modified: Fri, 09 Aug 1996 14:21:40 GMT\r\n");
            write(out, "\r\n");

            int bytesRead = 0;
            while (streaming) {
                Log.d("http server", "Serving " + outputFile + " from byte " + bytesRead);
                FileInputStream inputFile = new FileInputStream(new File(outputFile));
                inputFile.skip(bytesRead);
                //BufferedReader fileReader = new BufferedReader(new InputStreamReader(inputFile));
                byte[] buffer = new byte[BUFFER_SIZE];
                int readSize;
                while ((readSize = inputFile.read(buffer)) > 0) {
                    int offset = 0;
                    if (readSize >= 24 && bytesRead == 0) {
                        write(out, Integer.toHexString(24));
                        write(out, "\r\n");
                        out.write(buffer, 0, 24);
                        write(out, "\r\n");

                        Log.d("http server", "Writing moov atom");
                        InputStream moovStream = activity.getAssets().open("moov_atom");
                        int moovRead;
                        byte[] moovBuffer = new byte[16 * 1024];
                        while ((moovRead = moovStream.read(moovBuffer)) > 0) {
                            write(out, Integer.toHexString(moovRead));
                            write(out, "\r\n");
                            out.write(moovBuffer, 0, moovRead);
                            write(out, "\r\n");
                        }
                        moovStream.close();
                        offset = 24;
                    }

                    Log.d("http server", "Writing " + readSize + " bytes");
                    if (readSize < 100) {
                        Log.d("http server", new String(buffer, 0, readSize));
                    }
                    write(out, Integer.toHexString(readSize));
                    write(out, "\r\n");
                    out.write(buffer, offset, readSize);
                    write(out, "\r\n");
                    bytesRead += readSize;
                }
                inputFile.close();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }
            write(out, Integer.toHexString(0));
            write(out, "\r\n");
            write(out, "\r\n");
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void write(OutputStream out, String s) throws IOException {
        byte[] data = s.getBytes();
        out.write(data, 0, data.length);
    }

}
