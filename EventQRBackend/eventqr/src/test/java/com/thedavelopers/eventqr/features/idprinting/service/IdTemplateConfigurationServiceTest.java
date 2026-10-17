package com.thedavelopers.eventqr.features.idprinting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdTemplateConfigResponse;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdTemplate;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdTemplateConfigurationServiceTest {

    @Mock
    private IdTemplateRepository idTemplateRepository;
    @Mock
    private EventRepository eventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IdTemplateConfigurationService service;
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new IdTemplateConfigurationService(idTemplateRepository, eventRepository, objectMapper);
        given(eventRepository.existsById(eventId)).willReturn(true);
        given(idTemplateRepository.save(any(IdTemplate.class))).willAnswer(invocation -> {
            IdTemplate template = invocation.getArgument(0);
            if (template.getId() == null) {
                template.setId(UUID.randomUUID());
            }
            return template;
        });
    }

    @Test
    void getOrCreate_returnsExistingActiveTemplate_withoutLockingOrWriting() {
        IdTemplate existing = template("{\"visibleFields\":[\"ROLE\"],\"lockedFields\":[\"QR_CODE\",\"ATTENDEE_NAME\"]}");
        given(idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId)).willReturn(Optional.of(existing));

        assertThat(service.getOrCreateActiveTemplate(eventId)).isSameAs(existing);

        verify(idTemplateRepository, never()).acquireTemplateLock(anyInt(), anyInt());
        verify(idTemplateRepository, never()).save(any());
    }

    @Test
    void getOrCreate_createsDefaultTemplate_underAdvisoryLock() throws Exception {
        given(idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId)).willReturn(Optional.empty());

        IdTemplate created = service.getOrCreateActiveTemplate(eventId);

        InOrder order = inOrder(idTemplateRepository);
        order.verify(idTemplateRepository).boundLockWaits();
        order.verify(idTemplateRepository).acquireTemplateLock(IdTemplateConfigurationService.TEMPLATE_LOCK_NAMESPACE,
                eventId.hashCode());
        order.verify(idTemplateRepository).findFirstByEventIdAndActiveTrue(eventId);
        order.verify(idTemplateRepository).save(any(IdTemplate.class));

        assertThat(created.getEventId()).isEqualTo(eventId);
        assertThat(created.isActive()).isTrue();
        assertThat(created.getName()).isEqualTo("ID Display Configuration");
        assertThat(created.getCreatedByUserId()).isNull();
        JsonNode json = objectMapper.readTree(created.getTemplateJson());
        assertThat(json.get("visibleFields")).isEmpty();
        assertThat(json.get("lockedFields").get(0).asText()).isEqualTo("QR_CODE");
        assertThat(json.get("lockedFields").get(1).asText()).isEqualTo("ATTENDEE_NAME");
    }

    @Test
    void getOrCreate_concurrentCreatorWon_reusesItsRowAfterLock() {
        IdTemplate winner = template("{\"visibleFields\":[],\"lockedFields\":[\"QR_CODE\",\"ATTENDEE_NAME\"]}");
        given(idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(winner));

        assertThat(service.getOrCreateActiveTemplate(eventId)).isSameAs(winner);
        verify(idTemplateRepository).acquireTemplateLock(anyInt(), anyInt());
        verify(idTemplateRepository, never()).save(any());
    }

    @Test
    void getOrCreate_unknownEvent_isNotFound() {
        UUID unknown = UUID.randomUUID();
        given(idTemplateRepository.findFirstByEventIdAndActiveTrue(unknown)).willReturn(Optional.empty());
        given(eventRepository.existsById(unknown)).willReturn(false);

        assertThatThrownBy(() -> service.getOrCreateActiveTemplate(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(idTemplateRepository, never()).save(any());
    }

    @Test
    void getConfig_withoutTemplate_returnsAndPersistsDefaultConfig() {
        given(idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId)).willReturn(Optional.empty());

        IdTemplateConfigResponse response = service.getConfig(eventId);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.visibleFields()).isEmpty();
        assertThat(response.lockedFields()).containsExactly("QR_CODE", "ATTENDEE_NAME");
        verify(idTemplateRepository).save(any(IdTemplate.class));
    }

    @Test
    void getConfig_existingTemplate_returnsItsVisibleFields() {
        given(idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId)).willReturn(Optional.of(
                template("{\"visibleFields\":[\"ROLE\",\"EVENT_NAME\"],\"lockedFields\":[\"QR_CODE\",\"ATTENDEE_NAME\"]}")));

        assertThat(service.getConfig(eventId).visibleFields()).containsExactly("ROLE", "EVENT_NAME");
    }

    @Test
    void saveConfig_takesTemplateLockBeforeFindOrCreate() {
        UUID organizerId = UUID.randomUUID();
        com.thedavelopers.eventqr.features.events.model.entity.Event event =
                new com.thedavelopers.eventqr.features.events.model.entity.Event();
        event.setOrganizerUserId(organizerId);
        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
        given(idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId)).willReturn(Optional.empty());

        service.saveConfig(eventId, List.of("ROLE"), organizerId);

        InOrder order = inOrder(idTemplateRepository);
        order.verify(idTemplateRepository).acquireTemplateLock(IdTemplateConfigurationService.TEMPLATE_LOCK_NAMESPACE,
                eventId.hashCode());
        order.verify(idTemplateRepository).findFirstByEventIdAndActiveTrue(eventId);
        ArgumentCaptor<IdTemplate> saved = ArgumentCaptor.forClass(IdTemplate.class);
        order.verify(idTemplateRepository).save(saved.capture());
        assertThat(saved.getValue().getCreatedByUserId()).isEqualTo(organizerId);
    }

    @Test
    void saveConfig_unknownEvent_isForbidden_notNotFound() {
        UUID unknown = UUID.randomUUID();
        given(eventRepository.findById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveConfig(unknown, List.of(), UUID.randomUUID()))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ForbiddenException.class);
        verify(idTemplateRepository, never()).save(any());
    }

    private IdTemplate template(String json) {
        IdTemplate template = new IdTemplate();
        template.setId(UUID.randomUUID());
        template.setEventId(eventId);
        template.setName("ID Display Configuration");
        template.setActive(true);
        template.setTemplateJson(json);
        return template;
    }
}
