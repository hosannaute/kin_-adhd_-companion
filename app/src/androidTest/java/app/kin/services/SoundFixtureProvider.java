package app.kin.services;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Test APK only. A separate provider process must not depend on target-APK Kotlin classes. */
public class SoundFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) { return new File(getContext().getCacheDir(), "fixture-" + uri.getLastPathSegment() + ".wav"); }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        File file = file(uri);
        try {
            if (!file.exists()) {
                if ("large".equals(uri.getLastPathSegment())) {
                    try (RandomAccessFile out = new RandomAccessFile(file,"rw")) { out.setLength(10L * 1024 * 1024 + 1); }
                } else {
                    byte[] bytes;
                    if ("invalid".equals(uri.getLastPathSegment())) bytes = "Not an audio file".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                    else {
                        int rate = 22050, count = rate * 4;
                        ByteBuffer b = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN);
                        b.put("RIFF".getBytes()).putInt(36 + count * 2).put("WAVEfmt ".getBytes()).putInt(16)
                            .putShort((short)1).putShort((short)1).putInt(rate).putInt(rate * 2).putShort((short)2).putShort((short)16)
                            .put("data".getBytes()).putInt(count * 2);
                        for (int i = 0; i < count; i++) b.putShort((short)(Math.sin(2 * Math.PI * 440 * i / rate) * 6000));
                        bytes = b.array();
                    }
                    try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); }
                }
            }
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[]{"My phone tone.wav"}); return cursor;
    }
    @Override public String getType(Uri uri) { return "audio/wav"; }
    @Override public int delete(Uri uri, String selection, String[] args) { return file(uri).delete() ? 1 : 0; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
