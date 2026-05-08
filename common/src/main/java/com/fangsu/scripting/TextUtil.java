package com.fangsu.scripting;

import com.fangsu.Main;

public class TextUtil {
    public static String getCjkParts(String src) {
        return getCjkMatching(src, true);
    }

    public static String getNonCjkParts(String src) {
        return getCjkMatching(src, false);
    }

    public static String getExtraParts(String src) {
        return getExtraMatching(src, true);
    }

    public static String getNonExtraParts(String src) {
        return getExtraMatching(src, false);
    }

    public static String getNonCjkAndExtraParts(String src) {
        if (src == null) return "";
        String extraParts = getExtraMatching(src, false).trim();
        return getCjkMatching(src, false).trim() + (extraParts.isEmpty() ? "" : "|" + extraParts);
    }

    public static boolean isCjk(String src) {
        if (src == null || src.isEmpty()) return false;
        return src.codePoints().anyMatch((codePoint) -> {
            Character.UnicodeBlock unicodeBlock = Character.UnicodeBlock.of(codePoint);
            return Character.isIdeographic(codePoint) || unicodeBlock == Character.UnicodeBlock.CJK_COMPATIBILITY || unicodeBlock == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS || unicodeBlock == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS || unicodeBlock == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT || unicodeBlock == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT || unicodeBlock == Character.UnicodeBlock.CJK_STROKES || unicodeBlock == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION || unicodeBlock == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || unicodeBlock == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A || unicodeBlock == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B || unicodeBlock == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C || unicodeBlock == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D || unicodeBlock == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS || unicodeBlock == Character.UnicodeBlock.BOPOMOFO || unicodeBlock == Character.UnicodeBlock.BOPOMOFO_EXTENDED || unicodeBlock == Character.UnicodeBlock.HIRAGANA || unicodeBlock == Character.UnicodeBlock.KATAKANA || unicodeBlock == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS || unicodeBlock == Character.UnicodeBlock.KANA_SUPPLEMENT || unicodeBlock == Character.UnicodeBlock.KANBUN || unicodeBlock == Character.UnicodeBlock.HANGUL_JAMO || unicodeBlock == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_A || unicodeBlock == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_B || unicodeBlock == Character.UnicodeBlock.HANGUL_SYLLABLES || unicodeBlock == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO || unicodeBlock == Character.UnicodeBlock.KANGXI_RADICALS || unicodeBlock == Character.UnicodeBlock.TAI_XUAN_JING_SYMBOLS || unicodeBlock == Character.UnicodeBlock.IDEOGRAPHIC_DESCRIPTION_CHARACTERS;
        });
    }

    private static String getExtraMatching(String src, boolean extra) {
        if (src == null) return "";
        if (src.contains("||")) {
            return src.split("\\|\\|", 2)[extra ? 1 : 0].trim();
        } else {
            return extra ? "" : src;
        }
    }

    public static String getCjkMatching(String src, boolean cjk) {
        if (src == null) return "";
        String[] stringSplit = getNonExtraParts(src).split("\\|");
        StringBuilder result = new StringBuilder();

        for (String stringSplitPart : stringSplit) {
            if (isCjk(stringSplitPart) == cjk) {
                if (!result.isEmpty()) result.append(' ');
                result.append(stringSplitPart);
            }
        }
        return result.toString().trim();
    }

    public static String addPrefix(String text, String cjkPrefix, String nonCjkPrefix, boolean addSpace) {
        String[] stringSplit = text.split("\\|");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < stringSplit.length; i++) {
            String stringSplitPart = stringSplit[i];
            if (i > 0) result.append('|');
            if (isCjk(stringSplitPart)) {
                result.append(cjkPrefix);
                if (addSpace) result.append(' ');
                result.append(stringSplitPart);
            } else {
                result.append(nonCjkPrefix);
                if (addSpace) result.append(' ');
                result.append(stringSplitPart);
            }
        }
        return result.toString().trim();
    }

    public static boolean hasCjkPart(String text) {
        return !getCjkMatching(text, true).isEmpty();
    }

    public static boolean hasNonCjkPart(String text) {
        return !getCjkMatching(text, false).isEmpty();
    }
}
