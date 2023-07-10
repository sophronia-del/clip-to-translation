package indi.sophronia.tools.util;

import com.google.common.base.Charsets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5 {
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
}
