package com.github.hiteshsondhi88.sampleffmpeg;

import android.net.LocalServerSocket;
import android.net.LocalSocket;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Created by nick on 6/15/15.
 */
public class StreamFileDescriptorFactory {
    public static final int BUFFER_SIZE = 128 * 1024;
    private LocalSocket sender;
    private LocalSocket receiver;
    private byte[] buffer;

    public StreamFileDescriptorFactory() {
        LocalServerSocket server = null;
        try {
            server = new LocalServerSocket("ffmpeg-video");

        receiver = new LocalSocket();
        receiver.connect(server.getLocalSocketAddress());
        receiver.setReceiveBufferSize(BUFFER_SIZE);

        sender = server.accept();
        sender.setSendBufferSize(BUFFER_SIZE);

        } catch (IOException e) {
            e.printStackTrace();
        }

        buffer = new byte[BUFFER_SIZE];
    }

    public FileDescriptor getFD() {
        return sender.getFileDescriptor();
    }

    public byte[] read() {
        try {
            int bytesRead = receiver. getInputStream().read(buffer);
            if (bytesRead > 0) {
                byte[] newBuffer = new byte[bytesRead];
                System.arraycopy(buffer, 0, newBuffer, 0, bytesRead);
                return newBuffer;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
