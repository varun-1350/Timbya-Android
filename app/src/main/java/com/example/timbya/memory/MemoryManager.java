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
import java.util.Collections;
import java.util.Comparator;

public class MemoryManager {

    private static final String TAG = "TIMBYA_MEMORY";

    public interface MemoryCallback {
        void onMemoryReady(String contextText);
    }
    public interface MemoryCommandCallback {
        void onResult(String reply);
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
    private static final Pattern P_FORGET =
            Pattern.compile("^(?:please )?forget (?:that )?(.+?)[.!?]*$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern P_MEMORY_LIST =
            Pattern.compile("^(?:what|which) do you remember(?: about me)?[?!.]*$|"
                            + "^show (?:my )?(?:memories|memory)[?!.]*$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern P_CLEAR_MEMORIES =
            Pattern.compile("^(?:forget|clear|delete) (?:all )?(?:my )?"
                            + "(?:memories|memory|everything)[?!.]*$",
                    Pattern.CASE_INSENSITIVE);

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
    public void handleMemoryCommand(String userText, MemoryCommandCallback callback) {
        executor.execute(() -> {
            String text = userText == null ? "" : userText.trim();

            if (P_CLEAR_MEMORIES.matcher(text).matches()) {
                try {
                    dao.clearAll();
                    postCommandResult(callback, "I've cleared all saved memories.");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to clear memories", e);
                    postCommandResult(callback, "I couldn't clear your memories right now.");
                }
                return;
            }

            Matcher rememberMatcher = P_REMEMBER.matcher(text);
            if (rememberMatcher.matches()) {
                String fact = cleanValue(safeGroup(rememberMatcher));

                if (!fact.isEmpty()) {
                    try {
                        dao.upsert(new MemoryEntry(
                                "FACT",
                                stableKey(fact),
                                fact,
                                System.currentTimeMillis()));

                        postCommandResult(callback, "I'll remember that.");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to save explicit memory", e);
                        postCommandResult(callback, "I couldn't save that memory right now.");
                    }
                    return;
                }
            }

            Matcher forgetMatcher = P_FORGET.matcher(text);
            if (forgetMatcher.matches()) {
                String fact = cleanValue(safeGroup(forgetMatcher));

                if (!fact.isEmpty()) {
                    try {
                        dao.deleteByKeyName(stableKey(fact));
                        postCommandResult(callback, "I've forgotten that.");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to forget memory", e);
                        postCommandResult(callback, "I couldn't remove that memory right now.");
                    }
                    return;
                }
            }

            if (P_MEMORY_LIST.matcher(text).matches()) {
                try {
                    List<MemoryEntry> memories = dao.getAll();
                    postCommandResult(callback, formatMemoryList(memories));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to list memories", e);
                    postCommandResult(callback, "I couldn't read your memories right now.");
                }
                return;
            }

            postCommandResult(callback, null);
        });
    }

     public void forget(String category, String keyName) {
        executor.execute(() -> {
            try {
                dao.delete(category, keyName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete memory", e);
            }
        });
    }

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

            if (all.isEmpty()) {
                postResult(callback, "");
                return;
            }

            List<ScoredMemory> ranked = new ArrayList<>();

            for (MemoryEntry entry : all) {
                int score = relevanceScore(userText, entry);

                if (score > 0) {
                    ranked.add(new ScoredMemory(entry, score));
                }
            }

            Collections.sort(ranked, (left, right) -> {
                int scoreComparison = Integer.compare(right.score, left.score);

                if (scoreComparison != 0) {
                    return scoreComparison;
                }

                return Long.compare(right.entry.updatedAt, left.entry.updatedAt);
            });

            StringBuilder context = new StringBuilder();
            int included = 0;
            final int maxEntries = 6;
            final int maxCharacters = 900;

            for (ScoredMemory scored : ranked) {
                String line = "- [" + scored.entry.category + "] "
                        + scored.entry.value + "\n";

                if (included >= maxEntries
                        || context.length() + line.length() > maxCharacters) {
                    break;
                }

                context.append(line);
                included++;
            }

            postResult(callback, context.toString());
        });
    }

    /** Not every message - only durable-fact patterns. No AI call, cheap & deterministic. */
    public void extractAndStore(String userText) {
        executor.execute(() -> {
            tryMatchName(userText);
            tryMatchUnique(P_LIKE, userText, "INTEREST");
            tryMatchUnique(P_DISLIKE, userText, "DISLIKE");
            tryMatchUnique(P_PROJECT, userText, "PROJECT");
            tryMatchUnique(P_GOAL, userText, "GOAL");
            tryMatchUnique(P_HABIT, userText, "HABIT");
        });
    }

    private void tryMatchName(String text) {
        Matcher m = P_NAME.matcher(text);
        if (m.find()) {
            String value = safeGroup(m);
            if (!value.isEmpty()) remember("NAME", "name", value);
        }
    }

    private void tryMatchUnique(Pattern pattern, String text, String category) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            String value = safeGroup(m);
            if (!value.isEmpty()) {
                remember(category, stableKey(value), value);
            }
        }
    }

    private static String safeGroup(Matcher m) {
        String value = m.group(1);
        return value == null ? "" : value.trim();
    }

    private Set<String> wordsOf(String text) {
        Set<String> words = new HashSet<>();

        if (text == null) {
            return words;
        }

        for (String word : text.toLowerCase(Locale.ROOT)
                .split("[^\\p{L}\\p{N}]+")) {
            if (word.length() > 2 && !STOPWORDS.contains(word)) {
                words.add(word);
            }
        }

        return words;
    }
    private static final class ScoredMemory {
        final MemoryEntry entry;
        final int score;

        ScoredMemory(MemoryEntry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

    private int relevanceScore(String userText, MemoryEntry entry) {
        Set<String> queryWords = wordsOf(userText);
        Set<String> memoryWords = wordsOf(entry.keyName + " " + entry.value);

        memoryWords.retainAll(queryWords);

        int score = memoryWords.size() * 20;
        String query = userText == null ? "" : userText.toLowerCase(Locale.ROOT);

        if ("NAME".equals(entry.category)
                && (query.contains("my name") || query.contains("who am i"))) {
            score += 100;
        }

        if (("INTEREST".equals(entry.category) || "DISLIKE".equals(entry.category))
                && (query.contains("like")
                || query.contains("love")
                || query.contains("prefer")
                || query.contains("favorite")
                || query.contains("hate")
                || query.contains("dislike"))) {
            score += 40;
        }

        if (("PROJECT".equals(entry.category) || "GOAL".equals(entry.category))
                && (query.contains("project")
                || query.contains("goal")
                || query.contains("building")
                || query.contains("working on"))) {
            score += 40;
        }

        long ageDays = Math.max(
                0,
                (System.currentTimeMillis() - entry.updatedAt)
                        / (24L * 60L * 60L * 1000L));

        score += Math.max(0, 5 - (int) ageDays);
        return score;
    }

    private String formatMemoryList(List<MemoryEntry> memories) {
        if (memories == null || memories.isEmpty()) {
            return "I don't have any saved memories yet.";
        }

        StringBuilder reply = new StringBuilder("I remember: ");

        int limit = Math.min(8, memories.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                reply.append("; ");
            }

            reply.append(memories.get(i).value);
        }

        if (memories.size() > limit) {
            reply.append(". There are also ")
                    .append(memories.size() - limit)
                    .append(" more memories.");
        }

        return reply.toString();
    }

    private String cleanValue(String value) {
        return value == null
                ? ""
                : value.replaceAll("[.!?]+$", "").trim();
    }

    private String stableKey(String value) {
        return cleanValue(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private void postCommandResult(MemoryCommandCallback callback, String reply) {
        mainHandler.post(() -> callback.onResult(reply));
    }

    private void postResult(MemoryCallback callback, String contextText) {
        mainHandler.post(() -> callback.onMemoryReady(contextText));
    }
}