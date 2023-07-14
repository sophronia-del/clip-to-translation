package indi.sophronia.tools.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class StringHelper {
    private static final MessageDigest DIGEST;

    private static final Language[] LANGUAGE_TABLE;

    static {
        try {
            DIGEST = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            throw Rethrow.rethrow(e);
        }

        LANGUAGE_TABLE = new Language[0x110000];
        Arrays.fill(LANGUAGE_TABLE, Language.UNKNOWN);

        for (Language value : Language.values()) {
            for (int i = 0; i < value.rangesBegin.length; i++) {
                for (int j = value.rangesBegin[i]; j <= value.rangesEnd[i]; j++) {
                    LANGUAGE_TABLE[j] = value;
                }
            }
        }
    }

    public static String digest(String data) {
        return new String(DIGEST.digest(data.getBytes(StandardCharsets.UTF_8)));
    }

    public static void filterKeysByPattern(Set<String> keys, String pattern) {
        String[] parts = pattern.split("\\*");
        keys.removeIf(s -> {
            int cursor = 0;
            for (String part : parts) {
                if (part.isEmpty()) {
                    continue;
                }
                cursor = s.indexOf(part, cursor);
                if (cursor < 0) {
                    return true;
                }
            }
            return false;
        });
    }

    public static Language[] detectLanguage(String source) {
        EnumMap<Language, Integer> count = new EnumMap<>(Language.class);
        for (Language value : Language.values()) {
            count.put(value, 0);
        }

        source.codePoints().mapToObj(i -> LANGUAGE_TABLE[i]).
                forEach(l -> count.computeIfPresent(l, (language, integer) -> integer + 1));

        Language[] languages = count.entrySet().stream().
                sorted(Comparator.comparingInt(Map.Entry::getValue)).
                map(Map.Entry::getKey).toArray(Language[]::new);
        for (int i = 0; i < languages.length / 2; i++) {
            Language l = languages[i];
            languages[i] = languages[languages.length - 1 - i];
            languages[languages.length - 1 - i] = l;
        }
        return languages;
    }
}
