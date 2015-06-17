package com.github.hiteshsondhi88.sampleffmpeg;

import android.app.Activity;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by nick on 6/15/15.
 */
public class TcpToHttpServer {
    public static final int BUFFER_SIZE = 64 * 1024;
    private ServerSocket tcpServerSocket;
    private ServerSocket httpServerSocket;
    public boolean streaming = true;
    private Socket tcpSocket;

    public TcpToHttpServer(int tcpPort, int httpPort) {
        try {
            tcpServerSocket = new ServerSocket(tcpPort);
            httpServerSocket = new ServerSocket(httpPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("http server", "Server started on port "+ httpPort);
    }

    @Override
    protected void finalize() throws Throwable{
        super.finalize();
        try {
            httpServerSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void acceptTcp() {
        synchronized (this) {
            try {
                tcpSocket = tcpServerSocket.accept();
                Log.d("server", "Got tcp connection");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void run() {

        // TODO: Convert this class to function as an RTMP proxy: parse the GET request and fetch the rtmp stream requested

        try {
            // Can only handle one connection at a time, lolz
            final Socket socket = httpServerSocket.accept();
            Log.d("server", "Got http connection");

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

            InputStream inputFile;
            synchronized (this) {
                inputFile = tcpSocket.getInputStream();
            }
            while (streaming) {
                //BufferedReader fileReader = new BufferedReader(new InputStreamReader(inputFile));
                byte[] buffer = new byte[BUFFER_SIZE];
                int readSize;
                while ((readSize = inputFile.read(buffer)) > 0) {
                    int offset = 0;

                    Log.d("server", "Writing " + readSize + " bytes");
                    write(out, Integer.toHexString(readSize));
                    write(out, "\r\n");
                    out.write(buffer, offset, readSize);
                    write(out, "\r\n");
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            inputFile.close();
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
