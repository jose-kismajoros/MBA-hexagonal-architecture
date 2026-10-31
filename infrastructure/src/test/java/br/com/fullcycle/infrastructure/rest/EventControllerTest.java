package br.com.fullcycle.infrastructure.rest;

import br.com.fullcycle.domain.customer.Customer;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.customer.CustomerRepository;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.application.event.CreateEventUseCase;
import br.com.fullcycle.application.event.GetEventByIdUseCase;
import br.com.fullcycle.infrastructure.dtos.NewEventDTO;
import br.com.fullcycle.infrastructure.dtos.SubscribeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class EventControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private EventRepository eventRepository;

    private Customer johnDoe;
    private Partner disney;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        customerRepository.deleteAll();
        partnerRepository.deleteAll();

        johnDoe = customerRepository.create(Customer.newCustomer("John Doe", "123.456.789-00", "john@gmail.com"));
        disney = partnerRepository.create(Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com"));
    }

    @Test
    @DisplayName("Deve criar um evento")
    public void testCreate() throws Exception {

        var event = new NewEventDTO("Disney on Ice", "2021-01-01", 100, disney.partnerId().value());

        final var result = this.mvc.perform(
                        MockMvcRequestBuilders.post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(event))
                )
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").isString())
                .andReturn().getResponse().getContentAsByteArray();

        var actualResponse = mapper.readValue(result, NewEventDTO.class);
        Assertions.assertEquals(event.date(), actualResponse.date());
        Assertions.assertEquals(event.totalSpots(), actualResponse.totalSpots());
        Assertions.assertEquals(event.name(), actualResponse.name());
    }

    @Test
    @Transactional
    @DisplayName("Deve comprar um ticket de um evento")
    public void testReserveTicket() throws Exception {

        var event = new NewEventDTO("Disney on Ice", "2021-01-01", 100, disney.partnerId().value());

        final var createResult = this.mvc.perform(
                        MockMvcRequestBuilders.post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(event))
                )
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").isString())
                .andReturn().getResponse().getContentAsByteArray();

        var eventId = mapper.readValue(createResult, CreateEventUseCase.Output.class).id();

        var sub = new SubscribeDTO(johnDoe.customerId().value(), null);

        this.mvc.perform(
                        MockMvcRequestBuilders.post("/events/{id}/subscribe", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(sub))
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        var actualEvent = eventRepository.eventOfId(EventId.with(eventId)).get();
        Assertions.assertEquals(1, actualEvent.allTickets().size());
    }

    @Test
    @DisplayName("Deve cancelar um evento")
    public void testCancel() throws Exception {

        var eventId = createEvent();

        this.mvc.perform(
                        MockMvcRequestBuilders.post("/events/{id}/cancel", eventId)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(eventId))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));

        var actualEvent = eventRepository.eventOfId(EventId.with(eventId)).get();
        Assertions.assertEquals(EventStatus.CANCELLED, actualEvent.status());
    }

    @Test
    @DisplayName("Não deve cancelar um evento já cancelado")
    public void testCancelTwiceShouldFail() throws Exception {

        var eventId = createEvent();

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", eventId))
                .andExpect(MockMvcResultMatchers.status().isOk());

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", eventId))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.content().string("Event already cancelled"));
    }

    @Test
    @DisplayName("Não deve cancelar um evento que não existe")
    public void testCancelNotFoundShouldFail() throws Exception {

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", EventId.unique().value()))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.content().string("Event not found"));
    }

    @Test
    @DisplayName("Não deve comprar um ticket de um evento cancelado")
    public void testSubscribeCancelledEventShouldFail() throws Exception {

        var eventId = createEvent();

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", eventId))
                .andExpect(MockMvcResultMatchers.status().isOk());

        var sub = new SubscribeDTO(johnDoe.customerId().value(), null);

        this.mvc.perform(
                        MockMvcRequestBuilders.post("/events/{id}/subscribe", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(sub))
                )
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.content().string("Event is cancelled"));
    }

    @Test
    @DisplayName("Deve obter um evento por id")
    public void testGet() throws Exception {

        var eventId = createEvent();

        final var result = this.mvc.perform(
                        MockMvcRequestBuilders.get("/events/{id}", eventId)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        var actualResponse = mapper.readValue(result, GetEventByIdUseCase.Output.class);
        Assertions.assertEquals(eventId, actualResponse.id());
        Assertions.assertEquals("Disney on Ice", actualResponse.name());
        Assertions.assertEquals("2021-01-01", actualResponse.date());
        Assertions.assertEquals(100, actualResponse.totalSpots());
        Assertions.assertEquals("ACTIVE", actualResponse.status());
    }

    @Test
    @DisplayName("Deve obter um evento cancelado por id com X-Public")
    public void testGetPublic() throws Exception {

        var eventId = createEvent();

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", eventId))
                .andExpect(MockMvcResultMatchers.status().isOk());

        this.mvc.perform(
                        MockMvcRequestBuilders.get("/events/{id}", eventId)
                                .header("X-Public", "true")
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(eventId))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.date").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalSpots").doesNotExist());
    }

    @Test
    @DisplayName("Deve retornar 404 ao obter um evento inexistente")
    public void testGetNotFound() throws Exception {

        this.mvc.perform(MockMvcRequestBuilders.get("/events/{id}", EventId.unique().value()))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(""));
    }

    @Test
    @DisplayName("Deve retornar 404 ao obter um evento inexistente com X-Public")
    public void testGetPublicNotFound() throws Exception {

        this.mvc.perform(
                        MockMvcRequestBuilders.get("/events/{id}", EventId.unique().value())
                                .header("X-Public", "true")
                )
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(""));
    }

    private String createEvent() throws Exception {
        var event = new NewEventDTO("Disney on Ice", "2021-01-01", 100, disney.partnerId().value());

        final var createResult = this.mvc.perform(
                        MockMvcRequestBuilders.post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(event))
                )
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsByteArray();

        return mapper.readValue(createResult, CreateEventUseCase.Output.class).id();
    }
}