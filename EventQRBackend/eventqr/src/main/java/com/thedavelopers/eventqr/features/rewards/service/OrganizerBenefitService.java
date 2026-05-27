package com.thedavelopers.eventqr.features.rewards.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.rewards.model.dto.EventBenefitRequest;
import com.thedavelopers.eventqr.features.rewards.model.entity.EventBenefit;
import com.thedavelopers.eventqr.features.rewards.repository.EventBenefitRepository;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;

@Service
@Transactional
public class OrganizerBenefitService {

    private final EventBenefitRepository eventBenefitRepository;
    private final TransactionService transactionService;

    public OrganizerBenefitService(EventBenefitRepository eventBenefitRepository, TransactionService transactionService) {
        this.eventBenefitRepository = eventBenefitRepository;
        this.transactionService = transactionService;
    }

    public List<EventBenefit> list(UUID eventId) {
        return eventBenefitRepository.findByEventId(eventId);
    }

    public EventBenefit create(UUID eventId, EventBenefitRequest request) {
        EventBenefit benefit = new EventBenefit();
        benefit.setEventId(eventId);
        benefit.setName(request.name().trim());
        benefit.setDescription(request.description());
        benefit.setActive(request.active());
        return eventBenefitRepository.save(benefit);
    }

    public EventBenefit update(UUID eventId, UUID benefitId, EventBenefitRequest request) {
        EventBenefit benefit = eventBenefitRepository.findByIdAndEventId(benefitId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Benefit not found for event"));
        benefit.setName(request.name().trim());
        benefit.setDescription(request.description());
        benefit.setActive(request.active());
        return eventBenefitRepository.save(benefit);
    }

    public void delete(UUID eventId, UUID benefitId) {
        EventBenefit benefit = eventBenefitRepository.findByIdAndEventId(benefitId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Benefit not found for event"));
        eventBenefitRepository.delete(benefit);
    }

    public List<TransactionResponse> claims(UUID eventId) {
        return transactionService.findByEvent(eventId).stream()
                .filter(transaction -> transaction.transactionType() == TransactionType.BENEFIT_CLAIM)
                .toList();
    }
}