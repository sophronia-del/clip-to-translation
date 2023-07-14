package indi.sophronia.tools.endpoint;

import indi.sophronia.tools.util.Language;
import indi.sophronia.tools.util.RPC;
import indi.sophronia.tools.util.StringHelper;

import java.io.IOException;
import java.util.*;

public class BaiduTranslationSupport extends TranslationApiEndpoint {
    private static final String TRANS_API_HOST = "http://api.fanyi.baidu.com/api/trans/vip/translate";

    private String appid;
    private String securityKey;

    @Override
    public boolean onLoadingValidate(Properties properties) {
        appid = properties.getProperty("baidu.id");
        securityKey = properties.getProperty("baidu.secret");
        return appid != null && securityKey != null;
    }

    @Override
    protected String doTranslate(String source, Language sourceLanguage, Language targetLanguage) throws IOException {
        String from = convertLanguage(sourceLanguage);
        String to = convertLanguage(targetLanguage);
        if (to == null) {
            return null;
        }

        Map<String, String> params = new HashMap<>();
        params.put("q", source);
        params.put("from", from == null ? "auto" : from);
        params.put("to", to);

        params.put("appid", appid);

        String salt = String.valueOf(System.currentTimeMillis());
        params.put("salt", salt);

        String src = appid + source + salt + securityKey; // 加密前的原文
        params.put("sign", bytesToString(StringHelper.digest(src)));

        Map<?, ?> results = RPC.form(TRANS_API_HOST, Collections.emptyMap(), params, Map.class);

        String errorCode = (String) results.get("error_code");
        if (errorCode != null && !"0".equals(errorCode)) {
            throw new IOException("fail to call baidu api:" + errorCode);
        }

        return (String) ((Map<?, ?>) ((List<?>) results.get("trans_result")).get(0)).get("dst");
    }


    private static String convertLanguage(Language language) {
        return switch (language) {
            case CHINESE -> "zh";
            case KOREAN -> "kor";
            case JAPANESE -> "jp";
            case ENGLISH -> "en";
            case UNKNOWN -> null;
        };
    }


    private static final char[] hexDigits = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f'
    };

    private static String bytesToString(byte[] bytes) {
        // new一个字符数组，这个就是用来组成结果字符串的（解释一下：一个byte是八位二进制，也就是2位十六进制字符（2的8次方等于16的2次方））
        char[] resultCharArray = new char[bytes.length * 2];
        // 遍历字节数组，通过位运算（位运算效率高），转换成字符放到字符数组中去
        int index = 0;
        for (byte b : bytes) {
            resultCharArray[index++] = hexDigits[b >>> 4 & 0xf];
            resultCharArray[index++] = hexDigits[b & 0xf];
        }

        // 字符数组组合成字符串返回
        return new String(resultCharArray);

    }
}
