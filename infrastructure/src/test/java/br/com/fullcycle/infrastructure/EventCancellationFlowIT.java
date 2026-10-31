package br.com.fullcycle.infrastructure;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.customer.Customer;
import br.com.fullcycle.domain.customer.CustomerRepository;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventCancelled;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.infrastructure.gateways.ConsumerQueueGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

// Sem @Transactional: o consumo da fila roda fora da transação do teste e precisa enxergar os dados commitados.
@AutoConfigureMockMvc
class EventCancellationFlowIT extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ConsumerQueueGateway consumerQueueGateway;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Event disneyOnIce;
    private List<Ticket> tickets;
    private Ticket otherEventTicket;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        eventRepository.deleteAll();
        customerRepository.deleteAll();
        partnerRepository.deleteAll();

        final var disney = partnerRepository.create(Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com"));
        final var john = customerRepository.create(Customer.newCustomer("John Doe", "123.456.789-00", "john@gmail.com"));
        final var mary = customerRepository.create(Customer.newCustomer("Mary Doe", "123.456.789-11", "mary@gmail.com"));

        disneyOnIce = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, disney));
        final var otherEvent = eventRepository.create(Event.newEvent("Disney on Fire", "2021-02-01", 100, disney));

        tickets = List.of(
                ticketRepository.create(Ticket.newTicket(john.customerId(), disneyOnIce.eventId())),
                ticketRepository.create(Ticket.newTicket(mary.customerId(), disneyOnIce.eventId()))
        );
        otherEventTicket = ticketRepository.create(Ticket.newTicket(john.customerId(), otherEvent.eventId()));
    }

    @Test
    @DisplayName("Publicar o EventCancelled na fila deve cancelar todos os tickets do evento (e reprocessar é idempotente)")
    public void testEventCancelledPublishedOnQueueCancelsTickets() throws Exception {
        // given
        final var content = mapper.writeValueAsString(new EventCancelled(disneyOnIce.eventId()));

        // when
        consumerQueueGateway.publish(content);

        // then
        awaitUntil(() -> allTicketsCancelled(disneyOnIce.eventId()));
        assertTicketsOfEventCancelled();

        // when (reprocessamento)
        Assertions.assertDoesNotThrow(() -> consumerQueueGateway.publish(content));

        // then
        awaitUntil(() -> allTicketsCancelled(disneyOnIce.eventId()));
        assertTicketsOfEventCancelled();
    }

    @Test
    @DisplayName("Cancelar o evento via REST deve cancelar os tickets via outbox, OutboxRelay e fila")
    public void testCancelEventFlowsThroughOutboxAndRelay() throws Exception {
        // when
        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", disneyOnIce.eventId().value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));

        // then
        awaitUntil(() -> allTicketsCancelled(disneyOnIce.eventId()));
        assertTicketsOfEventCancelled();
    }

    private void assertTicketsOfEventCancelled() {
        final var actualTickets = ticketRepository.ticketsByEventId(disneyOnIce.eventId());
        Assertions.assertEquals(tickets.size(), actualTickets.size());
        Assertions.assertTrue(actualTickets.stream().allMatch(t -> t.status() == TicketStatus.CANCELLED));

        final var actualOtherTicket = ticketRepository.ticketOfId(otherEventTicket.ticketId()).get();
        Assertions.assertEquals(TicketStatus.PENDING, actualOtherTicket.status());
    }

    private boolean allTicketsCancelled(final EventId anEventId) {
        final var actualTickets = ticketRepository.ticketsByEventId(anEventId);
        return !actualTickets.isEmpty() && actualTickets.stream().allMatch(t -> t.status() == TicketStatus.CANCELLED);
    }

    private static void awaitUntil(final Supplier<Boolean> condition) throws InterruptedException {
        final var deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(200);
        }
        Assertions.fail("Condition not met within " + TIMEOUT);
    }
}
