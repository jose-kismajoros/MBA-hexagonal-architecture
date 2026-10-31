package br.com.fullcycle.infrastructure.graphql;

import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
@SpringBootTest
class EventResolverTest {

    @Autowired
    private HttpGraphQlTester graphQlTester;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    private Partner disney;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        partnerRepository.deleteAll();

        disney = partnerRepository.create(Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com"));
    }

    @Test
    @DisplayName("Deve cancelar um evento via GraphQL")
    public void testCancelEvent() {
        // given
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, disney));
        final var expectedId = anEvent.eventId().value();

        // when
        graphQlTester.document("""
                        mutation CancelEvent($id: ID!) {
                            cancelEvent(id: $id) { id status }
                        }
                        """)
                .variable("id", expectedId)
                .execute()
                .path("cancelEvent.id").entity(String.class).isEqualTo(expectedId)
                .path("cancelEvent.status").entity(String.class).isEqualTo("CANCELLED");

        // then
        final var actualEvent = eventRepository.eventOfId(anEvent.eventId()).get();
        Assertions.assertEquals(EventStatus.CANCELLED, actualEvent.status());
    }

    @Test
    @DisplayName("Deve retornar erro ao cancelar um evento já cancelado via GraphQL")
    public void testCancelEventTwice() {
        // given
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 100, disney);
        anEvent.cancel();
        eventRepository.create(anEvent);

        // when / then
        graphQlTester.document("""
                        mutation CancelEvent($id: ID!) {
                            cancelEvent(id: $id) { id status }
                        }
                        """)
                .variable("id", anEvent.eventId().value())
                .execute()
                .errors()
                .satisfy(errors -> Assertions.assertFalse(errors.isEmpty()));

        final var actualEvent = eventRepository.eventOfId(anEvent.eventId()).get();
        Assertions.assertEquals(EventStatus.CANCELLED, actualEvent.status());
    }

    @Test
    @DisplayName("Deve obter um evento por id via GraphQL com o status")
    public void testEventOfId() {
        // given
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, disney));
        final var expectedId = anEvent.eventId().value();

        // when / then
        graphQlTester.document("""
                        query EventOfId($id: ID!) {
                            eventOfId(id: $id) { id name date totalSpots status }
                        }
                        """)
                .variable("id", expectedId)
                .execute()
                .path("eventOfId.id").entity(String.class).isEqualTo(expectedId)
                .path("eventOfId.name").entity(String.class).isEqualTo("Disney on Ice")
                .path("eventOfId.date").entity(String.class).isEqualTo("2021-01-01")
                .path("eventOfId.totalSpots").entity(Integer.class).isEqualTo(100)
                .path("eventOfId.status").entity(String.class).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Deve retornar null ao obter um evento inexistente via GraphQL")
    public void testEventOfIdNotFound() {
        graphQlTester.document("""
                        query EventOfId($id: ID!) {
                            eventOfId(id: $id) { id status }
                        }
                        """)
                .variable("id", EventId.unique().value())
                .execute()
                .path("eventOfId").valueIsNull();
    }

    @Test
    @DisplayName("Deve continuar criando eventos via GraphQL (regressão do type Event)")
    public void testCreateEventStillWorks() {
        graphQlTester.document("""
                        mutation CreateEvent($input: EventInput) {
                            createEvent(input: $input) { id name date totalSpots }
                        }
                        """)
                .variable("input", Map.of(
                        "name", "Disney on Ice",
                        "date", "2021-01-01",
                        "totalSpots", 100,
                        "partnerId", disney.partnerId().value()
                ))
                .execute()
                .path("createEvent.id").hasValue()
                .path("createEvent.name").entity(String.class).isEqualTo("Disney on Ice");
    }
}
