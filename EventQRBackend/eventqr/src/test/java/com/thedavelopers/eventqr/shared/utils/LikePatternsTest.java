package com.thedavelopers.eventqr.shared.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikePatternsTest {

    @Test
    void blankOrNullMeansNoSearch() {
        assertThat(LikePatterns.contains(null)).isNull();
        assertThat(LikePatterns.contains("   ")).isNull();
    }

    @Test
    void termIsLowerCasedTrimmedAndWrapped() {
        assertThat(LikePatterns.contains("  Jane DOE ")).isEqualTo("%jane doe%");
    }

    @Test
    void likeWildcardsAndTheEscapeCharacterAreEscaped() {
        assertThat(LikePatterns.contains("50%_off!")).isEqualTo("%50!%!_off!!%");
    }

    @Test
    void lowerCasingIgnoresTheDefaultLocale() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(LikePatterns.contains("TITLE ID")).isEqualTo("%title id%");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    /** Evaluates a LIKE pattern the way SQL does with ESCAPE '!' (building-block check of the escaping rules). */
    private static boolean sqlLike(String value, String pattern) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '!' && i + 1 < pattern.length()) {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(pattern.charAt(++i))));
            } else if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append('.');
            } else {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
            }
        }
        return value.matches(regex.toString());
    }

    @Test
    void escapedPatternsMatchLiterallyUnderEscapeBang() {
        assertThat(sqlLike("100% sure", LikePatterns.contains("100%"))).isTrue();
        assertThat(sqlLike("1000 sure", LikePatterns.contains("100%"))).isFalse();
        assertThat(sqlLike("a_b", LikePatterns.contains("a_b"))).isTrue();
        assertThat(sqlLike("axb", LikePatterns.contains("a_b"))).isFalse();
        assertThat(sqlLike("wx", LikePatterns.contains("w!"))).isFalse();
        assertThat(sqlLike("hello w!x", LikePatterns.contains("w!"))).isTrue();
        assertThat(sqlLike("anything", "%")).isTrue();
    }
}
