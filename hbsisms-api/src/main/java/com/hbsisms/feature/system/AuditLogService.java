package com.hbsisms.feature.system;

import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Append-only writer for {@link AuditLog}. */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void record(Long userId, String action, String entityType, Long entityId, Map<String, Object> detail) {
        AuditLog log = new AuditLog();
        if (userId != null) {
            AppUser user = userRepository.findById(userId).orElse(null);
            log.setUser(user);
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetail(detail == null ? null : new HashMap<>(detail));
        auditLogRepository.save(log);
    }
}
