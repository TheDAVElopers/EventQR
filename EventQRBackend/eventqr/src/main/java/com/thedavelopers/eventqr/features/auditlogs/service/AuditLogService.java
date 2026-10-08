package com.thedavelopers.eventqr.features.auditlogs.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.auditlogs.model.dto.AuditLogRequest;
import com.thedavelopers.eventqr.features.auditlogs.model.dto.AuditLogResponse;
import com.thedavelopers.eventqr.features.auditlogs.model.entity.AuditLog;
import com.thedavelopers.eventqr.features.auditlogs.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    static final int MAX_ACTION_PREFIX = 64;
    private final AuditLogRepository auditLogRepository;
    private final UserProfileRepository userProfileRepository;

    public void log(AuditLogRequest request, UUID userId, String fullName) {
        log(request.action(), request.details(), userId, fullName, request.eventId(), request.targetUserId());
    }

    public void log(String action, String details, UUID userId, String fullName, UUID eventId, UUID targetUserId) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setDetails(details);
        log.setPerformedByUserId(userId);
        log.setPerformedByFullName(normalizeName(fullName));
        log.setEventId(eventId);
        log.setTargetUserId(targetUserId);
        auditLogRepository.save(log);
    }

    public List<AuditLogResponse> findAll() {
        return mapAll(auditLogRepository.findAllByOrderByCreatedAtDesc(), AccountRole.SUPER_ADMIN);
    }

    /** Newest first, id as tiebreaker so pages never overlap. */
    public Page<AuditLogResponse> findAll(int page, int size) {
        return findAll(page, size, null, AccountRole.SUPER_ADMIN);
    }

    /**
     * Newest first. {@code actionPrefix} (case-sensitive prefix of {@code action}, blank = none, max 64 chars)
     * narrows the page. Target names of ADMIN/SUPER_ADMIN accounts are hidden from callers who are not SUPER_ADMIN.
     */
    public Page<AuditLogResponse> findAll(int page, int size, String actionPrefix, AccountRole callerRole) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<AuditLog> result;
        if (actionPrefix == null || actionPrefix.isBlank()) {
            result = auditLogRepository.findAll(pageable);
        } else {
            if (actionPrefix.length() > MAX_ACTION_PREFIX) {
                throw new BadRequestException("actionPrefix must be at most " + MAX_ACTION_PREFIX + " characters");
            }
            result = auditLogRepository.findByActionStartingWith(actionPrefix, pageable);
        }
        List<AuditLogResponse> mapped = mapAll(result.getContent(), callerRole);
        return new PageImpl<>(mapped, pageable, result.getTotalElements());
    }

    public List<AuditLogResponse> findByEvent(UUID eventId) {
        return findByEvent(eventId, AccountRole.SUPER_ADMIN);
    }

    public List<AuditLogResponse> findByEvent(UUID eventId, AccountRole callerRole) {
        return mapAll(auditLogRepository.findByEventIdOrderByCreatedAtDesc(eventId), callerRole);
    }

    /** Maps a list of logs resolving every target user's name with one batched lookup. */
    private List<AuditLogResponse> mapAll(List<AuditLog> logs, AccountRole callerRole) {
        Set<UUID> targetIds = logs.stream().map(AuditLog::getTargetUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        Set<UUID> hiddenTargets = new HashSet<>();
        if (!targetIds.isEmpty()) {
            for (UserProfile user : userProfileRepository.findAllById(targetIds)) {
                boolean hidden = callerRole != AccountRole.SUPER_ADMIN
                        && (user.getRole() == AccountRole.ADMIN || user.getRole() == AccountRole.SUPER_ADMIN);
                if (hidden) {
                    hiddenTargets.add(user.getId());
                } else {
                    names.put(user.getId(), user.getFullName());
                }
            }
        }
        return logs.stream().map(log -> mapToResponse(log, names.get(log.getTargetUserId()),
                hidesDetails(log, callerRole, names, hiddenTargets))).collect(Collectors.toList());
    }

    /**
     * Account actions store the target's name as their details, so those must be hidden with the target name: for
     * ADMIN/SUPER_ADMIN targets, and for targets that no longer exist (their role can no longer be checked).
     */
    private boolean hidesDetails(AuditLog log, AccountRole callerRole, Map<UUID, String> visibleNames, Set<UUID> hiddenTargets) {
        if (callerRole == AccountRole.SUPER_ADMIN || log.getTargetUserId() == null) {
            return false;
        }
        if (hiddenTargets.contains(log.getTargetUserId())) {
            return true;
        }
        return log.getAction() != null && log.getAction().startsWith("ACCOUNT_") && !visibleNames.containsKey(log.getTargetUserId());
    }

    private AuditLogResponse mapToResponse(AuditLog log, String targetUserFullName, boolean hideDetails) {
        return new AuditLogResponse(log.getId(), log.getAction(), hideDetails ? null : log.getDetails(), log.getPerformedByUserId(),
                log.getPerformedByFullName(), log.getEventId(), log.getTargetUserId(), log.getCreatedAt(), targetUserFullName);
    }

    private String normalizeName(String fullName) {
        return fullName == null || fullName.isBlank() ? "Admin User" : fullName.trim();
    }
}
