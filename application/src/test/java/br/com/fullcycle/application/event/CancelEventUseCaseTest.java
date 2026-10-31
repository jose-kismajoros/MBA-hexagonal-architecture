package br.com.fullcycle.application.event;

import br.com.fullcycle.application.repository.InMemoryEventRepository;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventCancelled;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.exceptions.ValidationException;
import br.com.fullcycle.domain.partner.Partner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelEventUseCaseTest {

    @Test
    @DisplayName("Deve cancelar um evento ativo")
    public void testCancelEvent() throws Exception {
        // given
        final var expectedStatus = "CANCELLED";
        final var expectedDomainEvent = "event.cancelled";

        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var expectedId = anEvent.eventId().value();
        final var input = new CancelEventUseCase.Input(expectedId);

        // when
        final var useCase = new CancelEventUseCase(eventRepository);
        final var output = useCase.execute(input);

        // then
        Assertions.assertEquals(expectedId, output.id());
        Assertions.assertEquals(expectedStatus, output.status());

        final var actualEvent = eventRepository.eventOfId(anEvent.eventId()).get();
        Assertions.assertEquals(EventStatus.CANCELLED, actualEvent.status());

        final var actualDomainEvent = actualEvent.allDomainEvents().iterator().next();
        Assertions.assertInstanceOf(EventCancelled.class, actualDomainEvent);
        Assertions.assertEquals(expectedDomainEvent, actualDomainEvent.type());
    }

    @Test
    @DisplayName("Não deve cancelar um evento que não existe")
    public void testCancelEventNotFound() throws Exception {
        // given
        final var expectedError = "Event not found";

        final var input = new CancelEventUseCase.Input(EventId.unique().value());

        // when
        final var useCase = new CancelEventUseCase(new InMemoryEventRepository());
        final var actualException = Assertions.assertThrows(ValidationException.class, () -> useCase.execute(input));

        // then
        Assertions.assertEquals(expectedError, actualException.getMessage());
    }

    @Test
    @DisplayName("Não deve cancelar um evento já cancelado")
    public void testCancelEventAlreadyCancelled() throws Exception {
        // given
        final var expectedError = "Event already cancelled";

        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);
        anEvent.cancel();

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var input = new CancelEventUseCase.Input(anEvent.eventId().value());

        // when
        final var useCase = new CancelEventUseCase(eventRepository);
        final var actualException = Assertions.assertThrows(ValidationException.class, () -> useCase.execute(input));

        // then
        Assertions.assertEquals(expectedError, actualException.getMessage());
    }
}
