package com.example.filetranferproject;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;

public class MainActivity extends Activity {

    private static final int FILE_SELECT_CODE = 1;
    private NfcAdapter nfcAdapter;
    private IsoDep isoDep;
    private byte[] fileBytes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Let user choose a file
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, FILE_SELECT_CODE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0,
                    new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_MUTABLE
            );
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Parcelable tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag != null) {
            isoDep = IsoDep.get((Tag) tag);
            if (isoDep != null && fileBytes != null) {
                new Thread(() -> {
                    try {
                        isoDep.connect();
                        runOnUiThread(() -> Toast.makeText(this, "Connected to PC", Toast.LENGTH_SHORT).show());
                        sendFile(fileBytes);
                        isoDep.close();
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK && data != null) {
            try {
                InputStream inputStream = getContentResolver().openInputStream(data.getData());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    fileBytes = inputStream.readAllBytes();
                } else {
                    // Backward compatible file reading
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] dataBuffer = new byte[4096];
                    while ((nRead = inputStream.read(dataBuffer, 0, dataBuffer.length)) != -1) {
                        buffer.write(dataBuffer, 0, nRead);
                    }
                    fileBytes = buffer.toByteArray();
                }

                inputStream.close();
                Toast.makeText(this, "File loaded. Tap your PC reader!", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "File load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void sendFile(byte[] data) {
        try {
            int offset = 0;
            int chunkSize = 200;
            int chunkIndex = 0;

            while (offset < data.length) {
                int length = Math.min(chunkSize, data.length - offset);
                byte[] chunk = Arrays.copyOfRange(data, offset, offset + length);

                // APDU format: [CLA][INS][P1][P2][Lc(00)][Extended Lc Hi][Extended Lc Lo][Data]
                byte[] apdu = new byte[] {
                        (byte) 0x00,   // CLA
                        (byte) 0xA4,   // INS (SELECT)
                        (byte) 0x04,   // P1 (SELECT by AID)
                        (byte) 0x00,   // P2 (No specific behavior)
                        (byte) 0x07,   // Lc (length of AID in bytes)
                        (byte) 0xA0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00  // AID bytes
                };

                byte[] response = isoDep.transceive(apdu);

                int finalChunkIndex = chunkIndex;
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sent chunk " + finalChunkIndex, Toast.LENGTH_SHORT).show());

                // Expecting status word 0x9000 for success
                if (!(response.length >= 2 &&
                        response[response.length - 2] == (byte) 0x90 &&
                        response[response.length - 1] == 0x00)) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error during transmission", Toast.LENGTH_SHORT).show());
                    break;
                }

                offset += length;
                chunkIndex++;
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }
}
