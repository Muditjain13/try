package com.example.filetranferproject;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;

public class MyHostApduService extends HostApduService {

    private static final String TAG = "APDUReceiver";
    private static final String FILE_NAME = "received_file.bin";
    private FileOutputStream fileOutputStream;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            fileOutputStream = openFileOutput(FILE_NAME, MODE_PRIVATE);
            Log.i(TAG, "File output stream initialized at: " + getFileStreamPath(FILE_NAME).getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to open file: " + e.getMessage());
        }
    }

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        Log.i(TAG, "Received APDU of length: " + apdu.length);

        if (apdu.length < 7) {
            Log.w(TAG, "Invalid APDU (too short)");
            return new byte[]{(byte) 0x6A, (byte) 0x80};
        }

        if (apdu[4] != 0x00) {
            Log.w(TAG, "Not extended format APDU");
            return new byte[]{(byte) 0x6A, (byte) 0x81};
        }

        int length = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
        if (apdu.length < 7 + length) {
            Log.w(TAG, "Incomplete APDU: expected " + length + " bytes, got " + (apdu.length - 7));
            return new byte[]{(byte) 0x67, (byte) 0x00};
        }

        byte[] chunk = new byte[length];
        System.arraycopy(apdu, 7, chunk, 0, length);

        try {
            fileOutputStream.write(chunk);
            fileOutputStream.flush();
            Log.i(TAG, "Chunk written: " + length + " bytes");
            return new byte[]{(byte) 0x90, (byte) 0x00};
        } catch (IOException e) {
            Log.e(TAG, "Write failed: " + e.getMessage());
            return new byte[]{(byte) 0x6F, (byte) 0x00};
        }
    }

    @Override
    public void onDeactivated(int reason) {
        Log.i(TAG, "Deactivated: " + reason);
        try {
            if (fileOutputStream != null) {
                fileOutputStream.close();
                Log.i(TAG, "File closed successfully");
            }
        } catch (IOException e) {
            Log.e(TAG, "File close failed: " + e.getMessage());
        }
    }
}
