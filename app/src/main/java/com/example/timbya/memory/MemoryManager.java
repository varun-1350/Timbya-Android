package com.example.timbya.memory;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemoryManager {

    private static final String TAG = "TIMBYA_MEMORY";

    public interface MemoryCallback {
        void onMemoryReady(String contextText);
    }

    private final MemoryDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the","a","an","is","are","was","were","to","of","in","on","for",
            "and","or","but","i","you","me","my","your","it","this","that",
            "do","does","did","what","how","why","can","could","would","will"
    ));

    private static final Pattern P_NAME =
            Pattern.compile("my name is ([a-zA-Z]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_LIKE =
            Pattern.compile("i (?:like|love|enjoy) ([^.!?]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_DISLIKE =
            Pattern.compile("i (?:hate|dislike|don't like) ([^.!?]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PROJECT =
            Pattern.compile("i(?:'m| am) (?:building|working on|developing) ([^.!?]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_GOAL =
            Pattern.compile("(?:my goal is|i want to|i'm trying to) ([^.!?]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_HABIT =
            Pattern.compile("(?:i usually|every day i|i always) ([^.!?]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_REMEMBER =
            Pattern.compile("remember (?:that )?([^.!?]+)", Pattern.CASE_INSENSITIVE);

    public MemoryManager(Context context) {
        dao = MemoryDatabase.getInstance(context).memoryDao();
    }

    public void remember(String category, String keyName, String value) {
        executor.execute(() -> {
            try {
                dao.upsert(new MemoryEntry(category, keyName, value, System.currentTimeMillis()));
                Log.d(TAG, "REMEMBER [" + category + "] " + keyName + " = " + value);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save memory", e);
            }
        });
    }

    /* public void forget(String category, String keyName) {
        executor.execute(() -> {
            try {
                dao.delete(category, keyName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete memory", e);
            }
        });
    } */

    /** Keyword-overlap retrieval, falling back to most-recent so continuity
     *  (e.g. the user's name) still surfaces even without a direct hit. */
    public void getRelevantMemories(String userText, MemoryCallback callback) {
        executor.execute(() -> {

            List<MemoryEntry> all;
            try {
                all = dao.getAll();
            } catch (Exception e) {
                Log.e(TAG, "Failed to read memory", e);
                postResult(callback, "");
                return;
            }

            if (all.isEmpty()) { postResult(callback, ""); return; }

            Set<String> queryWords = wordsOf(userText);
            List<MemoryEntry> scored = new ArrayList<>();
            for (MemoryEntry entry : all) {
                Set<String> entryWords = wordsOf(entry.value);
                entryWords.retainAll(queryWords);
                if (!entryWords.isEmpty()) scored.add(entry);
            }

            List<MemoryEntry> chosen = scored.isEmpty()
                    ? all.subList(0, Math.min(5, all.size()))
                    : scored.subList(0, Math.min(5, scored.size()));

            StringBuilder sb = new StringBuilder();
            for (MemoryEntry entry : chosen) {
                sb.append("- [").append(entry.category).append("] ")
                        .append(entry.value).append("\n");
            }

            postResult(callback, sb.toString());
        });
    }

    /** Not every message - only durable-fact patterns. No AI call, cheap & deterministic. */
    public void extractAndStore(String userText, String assistantReply) {
        executor.execute(() -> {
            tryMatch(P_NAME, userText, "NAME", "name");
            tryMatchUnique(P_LIKE, userText, "INTEREST");
            tryMatchUnique(P_DISLIKE, userText, "DISLIKE");
            tryMatchUnique(P_PROJECT, userText, "PROJECT");
            tryMatchUnique(P_GOAL, userText, "GOAL");
            tryMatchUnique(P_HABIT, userText, "HABIT");
            tryMatchUnique(P_REMEMBER, userText, "FACT");
        });
    }

    private void tryMatch(Pattern pattern, String text, String category, String keyName) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            String value = m.group(1).trim();
            if (!value.isEmpty()) remember(category, keyName, value);
        }
    }

    private void tryMatchUnique(Pattern pattern, String text, String category) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            String value = m.group(1).trim();
            if (!value.isEmpty()) {
                remember(category, value.toLowerCase(Locale.getDefault()), value);
            }
        }
    }

    private Set<String> wordsOf(String text) {
        Set<String> words = new HashSet<>();
        for (String w : text.toLowerCase(Locale.getDefault()).split("[^a-z0-9]+")) {
            if (w.length() > 2 && !STOPWORDS.contains(w)) words.add(w);
        }
        return words;
    }

    private void postResult(MemoryCallback callback, String contextText) {
        mainHandler.post(() -> callback.onMemoryReady(contextText));
    }
}