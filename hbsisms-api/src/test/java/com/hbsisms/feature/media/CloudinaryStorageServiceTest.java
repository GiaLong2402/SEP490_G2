package com.hbsisms.feature.media;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.hbsisms.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class CloudinaryStorageServiceTest {

    private Cloudinary cloudinary;
    private Uploader uploader;
    private CloudinaryStorageService service;

    @BeforeEach
    void setUp() {
        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        service = new CloudinaryStorageService(
                new CloudinaryProperties("demo", "key", "secret", "products"), cloudinary);
    }

    @Test
    void rejectsWhenCloudinaryNotConfigured() {
        CloudinaryStorageService disabled = new CloudinaryStorageService(
                new CloudinaryProperties("", "", "", "products"), cloudinary);
        assertThatThrownBy(() -> disabled.uploadImages(List.of(jpeg("a.jpg"))))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void rejectsEmptyList() {
        assertThatThrownBy(() -> service.uploadImages(List.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ít nhất một ảnh");
    }

    @Test
    void rejectsNonImageContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.uploadImages(List.of(file)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("JPEG");
    }

    @Test
    void rejectsOversizedFile() {
        byte[] big = new byte[(int) CloudinaryStorageService.MAX_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile("files", "a.jpg", "image/jpeg", big);
        assertThatThrownBy(() -> service.uploadImages(List.of(file)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void uploadsAndReturnsSecureUrl() throws Exception {
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/a.jpg"));
        var urls = service.uploadImages(List.of(jpeg("a.jpg")));
        org.assertj.core.api.Assertions.assertThat(urls)
                .containsExactly("https://res.cloudinary.com/demo/image/upload/a.jpg");
    }

    private static MockMultipartFile jpeg(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8});
    }
}
