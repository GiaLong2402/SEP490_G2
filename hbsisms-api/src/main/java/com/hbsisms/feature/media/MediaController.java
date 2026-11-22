package com.hbsisms.feature.media;

import com.hbsisms.feature.media.dto.CloudinarySignatureRequest;
import com.hbsisms.feature.media.dto.CloudinarySignatureResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/media")
public class MediaController {

    private final CloudinarySignatureService cloudinarySignatureService;

    public MediaController(CloudinarySignatureService cloudinarySignatureService) {
        this.cloudinarySignatureService = cloudinarySignatureService;
    }

    /** Staff có quyền products xin chữ ký tạm để upload ảnh lên Cloudinary. */
    @PostMapping("/cloudinary-signature")
    @PreAuthorize("hasAuthority('products')")
    public CloudinarySignatureResponse cloudinarySignature(
            @RequestBody(required = false) CloudinarySignatureRequest request) {
        String folder = request == null ? null : request.folder();
        return cloudinarySignatureService.createUploadSignature(folder);
    }
}
