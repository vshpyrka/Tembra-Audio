package audio.tembra.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A document provider for the picker's copy tests: every document is {@link #BYTES} long. {@code /named/<id>} reports
 * the display name "Rim Shot.wav", {@code /blank/<name>} an empty one, and {@code /throwing/<name>} throws from
 * {@code query} as some providers do.
 *
 * <p>Java on purpose: the provider runs in the test APK's own process, which has no Kotlin standard library (the test
 * APK leaves it to the app under test), so a Kotlin provider dies on its first call.
 */
public final class TestSoundProvider extends ContentProvider {

    public static final String AUTHORITY = "audio.tembra.android.test.sounds";

    public static final byte[] BYTES = bytes();

    private static byte[] bytes() {
        byte[] bytes = new byte[1024];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 7);
        return bytes;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String kind = uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0);
        if (kind.equals("throwing")) throw new SecurityException("this provider refuses queries");
        String name = kind.equals("named") ? "Rim Shot.wav" : "";
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[] {name, (long) BYTES.length});
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        File file = new File(getContext().getCacheDir(), "test-sound.wav");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(BYTES);
        } catch (IOException e) {
            throw new java.io.FileNotFoundException(e.getMessage());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "audio/wav";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
