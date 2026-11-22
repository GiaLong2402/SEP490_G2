package com.hbsisms.feature.media.dto;

/** Optional folder override; null/blank → default {@code products}. */
public record CloudinarySignatureRequest(String folder) {}
