package br.com.fullcycle.infrastructure.repositories;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.stream.Collectors;

class TicketDatabaseRepositoryIT extends IntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve buscar apenas os tickets de um evento")
    public void testTicketsByEventId() {
        // given
        final var anEventId = EventId.unique();
        final var otherEventId = EventId.unique();

        final var ticket1 = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        final var ticket2 = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), otherEventId));

        final var expectedIds = Set.of(ticket1.ticketId(), ticket2.ticketId());

        // when
        final var actualTickets = ticketRepository.ticketsByEventId(anEventId);

        // then
        Assertions.assertEquals(expectedIds.size(), actualTickets.size());
        Assertions.assertEquals(expectedIds, actualTickets.stream().map(Ticket::ticketId).collect(Collectors.toSet()));
        Assertions.assertTrue(actualTickets.stream().allMatch(t -> anEventId.equals(t.eventId())));
    }

    @Test
    @DisplayName("Deve retornar vazio quando o evento não possui tickets")
    public void testTicketsByEventIdWithoutTickets() {
        // when
        final var actualTickets = ticketRepository.ticketsByEventId(EventId.unique());

        // then
        Assertions.assertTrue(actualTickets.isEmpty());
    }

    @Test
    @DisplayName("Deve persistir o status CANCELLED de um ticket")
    public void testUpdateCancelledTicket() {
        // given
        final var aTicket = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), EventId.unique()));

        // when
        aTicket.cancel();
        ticketRepository.update(aTicket);

        // then
        final var actualTicket = ticketRepository.ticketOfId(aTicket.ticketId()).get();
        Assertions.assertEquals(TicketStatus.CANCELLED, actualTicket.status());
    }
}
