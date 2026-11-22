package com.hbsisms.feature.media.dto;

import java.util.List;

/** Result of multipart product image upload to Cloudinary. */
public record ImageUploadResponse(List<String> urls) {}
