package com.thedavelopers.eventqr.features.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.admin.model.dto.AdminStatsResponse;
import com.thedavelopers.eventqr.features.auditlogs.repository.AuditLogRepository;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;

@Service
@Transactional(readOnly = true)
public class AdminStatsService {

    private static final List<AccountRole> ADMIN_ROLES = List.of(AccountRole.ADMIN, AccountRole.SUPER_ADMIN);

    private final UserProfileRepository userProfileRepository;
    private final EventRepository eventRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminStatsService(UserProfileRepository userProfileRepository, EventRepository eventRepository,
                             AuditLogRepository auditLogRepository) {
        this.userProfileRepository = userProfileRepository;
        this.eventRepository = eventRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Account visibility mirrors GET /users: SUPER_ADMIN sees every account, a plain ADMIN never sees
     * ADMIN/SUPER_ADMIN accounts.
     */
    public AdminStatsResponse stats(AccountRole callerRole) {
        long accounts = callerRole == AccountRole.SUPER_ADMIN
                ? userProfileRepository.count()
                : userProfileRepository.countByRoleNotIn(ADMIN_ROLES);
        return new AdminStatsResponse(accounts, eventRepository.countByStatus(EventStatus.ACTIVE), auditLogRepository.count());
    }
}
