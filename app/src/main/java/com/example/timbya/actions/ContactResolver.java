package com.example.timbya.actions;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import androidx.core.content.ContextCompat;
import android.Manifest;

public class ContactResolver {

    private final Context context;

    public ContactResolver(Context context) {
        this.context = context;
    }

    /** Returns the first matching phone number for a contact display name, or null. */
    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public String findPhoneNumber(String contactName) {
        if (!hasPermission()) {
            return null;
        }

        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        };
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = { "%" + contactName + "%" };

        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, selection, selectionArgs, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                return cursor.getString(idx).replaceAll("[^+\\d]", "");
            }
        }
        return null;
    }
}