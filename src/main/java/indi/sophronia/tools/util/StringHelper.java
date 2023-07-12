package indi.sophronia.tools.util;

import com.google.common.base.Charsets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

public class StringHelper {
    private static final MessageDigest DIGEST;

    static {
        try {
            DIGEST = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            throw Rethrow.rethrow(e);
        }
    }

    public static String digest(String data) {
        return new String(DIGEST.digest(data.getBytes(Charsets.UTF_8)));
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
}
