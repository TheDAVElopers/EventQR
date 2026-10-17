package com.thedavelopers.eventqr.features.idprinting.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdTemplateConfigResponse;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdTemplate;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

/**
 * SDD Module 3.7 — Configure ID Display Fields.
 *
 * SDD 3.7 deviation note (capstone defense): SRS UC-22 describes logo upload, color editing,
 * and predefined template selection; SDD 3.7 explicitly overrides it — "organizer cannot edit
 * the ID layout, design, colors, logo, or visual format." Only field visibility toggling is
 * implemented here.
 *
 * Scope-tracked decision: ATTENDEE_ID maps to event_registrations.registration_number (V13),
 * a per-event 1..N sequence assigned by a DB trigger. This replaces the earlier
 * user_profiles.id (UUID) source — the UUID was visually poor on a printed card (36 chars)
 * and was documented as a known limitation; registration_number resolves it without any SDD
 * deviation.
 */
@Service
public class IdTemplateConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(IdTemplateConfigurationService.class);

    /** The only optional fields an organizer may toggle (SDD 3.7). */
    public static final List<String> OPTIONAL_FIELDS = List.of("ATTENDEE_ID", "ROLE", "EVENT_NAME", "EVENT_DATE");

    /**
     * Always rendered on every printed ID; not organizer-editable but stored explicitly in
     * template_json so the renderer has one authoritative shape to read.
     */
    public static final List<String> LOCKED_FIELDS = List.of("QR_CODE", "ATTENDEE_NAME");

    private static final String DEFAULT_TEMPLATE_NAME = "ID Display Configuration";

    /**
     * Visible optional fields of the default template created when an event has none: nothing beyond the locked
     * QR code and attendee name. Matches the ID Display Settings screen, whose toggles all start off.
     */
    public static final List<String> DEFAULT_VISIBLE_FIELDS = List.of();

    /** Advisory-lock namespace ("EQIT") for find-or-create of an event's active ID template. */
    static final int TEMPLATE_LOCK_NAMESPACE = 0x45514954;

    private final IdTemplateRepository idTemplateRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public IdTemplateConfigurationService(IdTemplateRepository idTemplateRepository,
                                          EventRepository eventRepository,
                                          ObjectMapper objectMapper) {
        this.idTemplateRepository = idTemplateRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IdTemplateConfigResponse saveConfig(UUID eventId, List<String> visibleFields, UUID actorUserId) {
        requireOwnedEvent(eventId, actorUserId);

        if (visibleFields == null) {
            visibleFields = List.of();
        }
        for (String field : visibleFields) {
            // Reject loudly rather than silently dropping unknown values: agent-side bugs should be visible.
            if (!OPTIONAL_FIELDS.contains(field)) {
                throw new BadRequestException("Unknown ID display field: " + field);
            }
        }
        List<String> normalized = visibleFields.stream().distinct().toList();

        lockTemplates(eventId);
        IdTemplate template = idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId)
                .orElseGet(IdTemplate::new);
        boolean isNewRow = template.getId() == null;
        template.setEventId(eventId);
        if (template.getName() == null || template.getName().isBlank()) {
            template.setName(DEFAULT_TEMPLATE_NAME);
        }
        template.setActive(true);
        template.setTemplateJson(writeJson(normalized));
        if (isNewRow) {
            template.setCreatedByUserId(actorUserId);
        }
        idTemplateRepository.save(template);
        return new IdTemplateConfigResponse(eventId, normalized, LOCKED_FIELDS);
    }

    /**
     * Returns the event's active configuration, creating the default template first when the event has none, so
     * the app renders the same card the print endpoints will log against. Note: this GET can therefore write (one
     * id_templates row per event, at most once).
     */
    @Transactional
    public IdTemplateConfigResponse getConfig(UUID eventId) {
        IdTemplate template = getOrCreateActiveTemplate(eventId);
        return new IdTemplateConfigResponse(eventId, parseVisibleFields(template.getTemplateJson()), LOCKED_FIELDS);
    }

    /**
     * The event's active ID template, or a newly created default one (QR code + attendee name only) when none
     * exists. Idempotent and safe under concurrency: a fast path reads without locking; otherwise a
     * transaction-scoped advisory lock per event serializes creators and the active row is re-checked under it,
     * so concurrent first prints for one event create exactly one template. Must run inside a transaction.
     */
    @Transactional
    public IdTemplate getOrCreateActiveTemplate(UUID eventId) {
        Optional<IdTemplate> existing = idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event not found");
        }
        lockTemplates(eventId);
        return idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId).orElseGet(() -> {
            IdTemplate template = new IdTemplate();
            template.setEventId(eventId);
            template.setName(DEFAULT_TEMPLATE_NAME);
            template.setActive(true);
            template.setTemplateJson(writeJson(DEFAULT_VISIBLE_FIELDS));
            log.info("Created default ID template eventId={}", eventId);
            return idTemplateRepository.save(template);
        });
    }

    private void lockTemplates(UUID eventId) {
        idTemplateRepository.boundLockWaits();
        idTemplateRepository.acquireTemplateLock(TEMPLATE_LOCK_NAMESPACE, eventId.hashCode());
    }

    private String writeJson(List<String> visibleFields) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("visibleFields", objectMapper.valueToTree(visibleFields));
        node.set("lockedFields", objectMapper.valueToTree(LOCKED_FIELDS));
        return node.toString();
    }

    /**
     * template_json lives in a varchar column with no DB-level JSON validation, so read-side
     * parsing is defensive: malformed data logs a warning and falls back to the default
     * configuration instead of 500-ing the print flow.
     */
    private List<String> parseVisibleFields(String templateJson) {
        if (templateJson == null || templateJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(templateJson);
            JsonNode visible = node.get("visibleFields");
            List<String> result = new ArrayList<>();
            if (visible != null && visible.isArray()) {
                for (JsonNode entry : visible) {
                    String value = entry.asText(null);
                    // Keep only recognized fields so a corrupted row can never leak bad keys into the renderer.
                    if (value != null && OPTIONAL_FIELDS.contains(value) && !result.contains(value)) {
                        result.add(value);
                    }
                }
            }
            return List.copyOf(result);
        } catch (Exception e) {
            log.warn("Malformed id_templates.template_json encountered; falling back to default config: {}",
                    e.getMessage());
            return List.of();
        }
    }

    /** A missing event answers 403 like someone else's event, so the route cannot be used to probe event ids. */
    private void requireOwnedEvent(UUID eventId, UUID organizerUserId) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.getOrganizerUserId() == null || !event.getOrganizerUserId().equals(organizerUserId)) {
            throw new ForbiddenException("Organizer does not have access to this event");
        }
    }
}
