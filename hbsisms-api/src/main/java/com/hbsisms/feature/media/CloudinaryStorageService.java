package com.hbsisms.feature.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.hbsisms.common.exception.ApiException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CloudinaryStorageService {

    static final int MAX_FILES = 10;
    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CloudinaryProperties properties;
    private final Cloudinary cloudinary;

    public CloudinaryStorageService(CloudinaryProperties properties, Cloudinary cloudinary) {
        this.properties = properties;
        this.cloudinary = cloudinary;
    }

    public List<String> uploadImages(List<MultipartFile> files) {
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình Cloudinary để tải ảnh");
        }
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("Chọn ít nhất một ảnh để tải lên");
        }
        if (files.size() > MAX_FILES) {
            throw ApiException.badRequest("Tối đa " + MAX_FILES + " ảnh mỗi lần tải");
        }

        List<String> urls = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            validate(file);
            urls.add(uploadOne(file));
        }
        return urls;
    }

    void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File ảnh trống");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Mỗi ảnh tối đa 5MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.badRequest("Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP");
        }
    }

    @SuppressWarnings("unchecked")
    private String uploadOne(MultipartFile file) {
        try {
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", properties.resolvedFolder(),
                            "resource_type", "image"));
            Object secureUrl = result.get("secure_url");
            if (!(secureUrl instanceof String url) || !StringUtils.hasText(url)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Cloudinary không trả URL ảnh");
            }
            return url;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Không tải được ảnh lên Cloudinary");
        }
    }
}
