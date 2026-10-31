package br.com.fullcycle.infrastructure.repositories;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.infrastructure.jpa.repositories.OutboxJpaRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

class EventDatabaseRepositoryIT extends IntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    private Partner disney;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        partnerRepository.deleteAll();

        disney = partnerRepository.create(Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com"));
    }

    @Test
    @DisplayName("Deve persistir um evento novo como ACTIVE")
    public void testCreateActiveEvent() {
        // given
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, disney);

        // when
        eventRepository.create(anEvent);

        // then
        final var actualEvent = eventRepository.eventOfId(anEvent.eventId()).get();
        Assertions.assertEquals(EventStatus.ACTIVE, actualEvent.status());
    }

    @Test
    @DisplayName("Deve persistir o cancelamento do evento e gravar o EventCancelled na outbox")
    public void testUpdateCancelledEvent() {
        // given
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, disney));

        final var aPersistedEvent = eventRepository.eventOfId(anEvent.eventId()).get();
        aPersistedEvent.cancel();

        final var expectedDomainEventId = aPersistedEvent.allDomainEvents().iterator().next().domainEventId();

        // when
        eventRepository.update(aPersistedEvent);

        // then
        final var actualEvent = eventRepository.eventOfId(anEvent.eventId()).get();
        Assertions.assertEquals(EventStatus.CANCELLED, actualEvent.status());
        Assertions.assertTrue(actualEvent.allDomainEvents().isEmpty());

        final var actualOutbox = outboxJpaRepository.findById(UUID.fromString(expectedDomainEventId)).get();
        Assertions.assertTrue(actualOutbox.content().contains("event.cancelled"));
        Assertions.assertTrue(actualOutbox.content().contains(anEvent.eventId().value()));
    }
}
