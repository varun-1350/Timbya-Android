package com.example.timbya.actions;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.util.ArrayList;
import java.util.List;

public class FileOpener {

    public static class FileMatch {
        public final long id;
        public final String displayName;
        public final String mimeType;
        public final String relativePath;

        FileMatch(long id, String displayName, String mimeType, String relativePath) {
            this.id = id;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.relativePath = relativePath == null ? "" : relativePath;
        }

        public Uri toUri() {
            return Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), String.valueOf(id));
        }
    }

    private final Context context;

    public FileOpener(Context context) {
        this.context = context;
    }

    /** Fuzzy search device storage (via MediaStore) for files whose name contains query. */
    public List<FileMatch> search(String query) {
        List<FileMatch> results = new ArrayList<>();

        Uri collection = MediaStore.Files.getContentUri("external");
        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.RELATIVE_PATH
        };
        String selection = MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = { "%" + query + "%" };

        try (Cursor cursor = context.getContentResolver().query(
                collection, projection, selection, selectionArgs,
                MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC LIMIT 10")) {

            if (cursor != null) {
                int idIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                int nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE);
                int pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH);

                while (cursor.moveToNext()) {
                    results.add(new FileMatch(
                            cursor.getLong(idIdx),
                            cursor.getString(nameIdx),
                            cursor.getString(mimeIdx),
                            cursor.getString(pathIdx)));
                }
            }
        } catch (Exception ignored) { }

        return results;
    }

    public void open(FileMatch match) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String mime = match.mimeType;
        if (mime == null || mime.isEmpty()) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(match.displayName);
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        }
        intent.setDataAndType(match.toUri(), mime != null ? mime : "*/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }
}