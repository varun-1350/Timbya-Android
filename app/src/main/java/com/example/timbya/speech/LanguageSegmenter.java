package com.example.timbya.speech;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Splits a sentence into same-language segments so Speaker can switch the
 * TTS locale per segment instead of treating the whole reply as English.
 * Heuristic-based, not a full language-ID model:
 *  - Devanagari script -> Hindi or Marathi, disambiguated by a small
 *    marker-word lexicon (the two languages share a script, so this is
 *    the only practical signal available without a bundled NLP library).
 *  - Latin script + umlauts/German function words -> German.
 *  - Everything else in Latin script -> English.
 */
public final class LanguageSegmenter {

    private LanguageSegmenter() { }

    public static final Locale ENGLISH = Locale.US;
    public static final Locale HINDI = new Locale("hi", "IN");
    public static final Locale MARATHI = new Locale("mr", "IN");
    public static final Locale GERMAN = Locale.GERMANY;

    public static class Segment {
        public final String text;
        public final Locale locale;
        Segment(String text, Locale locale) { this.text = text; this.locale = locale; }
    }

    private static final Set<String> MARATHI_MARKERS = new HashSet<>(Arrays.asList(
            "आहे", "आहात", "कसं", "कसे", "मध्ये", "काय", "तुम्ही", "मी", "तू",
            "नाही", "होय", "बरं", "छान", "करतोय", "करते", "जातोय", "येतोय", "येते"
    ));

    private static final Set<String> HINDI_MARKERS = new HashSet<>(Arrays.asList(
            "है", "हैं", "कैसा", "कैसे", "में", "क्या", "आप", "मैं", "तुम",
            "नहीं", "हाँ", "अच्छा", "ठीक", "कुछ"
    ));

    private static final Set<String> GERMAN_MARKERS = new HashSet<>(Arrays.asList(
            "der", "die", "das", "und", "ist", "nicht", "heute", "wie", "ich",
            "du", "sie", "wir", "ja", "nein", "gut", "danke", "bitte", "wetter",
            "schön", "auch", "aber", "mit", "für", "auf", "sehr", "haben", "sein"
    ));

    /** Groups consecutive same-language words into segments, preserving order. */
    public static List<Segment> segment(String sentence) {
        List<Segment> result = new ArrayList<>();
        if (sentence == null || sentence.trim().isEmpty()) return result;

        String[] tokens = sentence.trim().split("\\s+");
        StringBuilder currentText = new StringBuilder();
        Locale currentLocale = null;

        for (String token : tokens) {
            Locale tokenLocale = classify(token);

            if (currentLocale == null) {
                currentLocale = tokenLocale;
                currentText.append(token);
            } else if (tokenLocale.equals(currentLocale)) {
                currentText.append(' ').append(token);
            } else {
                result.add(new Segment(currentText.toString(), currentLocale));
                currentText = new StringBuilder(token);
                currentLocale = tokenLocale;
            }
        }
        if (currentText.length() > 0) {
            result.add(new Segment(currentText.toString(), currentLocale));
        }
        return result;
    }

    private static Locale classify(String rawToken) {
        String token = rawToken.replaceAll("\\p{Punct}", "").trim();
        if (token.isEmpty()) return ENGLISH;

        if (containsDevanagari(token)) {
            if (MARATHI_MARKERS.contains(token)) return MARATHI;
            if (HINDI_MARKERS.contains(token)) return HINDI;
            return HINDI; // unrecognized Devanagari word: Hindi is the more common default
        }

        String lower = token.toLowerCase(Locale.ROOT);
        if (containsUmlaut(token) || GERMAN_MARKERS.contains(lower)) {
            return GERMAN;
        }

        return ENGLISH;
    }

    private static boolean containsDevanagari(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeBlock.of(s.charAt(i)) == Character.UnicodeBlock.DEVANAGARI) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUmlaut(String s) {
        return s.indexOf('ä') >= 0 || s.indexOf('ö') >= 0 || s.indexOf('ü') >= 0
                || s.indexOf('Ä') >= 0 || s.indexOf('Ö') >= 0 || s.indexOf('Ü') >= 0
                || s.indexOf('ß') >= 0;
    }
}