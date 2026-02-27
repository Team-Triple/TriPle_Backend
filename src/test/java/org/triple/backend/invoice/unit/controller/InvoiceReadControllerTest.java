package org.triple.backend.invoice.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.invoice.controller.InvoiceController;
import org.triple.backend.invoice.dto.response.InvoiceDetailResponseDto;
import org.triple.backend.invoice.service.InvoiceService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN_KEY;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@WebMvcTest(InvoiceController.class)
class InvoiceReadControllerTest extends ControllerTest {

    @MockitoBean
    private InvoiceService invoiceService;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @Test
    @DisplayName("Travel member can read invoice")
    void readInvoiceByTravelItinerary_success() throws Exception {
        InvoiceDetailResponseDto response = new InvoiceDetailResponseDto(
                "invoice title",
                new BigDecimal("70000"),
                LocalDateTime.of(2030, 3, 31, 18, 0),
                "invoice description",
                new InvoiceDetailResponseDto.UserSummaryDto(1L, "creator", "http://profile/1"),
                List.of(
                        new InvoiceDetailResponseDto.InvoiceMemberDto(2L, "member-1", "http://profile/2", BigDecimal.ZERO),
                        new InvoiceDetailResponseDto.InvoiceMemberDto(3L, "member-2", "http://profile/3", BigDecimal.ZERO)
                ),
                BigDecimal.ZERO,
                true
        );
        given(invoiceService.searchInvoice(eq(1L), eq(20L))).willReturn(response);
        mockCsrfValid();

        mockMvc.perform(get("/invoices/travels/{travelItineraryId}", 20L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("invoice title"))
                .andExpect(jsonPath("$.totalAmount").value(70000))
                .andExpect(jsonPath("$.creator.userId").value(1L))
                .andExpect(jsonPath("$.invoiceMembers.length()").value(2))
                .andExpect(jsonPath("$.remainingAmount").value(0))
                .andExpect(jsonPath("$.isDone").value(true));

        verify(invoiceService, times(1)).searchInvoice(1L, 20L);
    }

    @Test
    @DisplayName("Unauthenticated user gets 401")
    void readInvoiceByTravelItinerary_unauthorized() throws Exception {
        mockMvc.perform(get("/invoices/travels/{travelItineraryId}", 20L))
                .andExpect(status().isUnauthorized());

        verify(invoiceService, never()).searchInvoice(anyLong(), anyLong());
    }

    private void mockCsrfValid() {
        when(csrfTokenManager.isValid(any(HttpServletRequest.class), any(String.class))).thenReturn(true);
    }

    private RequestPostProcessor loginSessionAndCsrf() {
        return request -> {
            request.getSession(true).setAttribute(USER_SESSION_KEY, 1L);
            request.getSession().setAttribute(CSRF_TOKEN_KEY, CSRF_TOKEN);
            request.addHeader(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN);
            return request;
        };
    }
}
