package com.thedavelopers.eventqr.features.reports.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.thedavelopers.eventqr.features.reports.model.dto.EventReportDtos.EventReportFilters;
import static com.thedavelopers.eventqr.features.reports.model.dto.EventReportDtos.EventReportResponse;
import static com.thedavelopers.eventqr.features.reports.model.dto.EventReportDtos.EventReportRow;
import static com.thedavelopers.eventqr.features.reports.model.dto.EventReportDtos.EventReportSummaryResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.reports.model.ReportEmptyState;
import com.thedavelopers.eventqr.features.reports.model.ReportFilterStatus;
import com.thedavelopers.eventqr.features.reports.model.ReportType;
import com.thedavelopers.eventqr.features.rewards.model.entity.PointTransaction;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

/**
 * Builds organizer reports on demand. Deliberately NOT cached: every call re-runs the organizer
 * ownership check and returns live data, so a cached result can never leak across callers.
 */
@Service
@Transactional(readOnly = true)
public class EventReportGenerationService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.ENGLISH)
            .withZone(DISPLAY_ZONE);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH)
            .withZone(DISPLAY_ZONE);

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final ObjectMapper objectMapper;

    public EventReportGenerationService(EventRepository eventRepository,
                                        EventRegistrationRepository registrationRepository,
                                        TransactionLogRepository transactionLogRepository,
                                        PointTransactionRepository pointTransactionRepository,
                                        ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.objectMapper = objectMapper;
    }

    public EventReportSummaryResponse summary(UUID organizerUserId, UUID eventId) {
        return summary(organizerUserId, null, eventId);
    }

    /** @param callerRole ADMIN and SUPER_ADMIN may read any event's reports; everyone else must own the event. */
    public EventReportSummaryResponse summary(UUID organizerUserId, AccountRole callerRole, UUID eventId) {
        Event event = requireReportAccess(organizerUserId, callerRole, eventId);
        List<EventRegistration> registrations = registrationRepository.findByEventId(eventId);
        // Registered = registrations excluding CANCELLED and NO_SHOW — must stay in sync with DashboardService canonical count
        long registered = registrations.stream()
                .filter(reg -> reg.getStatus().isCountedAsRegistered())
                .count();
        long checkedIn = registrations.stream().filter(registration -> registration.getStatus() == RegistrationStatus.ENTERED).count();
        long exited = registrations.stream().filter(registration -> registration.getStatus() == RegistrationStatus.EXITED).count();
        boolean hasAnyRecords = registered > 0 || transactionLogRepository.countByEventId(eventId) > 0
                || pointTransactionRepository.countByEventId(eventId) > 0;
        return new EventReportSummaryResponse(eventId, event.getTitle(), registered, checkedIn, exited, hasAnyRecords);
    }

    public EventReportResponse generate(UUID organizerUserId, UUID eventId, ReportType reportType, EventReportFilters filters) {
        return generate(organizerUserId, null, eventId, reportType, filters);
    }

    public EventReportResponse generate(UUID organizerUserId, AccountRole callerRole, UUID eventId, ReportType reportType,
            EventReportFilters filters) {
        validateDateRange(filters);
        if (filters != null && filters.attendeeQuery() != null && filters.attendeeQuery().length() > MAX_ATTENDEE_QUERY_LENGTH) {
            throw new BadRequestException("attendeeQuery must be at most " + MAX_ATTENDEE_QUERY_LENGTH + " characters");
        }
        Event event = requireReportAccess(organizerUserId, callerRole, eventId);
        List<EventRegistration> registrations = registrationRepository.findByEventId(eventId);
        List<TransactionLog> transactions = transactionLogRepository.findByEventIdOrderByScannedAtDesc(eventId);
        List<PointTransaction> pointTransactions = pointTransactionRepository.findByEventId(eventId);
        Map<UUID, EventRegistration> registrationByUser = registrations.stream()
                .collect(Collectors.toMap(EventRegistration::getAttendeeUserId, registration -> registration, (first, second) -> first));

        ReportAssembly assembly = switch (reportType) {
            case ROSTER -> buildRoster(event, registrations, registrationByUser, filters);
            case NO_SHOWS -> buildNoShows(event, registrations, filters);
            case ENTRY_LOGS -> buildEntryLogs(event, registrationByUser, transactions, filters);
            case ATTENDANCE -> buildAttendance(event, registrationByUser, transactions, filters);
            case CLAIMS -> buildClaims(event, registrationByUser, transactions, filters);
            case BOOTH_VISITS -> buildBoothVisits(event, registrationByUser, transactions, filters);
            case EXIT_LOGS -> buildExitLogs(event, registrationByUser, transactions, filters);
            case POINTS -> buildPoints(event, registrationByUser, pointTransactions, filters);
        };

        ReportEmptyState emptyState = ReportEmptyState.NONE;
        if (assembly.allRows().isEmpty()) {
            emptyState = ReportEmptyState.NO_EVENT_RECORDS;
        } else if (assembly.rows().isEmpty()) {
            emptyState = ReportEmptyState.NO_FILTER_MATCH;
        }

        return new EventReportResponse(
                eventId,
                reportType,
                assembly.title(),
                event.getTitle(),
                Instant.now(),
                assembly.columns(),
                assembly.rows().stream().map(RowData::row).toList(),
                assembly.chartSeries(),
                assembly.chartSeries().values().stream().mapToLong(Long::longValue).sum(),
                emptyState,
                normalizeFilters(filters)
        );
    }

    private ReportAssembly buildRoster(Event event, List<EventRegistration> registrations,
                                       Map<UUID, EventRegistration> registrationByUser, EventReportFilters filters) {
        List<String> columns = List.of("Name", "Registration Status", "Registered On");
        List<RowData> all = registrations.stream()
                .sorted(Comparator.comparing(EventRegistration::getRegisteredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(registration -> new RowData(
                        new EventReportRow(List.of(
                                safe(registration.getAttendeeName()),
                                prettyRegistrationStatus(registration.getStatus()),
                                formatDate(registration.getRegisteredAt())
                        )),
                        registration.getRegisteredAt(),
                        safe(registration.getAttendeeName()),
                        null,
                        registration.getAttendeeUserId(),
                        0L
                )).toList();

        List<RowData> filtered = applyDateFilter(all, filters)
                .stream()
                .filter(attendeeMatcher(filters, registrationByUser))
                .filter(row -> rosterStatusMatches(filters, row, registrationByUser))
                .toList();

        Map<String, Long> chart = chartBy(columns.get(1), filtered.stream()
                .collect(Collectors.groupingBy(row -> row.row().values().get(1), LinkedHashMap::new, Collectors.counting())));

        return new ReportAssembly("Attendee Roster Report", columns, all, filtered, chart);
    }

    private ReportAssembly buildNoShows(Event event, List<EventRegistration> registrations, EventReportFilters filters) {
        List<String> columns = List.of("Name", "Registered On", "Reason");
        // Before the event starts nobody can be "not checked in" yet, so only explicitly marked no-shows are listed.
        // While running / after it ended, registrations that never entered are listed too.
        Instant now = Instant.now();
        boolean notStarted = event.getStatus() != com.thedavelopers.eventqr.shared.constants.EventStatus.ENDED
                && (event.getEventStartAt() == null || event.getEventStartAt().isAfter(now));
        List<RowData> all = registrations.stream()
                .filter(registration -> registration.getStatus() == RegistrationStatus.NO_SHOW
                        || (!notStarted && registration.getStatus() == RegistrationStatus.REGISTERED))
                .sorted(Comparator.comparing(EventRegistration::getRegisteredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(registration -> new RowData(
                        new EventReportRow(List.of(
                                safe(registration.getAttendeeName()),
                                formatDate(registration.getRegisteredAt()),
                                registration.getStatus() == RegistrationStatus.NO_SHOW ? "Marked No Show" : "Not Entered"
                        )),
                        registration.getRegisteredAt(),
                        safe(registration.getAttendeeName()),
                        null
                )).toList();

        List<RowData> filtered = applyDateFilter(all, filters);
        Map<String, Long> chart = chartBy("Reason", filtered.stream()
                .collect(Collectors.groupingBy(row -> row.row().values().get(2), LinkedHashMap::new, Collectors.counting())));
        String title = notStarted ? "Not Checked In Report (event has not started)" : "Not Checked In Report";
        return new ReportAssembly(title, columns, all, filtered, chart);
    }

    private ReportAssembly buildEntryLogs(Event event,
                                          Map<UUID, EventRegistration> registrationByUser,
                                          List<TransactionLog> transactions,
                                          EventReportFilters filters) {
        List<String> columns = List.of("Name", "Entry Time", "Result");
        List<RowData> all = transactions.stream()
                .filter(transaction -> transaction.getTransactionType() == TransactionType.ENTRY)
                .map(transaction -> toTransactionRow(transaction, registrationByUser,
                        safeAttendeeName(registrationByUser, transaction.getAttendeeUserId()),
                        formatDateTime(transaction.getScannedAt()),
                        prettyResult(transaction.getTransactionResult())))
                .toList();

        List<RowData> filtered = applyStatusFilter(applyDateFilter(all, filters), filters);
        Map<String, Long> chart = chartBy("Result", filtered.stream()
                .collect(Collectors.groupingBy(row -> row.row().values().get(2), LinkedHashMap::new, Collectors.counting())));
        return new ReportAssembly("Entry Logs Report", columns, all, filtered, chart);
    }

    private ReportAssembly buildAttendance(Event event,
                                           Map<UUID, EventRegistration> registrationByUser,
                                           List<TransactionLog> transactions,
                                           EventReportFilters filters) {
        List<String> columns = List.of("Name", "Session/Activity", "Timestamp", "Result");
        List<RowData> all = transactions.stream()
                .filter(transaction -> transaction.getTransactionType() == TransactionType.ATTENDANCE)
                .map(transaction -> new RowData(
                        new EventReportRow(List.of(
                                safeAttendeeName(registrationByUser, transaction.getAttendeeUserId()),
                                extractActivityLabel(transaction),
                                formatDateTime(transaction.getScannedAt()),
                                prettyResult(transaction.getTransactionResult())
                        )),
                        transaction.getScannedAt(),
                        safeAttendeeName(registrationByUser, transaction.getAttendeeUserId()),
                        transaction.getTransactionResult(),
                        transaction.getAttendeeUserId(),
                        0L
                ))
                .toList();

        List<RowData> filtered = applyStatusFilter(applyDateFilter(all, filters), filters)
                .stream()
                .filter(attendeeMatcher(filters, registrationByUser))
                .toList();

        // Chart counts successful (APPROVED) attendance only; rejected scans stay visible in the table.
        Map<String, Long> chart = chartBy("Activity", filtered.stream()
                .filter(row -> row.result() == TransactionResult.APPROVED)
                .collect(Collectors.groupingBy(row -> row.row().values().get(1), LinkedHashMap::new, Collectors.counting())));
        return new ReportAssembly("Attendance Report", columns, all, filtered, chart);
    }

    private ReportAssembly buildClaims(Event event,
                                       Map<UUID, EventRegistration> registrationByUser,
                                       List<TransactionLog> transactions,
                                       EventReportFilters filters) {
        List<String> columns = List.of("Name", "Benefit", "Claimed At", "Result");
        List<RowData> all = transactions.stream()
                .filter(transaction -> transaction.getTransactionType() == TransactionType.BENEFIT_CLAIM)
                .map(transaction -> toTransactionRow(transaction, registrationByUser,
                        safeAttendeeName(registrationByUser, transaction.getAttendeeUserId()),
                        benefitLabel(transaction),
                        formatDateTime(transaction.getScannedAt()),
                        prettyResult(transaction.getTransactionResult())))
                .toList();

        List<RowData> filtered = applyStatusFilter(applyDateFilter(all, filters), filters);
        Map<String, Long> chart = chartBy("Result", filtered.stream()
                .collect(Collectors.groupingBy(row -> row.row().values().get(3), LinkedHashMap::new, Collectors.counting())));
        return new ReportAssembly("Benefit Claims Report", columns, all, filtered, chart);
    }

    private ReportAssembly buildBoothVisits(Event event,
                                            Map<UUID, EventRegistration> registrationByUser,
                                            List<TransactionLog> transactions,
                                            EventReportFilters filters) {
        List<String> columns = List.of("Name", "Booth/Session", "Visit Time", "Result");
        List<RowData> all = transactions.stream()
                .filter(transaction -> transaction.getTransactionType() == TransactionType.BOOTH_VISIT
                        || transaction.getTransactionType() == TransactionType.SESSION_VISIT)
                .map(transaction -> new RowData(
                        new EventReportRow(List.of(
                                safeAttendeeName(registrationByUser, transaction.getAttendeeUserId()),
                                extractActivityLabel(transaction),
                                formatDateTime(transaction.getScannedAt()),
                                prettyResult(transaction.getTransactionResult())
                        )),
                        transaction.getScannedAt(),
                        safeAttendeeName(registrationByUser, transaction.getAttendeeUserId()),
                        transaction.getTransactionResult(),
                        transaction.getAttendeeUserId(),
                        0L
                ))
                .toList();

        List<RowData> filtered = applyStatusFilter(applyDateFilter(all, filters), filters);
        // Chart counts successful (APPROVED) visits only; rejected scans stay visible in the table.
        Map<String, Long> chart = chartBy("Visit Type", filtered.stream()
                .filter(row -> row.result() == TransactionResult.APPROVED)
                .collect(Collectors.groupingBy(row -> row.row().values().get(1), LinkedHashMap::new, Collectors.counting())));
        return new ReportAssembly("Booth/Session Visits Report", columns, all, filtered, chart);
    }

    private ReportAssembly buildExitLogs(Event event,
                                         Map<UUID, EventRegistration> registrationByUser,
                                         List<TransactionLog> transactions,
                                         EventReportFilters filters) {
        List<String> columns = List.of("Name", "Exit Time", "Result");
        List<RowData> all = transactions.stream()
                .filter(transaction -> transaction.getTransactionType() == TransactionType.EXIT)
                .map(transaction -> toTransactionRow(transaction, registrationByUser,
                        safeAttendeeName(registrationByUser, transaction.getAttendeeUserId()),
                        formatDateTime(transaction.getScannedAt()),
                        prettyResult(transaction.getTransactionResult())))
                .toList();

        List<RowData> filtered = applyStatusFilter(applyDateFilter(all, filters), filters);
        Map<String, Long> chart = chartBy("Result", filtered.stream()
                .collect(Collectors.groupingBy(row -> row.row().values().get(2), LinkedHashMap::new, Collectors.counting())));
        return new ReportAssembly("Exit Logs Report", columns, all, filtered, chart);
    }

    private ReportAssembly buildPoints(Event event,
                                       Map<UUID, EventRegistration> registrationByUser,
                                       List<PointTransaction> pointTransactions,
                                       EventReportFilters filters) {
        List<String> columns = List.of("Name", "Points (+/-)", "Source Activity");
        List<RowData> all = pointTransactions.stream()
                .sorted(Comparator.comparing(PointTransaction::getOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(transaction -> new RowData(
                        new EventReportRow(List.of(
                                safeAttendeeName(registrationByUser, transaction.getAttendeeUserId()),
                                String.valueOf(transaction.getPointsChanged()),
                                safe(transaction.getReason()).isBlank() ? "Scan reward points" : safe(transaction.getReason())
                        )),
                        transaction.getOccurredAt(),
                        safeAttendeeName(registrationByUser, transaction.getAttendeeUserId()),
                        null,
                        transaction.getAttendeeUserId(),
                        transaction.getPointsChanged()
                ))
                .toList();

        List<RowData> filtered = applyDateFilter(all, filters)
                .stream()
                .filter(attendeeMatcher(filters, registrationByUser))
                .toList();

        // Chart sums POSITIVE points awarded per activity (not row counts); deductions are excluded.
        Map<String, Long> chart = chartBy("Source", filtered.stream()
                .filter(row -> row.points() > 0)
                .collect(Collectors.groupingBy(row -> row.row().values().get(2), LinkedHashMap::new,
                        Collectors.summingLong(RowData::points))));
        return new ReportAssembly("Points Report", columns, all, filtered, chart);
    }

    private List<RowData> applyDateFilter(List<RowData> rows, EventReportFilters filters) {
        if (filters == null || (filters.startDate() == null && filters.endDate() == null)) {
            return rows;
        }
        LocalDate start = filters.startDate();
        LocalDate end = filters.endDate();
        return rows.stream()
                .filter(row -> {
                    if (row.occurredAt() == null) {
                        return false;
                    }
                    LocalDate day = row.occurredAt().atZone(DISPLAY_ZONE).toLocalDate();
                    boolean afterStart = start == null || !day.isBefore(start);
                    boolean beforeEnd = end == null || !day.isAfter(end);
                    return afterStart && beforeEnd;
                })
                .toList();
    }

    private List<RowData> applyStatusFilter(List<RowData> rows, EventReportFilters filters) {
        if (filters == null || filters.status() == null || filters.status() == ReportFilterStatus.ALL) {
            return rows;
        }
        Predicate<RowData> matcher = switch (filters.status()) {
            case APPROVED -> row -> row.result() == TransactionResult.APPROVED;
            case REJECTED -> row -> row.result() == TransactionResult.REJECTED;
            case ALL -> row -> true;
        };
        return rows.stream().filter(matcher).toList();
    }

    /**
     * Roster has no scan result, so the status filter maps onto registration state: APPROVED keeps registrations that
     * count as registered (not cancelled / no-show), REJECTED keeps the cancelled and no-show ones.
     */
    private boolean rosterStatusMatches(EventReportFilters filters, RowData row,
                                        Map<UUID, EventRegistration> registrationByUser) {
        if (filters == null || filters.status() == null || filters.status() == ReportFilterStatus.ALL) {
            return true;
        }
        EventRegistration registration = row.attendeeUserId() == null ? null : registrationByUser.get(row.attendeeUserId());
        if (registration == null) {
            return false;
        }
        boolean counted = registration.getStatus().isCountedAsRegistered();
        return filters.status() == ReportFilterStatus.APPROVED ? counted : !counted;
    }

    /**
     * Substring search (name, email, registration number) for partial typing. When the trimmed query exactly equals
     * (case-insensitive) some registrant's full name or email, only exact matches are kept, so "QA Tester" does not
     * also return "QA Tester2". Evaluated once per report, across all registrations of the event.
     */
    private Predicate<RowData> attendeeMatcher(EventReportFilters filters,
                                               Map<UUID, EventRegistration> registrationByUser) {
        if (filters == null || filters.attendeeQuery() == null || filters.attendeeQuery().isBlank()) {
            return row -> true;
        }
        String query = filters.attendeeQuery().trim().toLowerCase(Locale.ENGLISH);
        boolean exact = registrationByUser.values().stream().anyMatch(r ->
                lower(r.getAttendeeName()).equals(query) || lower(r.getAttendeeEmail()).equals(query));
        return row -> attendeeMatches(query, exact, row, registrationByUser);
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private boolean attendeeMatches(String query, boolean exact, RowData row,
                                    Map<UUID, EventRegistration> registrationByUser) {
        String name = safe(row.attendeeName()).trim().toLowerCase(Locale.ENGLISH);
        if (exact ? name.equals(query) : name.contains(query)) {
            return true;
        }
        EventRegistration registration = row.attendeeUserId() == null ? null : registrationByUser.get(row.attendeeUserId());
        if (registration == null) {
            return false;
        }
        String email = lower(registration.getAttendeeEmail());
        if (exact ? email.equals(query) : email.contains(query)) {
            return true;
        }
        if (registration.getRegistrationNumber() != null) {
            String number = String.valueOf(registration.getRegistrationNumber());
            String numberQuery = query.startsWith("#") ? query.substring(1).trim() : query;
            return !numberQuery.isEmpty() && (exact ? number.equals(numberQuery) : number.contains(numberQuery));
        }
        return false;
    }

    private EventReportFilters normalizeFilters(EventReportFilters filters) {
        if (filters == null) {
            return new EventReportFilters(null, null, null, ReportFilterStatus.ALL);
        }
        return new EventReportFilters(
                filters.startDate(),
                filters.endDate(),
                filters.attendeeQuery(),
                filters.status() == null ? ReportFilterStatus.ALL : filters.status()
        );
    }

    private Event requireReportAccess(UUID userId, AccountRole callerRole, UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (callerRole == AccountRole.ADMIN || callerRole == AccountRole.SUPER_ADMIN) {
            return event;
        }
        return requireOrganizerEvent(userId, eventId);
    }

    private Event requireOrganizerEvent(UUID organizerUserId, UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (event.getOrganizerUserId() == null || !event.getOrganizerUserId().equals(organizerUserId)) {
            throw new ForbiddenException("Organizer does not have access to this event");
        }
        return event;
    }

    private void validateDateRange(EventReportFilters filters) {
        if (filters == null || filters.startDate() == null || filters.endDate() == null) {
            return;
        }
        if (filters.endDate().isBefore(filters.startDate())) {
            throw new BadRequestException("endDate must not be before startDate");
        }
    }

    private RowData toTransactionRow(TransactionLog transaction,
                                     Map<UUID, EventRegistration> registrationByUser,
                                     String value1,
                                     String value2,
                                     String value3) {
        return new RowData(
                new EventReportRow(List.of(value1, value2, value3)),
                transaction.getScannedAt(),
                safeAttendeeName(registrationByUser, transaction.getAttendeeUserId()),
                transaction.getTransactionResult(),
                transaction.getAttendeeUserId(),
                0L
        );
    }

    private RowData toTransactionRow(TransactionLog transaction,
                                     Map<UUID, EventRegistration> registrationByUser,
                                     String value1,
                                     String value2,
                                     String value3,
                                     String value4) {
        return new RowData(
                new EventReportRow(List.of(value1, value2, value3, value4)),
                transaction.getScannedAt(),
                safeAttendeeName(registrationByUser, transaction.getAttendeeUserId()),
                transaction.getTransactionResult(),
                transaction.getAttendeeUserId(),
                0L
        );
    }

    private String safeAttendeeName(Map<UUID, EventRegistration> registrations, UUID attendeeUserId) {
        return Optional.ofNullable(registrations.get(attendeeUserId))
                .map(EventRegistration::getAttendeeName)
                .map(this::safe)
                .filter(name -> !name.isBlank())
                .orElse("Unknown attendee");
    }

    private String extractActivityLabel(TransactionLog transaction) {
        String fromMetadata = metadataValue(transaction.getMetadata(), "scanPurposeLabel")
                .orElseGet(() -> metadataValue(transaction.getMetadata(), "scanPurposeCode").orElse(""));
        if (!fromMetadata.isBlank()) {
            return fromMetadata;
        }
        return switch (transaction.getTransactionType()) {
            case SESSION_VISIT -> "Session Visit";
            case BOOTH_VISIT -> "Booth Visit";
            case ATTENDANCE -> "Attendance";
            default -> prettyType(transaction.getTransactionType());
        };
    }

    private String benefitLabel(TransactionLog transaction) {
        if (transaction.getReason() != null && !transaction.getReason().isBlank()) {
            return transaction.getReason();
        }
        String fromMetadata = metadataValue(transaction.getMetadata(), "scanPurposeLabel").orElse("");
        if (!fromMetadata.isBlank()) {
            return fromMetadata;
        }
        return "Benefit Claim";
    }

    private Optional<String> metadataValue(String metadata, String key) {
        if (metadata == null || metadata.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            JsonNode valueNode = node.get(key);
            if (valueNode == null || valueNode.isNull()) {
                return Optional.empty();
            }
            return Optional.ofNullable(valueNode.asText(""));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static final int CHART_TOP_CATEGORIES = 5;
    private static final int MAX_ATTENDEE_QUERY_LENGTH = 100;

    /** Top 5 categories by value plus an "Other" bucket so the series always sums to the full total. */
    private Map<String, Long> chartBy(String key, Map<String, Long> values) {
        if (values.isEmpty()) {
            return Map.of(key, 0L);
        }
        List<Map.Entry<String, Long>> sorted = values.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .toList();
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        long other = 0L;
        for (int i = 0; i < sorted.size(); i++) {
            if (i < CHART_TOP_CATEGORIES) {
                result.put(sorted.get(i).getKey(), sorted.get(i).getValue());
            } else {
                other += sorted.get(i).getValue();
            }
        }
        if (other > 0) {
            result.merge("Other", other, Long::sum);
        }
        return result;
    }

    private String prettyResult(TransactionResult result) {
        if (result == null) {
            return "Unknown";
        }
        return switch (result) {
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
        };
    }

    private String prettyRegistrationStatus(RegistrationStatus status) {
        return switch (status) {
            case REGISTERED -> "Registered";
            case ENTERED -> "Entered";
            case EXITED -> "Exited";
            case NO_SHOW -> "No Show";
            case CANCELLED -> "Cancelled";
        };
    }

    private String prettyType(TransactionType type) {
        String normalized = type.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
        String[] chunks = normalized.split(" ");
        List<String> titled = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk.isBlank()) {
                continue;
            }
            titled.add(Character.toUpperCase(chunk.charAt(0)) + chunk.substring(1));
        }
        return String.join(" ", titled);
    }

    private String formatDateTime(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return DATE_TIME_FORMATTER.format(instant);
    }

    private String formatDate(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return DATE_FORMATTER.format(instant);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record RowData(EventReportRow row, Instant occurredAt, String attendeeName, TransactionResult result,
                           UUID attendeeUserId, long points) {
        RowData(EventReportRow row, Instant occurredAt, String attendeeName, TransactionResult result) {
            this(row, occurredAt, attendeeName, result, null, 0L);
        }
    }

    private record ReportAssembly(String title,
                                  List<String> columns,
                                  List<RowData> allRows,
                                  List<RowData> rows,
                                  Map<String, Long> chartSeries) {
    }
}
