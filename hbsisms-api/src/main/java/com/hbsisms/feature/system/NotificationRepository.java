package com.hbsisms.feature.system;

import org.springframework.data.jpa.repository.JpaRepository;

/** Thông báo trong hệ thống (chuông ở web nội bộ — API đọc chưa làm). */
public interface NotificationRepository extends JpaRepository<Notification, Long> {}
