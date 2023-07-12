package indi.sophronia.tools.endpoint;

import indi.sophronia.tools.util.RPC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class YouDaoTranslationSupport extends TranslationApiEndpoint {
    private String id;
    private String secret;

    @Override
    public boolean onLoadingValidate(Properties properties) {
        id = properties.getProperty("you_dao.id");
        secret = properties.getProperty("you_dao.secret");
        return id != null && secret != null;
    }

    @Override
    protected String doTranslate(String source) throws IOException {
        Map<String, String> params = new HashMap<>();
        String salt = String.valueOf(System.currentTimeMillis());
        params.put("from", "auto");
        params.put("to", "zh-CHS");
        params.put("signType", "v3");
        String curtime = String.valueOf(System.currentTimeMillis() / 1000);
        params.put("curtime", curtime);
        String signStr = id + truncate(source) + salt + curtime + secret;
        String sign = getDigest(signStr);
        params.put("appKey", id);
        params.put("q", source);
        params.put("salt", salt);
        params.put("sign", sign);

        Map<?, ?> results = RPC.form(
                "https://openapi.youdao.com/api",
                Collections.emptyMap(), params, Map.class
        );

        String errorCode = (String) results.get("errorCode");
        if (!"0".equals(errorCode)) {
            throw new IOException("fail to call you-dao api:" + errorCode);
        }

        return (String) ((List<?>) results.get("translation")).get(0);
    }

    public static String getDigest(String string) {
        if (string == null) {
            return null;
        }
        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        byte[] btInput = string.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest mdInst = MessageDigest.getInstance("SHA-256");
            mdInst.update(btInput);
            byte[] md = mdInst.digest();
            int j = md.length;
            char[] str = new char[j * 2];
            int k = 0;
            for (byte byte0 : md) {
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static String truncate(String q) {
        if (q == null) {
            return null;
        }
        int len = q.length();
        return len <= 20 ? q : (q.substring(0, 10) + len + q.substring(len - 10, len));
    }
}
