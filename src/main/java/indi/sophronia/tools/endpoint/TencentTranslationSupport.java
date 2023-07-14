package indi.sophronia.tools.endpoint;

import indi.sophronia.tools.util.Language;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.tmt.v20180321.TmtClient;
import com.tencentcloudapi.tmt.v20180321.models.*;

import java.io.IOException;
import java.util.Properties;

public class TencentTranslationSupport extends TranslationApiEndpoint {
    private String tencentId;
    private String tencentSecret;
    private String region;

    @Override
    public boolean onLoadingValidate(Properties properties) {
        tencentId = properties.getProperty("tencent.id");
        tencentSecret = properties.getProperty("tencent.secret");
        region = properties.getProperty("tencent.region");
        return tencentId != null && tencentSecret != null && region != null;
    }

    @Override
    protected String doTranslate(String source, Language sourceLanguage, Language targetLanguage) throws IOException {
        String from = convertLanguage(sourceLanguage);
        String to = convertLanguage(targetLanguage);
        if (to == null) {
            return null;
        }

        try{
            Credential cred = new Credential(tencentId, tencentSecret);
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("tmt.tencentcloudapi.com");
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            TmtClient client = new TmtClient(cred, region, clientProfile);
            TextTranslateRequest req = new TextTranslateRequest();
            req.setProjectId(0L);
            req.setSourceText(source);
            req.setSource(from == null ? "auto" : from);
            req.setTarget(to);

            TextTranslateResponse resp = client.TextTranslate(req);
            return resp.getTargetText();
        } catch (TencentCloudSDKException e) {
            throw new IOException(e.toString());
        }
    }
    private static String convertLanguage(Language language) {
        return switch (language) {
            case CHINESE -> "zh";
            case KOREAN -> "ko";
            case JAPANESE -> "ja";
            case ENGLISH -> "en";
            case UNKNOWN -> null;
        };
    }
}
