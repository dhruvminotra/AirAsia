package com.airasia.flight.calendar;

import com.airasia.flight.currency.UnsupportedCurrencyException;
import com.airasia.flight.event.SoldOutEventPublisher;
import com.airasia.flight.model.FareCalendarResponse;
import com.airasia.flight.model.FareCalendarData;
import com.airasia.flight.model.SoldOutEvent;
import com.airasia.flight.service.CalendarService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlightCalendarController.class)
class FlightCalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CalendarService calendarService;

    @MockBean
    private SoldOutEventPublisher soldOutEventPublisher;

    @Test
    void returnsCalendarJson() throws Exception {
        FareCalendarResponse response = new FareCalendarResponse("KUL", "SIN", "2026-06", "MYR",
                List.of(new FareCalendarData(LocalDate.of(2026, 6, 1), new BigDecimal("149.50"), true)));
        when(calendarService.getCalendar(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/flights/calendar")
                        .param("origin", "KUL")
                        .param("destination", "SIN")
                        .param("month", "2026-06")
                        .param("currency", "MYR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("MYR"))
                .andExpect(jsonPath("$.days[0].price").value(149.50));
    }

    @Test
    void rejectsInvalidMonthFormat() throws Exception {
        mockMvc.perform(get("/api/v1/flights/calendar")
                        .param("origin", "KUL")
                        .param("destination", "SIN")
                        .param("month", "2026-13"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsUnsupportedCurrencyToBadRequest() throws Exception {
        when(calendarService.getCalendar(any())).thenThrow(new UnsupportedCurrencyException("JPY"));

        mockMvc.perform(get("/api/v1/flights/calendar")
                        .param("origin", "KUL")
                        .param("destination", "SIN")
                        .param("month", "2026-06")
                        .param("currency", "JPY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishesSoldOutEvent() throws Exception {
        String body = """
                {
                  "eventId": "uuid-1",
                  "origin": "KUL",
                  "destination": "SIN",
                  "date": "2026-06-15",
                  "flightNumber": "AK100",
                  "priceClass": "PROMO",
                  "occurredAtEpochMillis": 1717000000000
                }
                """;

        mockMvc.perform(post("/api/v1/flights/sold-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(soldOutEventPublisher).publish(any(SoldOutEvent.class));
    }

    @Test
    void rejectsInvalidSoldOutEvent() throws Exception {
        String body = "{\"origin\":\"KUL\"}"; // missing required fields

        mockMvc.perform(post("/api/v1/flights/sold-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
