package br.com.fullcycle.application.ticket;

import br.com.fullcycle.application.repository.InMemoryTicketRepository;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelEventTicketsUseCaseTest {

    @Test
    @DisplayName("Deve cancelar todos os tickets de um evento")
    public void testCancelEventTickets() {
        // given
        final var expectedTotalCancelled = 3;

        final var anEventId = EventId.unique();
        final var ticketRepository = new InMemoryTicketRepository();

        for (int i = 0; i < expectedTotalCancelled; i++) {
            ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        }

        final var input = new CancelEventTicketsUseCase.Input(anEventId.value());

        // when
        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var output = useCase.execute(input);

        // then
        Assertions.assertEquals(anEventId.value(), output.eventId());
        Assertions.assertEquals(expectedTotalCancelled, output.totalCancelled());

        final var actualTickets = ticketRepository.ticketsByEventId(anEventId);
        Assertions.assertEquals(expectedTotalCancelled, actualTickets.size());
        Assertions.assertTrue(actualTickets.stream().allMatch(t -> t.status() == TicketStatus.CANCELLED));
    }

    @Test
    @DisplayName("Reprocessar o cancelamento dos tickets deve ser idempotente")
    public void testCancelEventTicketsTwice() {
        // given
        final var expectedTickets = 2;

        final var anEventId = EventId.unique();
        final var ticketRepository = new InMemoryTicketRepository();
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));

        final var input = new CancelEventTicketsUseCase.Input(anEventId.value());
        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        useCase.execute(input);

        // when
        Assertions.assertDoesNotThrow(() -> useCase.execute(input));

        // then
        final var actualTickets = ticketRepository.ticketsByEventId(anEventId);
        Assertions.assertEquals(expectedTickets, actualTickets.size());
        Assertions.assertTrue(actualTickets.stream().allMatch(t -> t.status() == TicketStatus.CANCELLED));
    }

    @Test
    @DisplayName("Não deve cancelar tickets de outros eventos")
    public void testCancelEventTicketsShouldNotTouchOtherEvents() {
        // given
        final var anEventId = EventId.unique();
        final var otherEventId = EventId.unique();

        final var ticketRepository = new InMemoryTicketRepository();
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        final var otherTicket = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), otherEventId));

        final var input = new CancelEventTicketsUseCase.Input(anEventId.value());

        // when
        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        useCase.execute(input);

        // then
        final var actualOtherTicket = ticketRepository.ticketOfId(otherTicket.ticketId()).get();
        Assertions.assertEquals(TicketStatus.PENDING, actualOtherTicket.status());
    }

    @Test
    @DisplayName("Deve concluir sem erro quando o evento não possui tickets")
    public void testCancelEventTicketsWithoutTickets() {
        // given
        final var expectedTotalCancelled = 0;
        final var input = new CancelEventTicketsUseCase.Input(EventId.unique().value());

        // when
        final var useCase = new CancelEventTicketsUseCase(new InMemoryTicketRepository());
        final var output = useCase.execute(input);

        // then
        Assertions.assertEquals(expectedTotalCancelled, output.totalCancelled());
    }
}
