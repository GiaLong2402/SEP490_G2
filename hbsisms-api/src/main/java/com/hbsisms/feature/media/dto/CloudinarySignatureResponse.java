package com.hbsisms.feature.media.dto;

/** Payload FE dùng để POST multipart lên Cloudinary (signed, không lộ API Secret). */
public record CloudinarySignatureResponse(
        String cloudName, String apiKey, long timestamp, String signature, String folder) {}
