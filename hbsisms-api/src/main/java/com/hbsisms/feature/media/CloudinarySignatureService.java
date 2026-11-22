package com.hbsisms.feature.media;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.media.dto.CloudinarySignatureResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CloudinarySignatureService {

    private final CloudinaryProperties properties;

    public CloudinarySignatureService(CloudinaryProperties properties) {
        this.properties = properties;
    }

    public CloudinarySignatureResponse createUploadSignature(String folderOverride) {
        if (!properties.enabled()) {
            throw ApiException.badRequest(
                    "Cloudinary chưa được cấu hình (CLOUDINARY_CLOUD_NAME / API_KEY / API_SECRET)");
        }

        String folder = StringUtils.hasText(folderOverride)
                ? folderOverride.trim()
                : properties.resolvedFolder();
        long timestamp = System.currentTimeMillis() / 1000L;

        // Chỉ các param gửi kèm upload (trừ file, cloud_name, resource_type, api_key) mới ký.
        Map<String, String> toSign = new TreeMap<>();
        toSign.put("folder", folder);
        toSign.put("timestamp", Long.toString(timestamp));

        String signature = sign(toSign, properties.apiSecret());

        return new CloudinarySignatureResponse(
                properties.cloudName().trim(),
                properties.apiKey().trim(),
                timestamp,
                signature,
                folder);
    }

    static String sign(Map<String, String> params, String apiSecret) {
        String payload = params.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining("&"))
                + apiSecret;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 không khả dụng", e);
        }
    }
}
