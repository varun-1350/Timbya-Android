package com.example.timbya.actions;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import androidx.core.content.ContextCompat;
import android.Manifest;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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

        Map<String, ContactCandidate> candidates = new LinkedHashMap<>();

        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {

            if (cursor == null) {
                return null;
            }

            int numberIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER);
            int nameIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);

            while (cursor.moveToNext()) {
                String displayName = cursor.getString(nameIndex);
                String phoneNumber = cursor.getString(numberIndex);

                int score = scoreNameMatch(contactName, displayName);
                if (score < 0 || phoneNumber == null) {
                    continue;
                }

                String key = normalizeName(displayName);
                ContactCandidate existing = candidates.get(key);

                if (existing == null || score < existing.score) {
                    candidates.put(key, new ContactCandidate(
                            phoneNumber.replaceAll("[^+\\d]", ""),
                            score));
                }
            }
        }

        ContactCandidate best = null;
        ContactCandidate secondBest = null;

        for (ContactCandidate candidate : candidates.values()) {
            if (best == null || candidate.score < best.score) {
                secondBest = best;
                best = candidate;
            } else if (secondBest == null || candidate.score < secondBest.score) {
                secondBest = candidate;
            }
        }

        // Never guess when two different contacts are equally likely.
        if (best == null || (secondBest != null && best.score == secondBest.score)) {
            return null;
        }

        return best.phoneNumber;
    }
    private static final class ContactCandidate {
        final String phoneNumber;
        final int score;

        ContactCandidate(String phoneNumber, int score) {
            this.phoneNumber = phoneNumber;
            this.score = score;
        }
    }

    private static int scoreNameMatch(String spokenName, String displayName) {
        String spoken = normalizeName(spokenName);
        String stored = normalizeName(displayName);

        if (spoken.isEmpty() || stored.isEmpty()) {
            return -1;
        }

        // Exact contact names always win.
        if (spoken.equals(stored)) {
            return 0;
        }

        // Supports names such as "Mammi Home" without replacing an exact match.
        if (stored.contains(spoken) || spoken.contains(stored)) {
            return 10 + Math.abs(stored.length() - spoken.length());
        }

        // Helps with speech-recognition variations:
        // Mammi, Mami, and Mummy normalize to the same vowel-folded form.
        if (foldVowels(spoken).equals(foldVowels(stored))) {
            return 20;
        }

        int distance = levenshteinDistance(spoken, stored);
        int allowedDistance = Math.max(1, Math.min(spoken.length(), stored.length()) / 3);

        return distance <= allowedDistance ? 30 + distance : -1;
    }

    private static String normalizeName(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String foldVowels(String value) {
        return value.replaceAll("[aeiouy]+", "a");
    }

    private static int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }


        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;

            for (int j = 1; j <= right.length(); j++) {
                int substitutionCost =
                        left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;

                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + substitutionCost);
            }

            int[] temporary = previous;
            previous = current;
            current = temporary;
        }

        return previous[right.length()];
    }
}