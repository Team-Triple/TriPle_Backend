package org.triple.backend.invoice.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.invoice.controller.InvoiceController;
import org.triple.backend.invoice.dto.request.InvoiceAdjustRequestDto;
import org.triple.backend.invoice.dto.request.InvoiceCreateRequestDto;
import org.triple.backend.invoice.dto.request.InvoiceUpdateRequestDto;
import org.triple.backend.invoice.dto.response.InvoiceAdjustResponseDto;
import org.triple.backend.invoice.dto.response.InvoiceCreateResponseDto;
import org.triple.backend.invoice.dto.response.InvoiceUpdateResponseDto;
import org.triple.backend.invoice.entity.InvoiceStatus;
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
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN_KEY;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@WebMvcTest(InvoiceController.class)
class InvoiceControllerTest extends ControllerTest {

    @MockitoBean
    private InvoiceService invoiceService;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @Test
    @DisplayName("로그인한 여행장(LEADER)은 청구서를 생성할 수 있다.")
    void 로그인한_여행장_LEADER은_청구서를_생성할_수_있다() throws Exception {
        // given
        InvoiceCreateResponseDto response = new InvoiceCreateResponseDto(
                1L,
                10L,
                20L,
                "제주 렌트비 정산",
                new BigDecimal("70000"),
                LocalDateTime.of(2030, 3, 31, 18, 0),
                List.of(
                        new InvoiceCreateResponseDto.RecipientDto(2L, new BigDecimal("30000")),
                        new InvoiceCreateResponseDto.RecipientDto(3L, new BigDecimal("40000"))
                )
        );
        given(invoiceService.create(eq(1L), any(InvoiceCreateRequestDto.class))).willReturn(response);
        mockCsrfValid();

        String body = """
                {
                  "groupId": 10,
                  "travelItineraryId": 20,
                  "recipients": [
                    { "userId": 2, "amount": 30000 },
                    { "userId": 3, "amount": 40000 }
                  ],
                  "title": "제주 렌트비 정산",
                  "description": "렌트비 N빵",
                  "totalAmount": 70000,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """;

        // when & then
        mockMvc.perform(post("/invoices")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(1L))
                .andExpect(jsonPath("$.groupId").value(10L))
                .andExpect(jsonPath("$.travelItineraryId").value(20L))
                .andExpect(jsonPath("$.title").value("제주 렌트비 정산"))
                .andExpect(jsonPath("$.totalAmount").value(70000))
                .andExpect(jsonPath("$.dueAt").value("2030-03-31T18:00:00"))
                .andExpect(jsonPath("$.recipients.length()").value(2))
                .andExpect(jsonPath("$.recipients[0].userId").value(2L))
                .andExpect(jsonPath("$.recipients[0].amount").value(30000))
                .andDo(document("invoices/create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("groupId").description("그룹 ID"),
                                fieldWithPath("travelItineraryId").description("여행 일정 ID"),
                                fieldWithPath("recipients").description("청구 대상 목록"),
                                fieldWithPath("recipients[].userId").description("청구 대상 사용자 ID"),
                                fieldWithPath("recipients[].amount").description("청구 금액"),
                                fieldWithPath("title").description("청구서 제목"),
                                fieldWithPath("description").description("청구서 설명"),
                                fieldWithPath("totalAmount").description("총 청구 금액"),
                                fieldWithPath("dueAt").description("납부 기한")
                        ),
                        responseFields(
                                fieldWithPath("invoiceId").description("생성된 청구서 ID"),
                                fieldWithPath("groupId").description("그룹 ID"),
                                fieldWithPath("travelItineraryId").description("여행 일정 ID"),
                                fieldWithPath("title").description("청구서 제목"),
                                fieldWithPath("totalAmount").description("총 청구 금액"),
                                fieldWithPath("dueAt").description("납부 기한"),
                                fieldWithPath("recipients").description("청구 대상 목록"),
                                fieldWithPath("recipients[].userId").description("청구 대상 사용자 ID"),
                                fieldWithPath("recipients[].amount").description("청구 금액")
                        )
                ));

        verify(invoiceService, times(1)).create(eq(1L), any(InvoiceCreateRequestDto.class));
    }

    @Test
    @DisplayName("비로그인 사용자가 청구서 생성을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_생성을_요청하면_401을_반환한다() throws Exception {
        String body = """
                {
                  "groupId": 10,
                  "travelItineraryId": 20,
                  "recipients": [ { "userId": 2, "amount": 30000 } ],
                  "title": "제주 렌트비 정산",
                  "description": "렌트비 N빵",
                  "totalAmount": 30000,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """;

        mockMvc.perform(post("/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(invoiceService, never()).create(any(), any());
    }

    @Test
    @DisplayName("청구서 생성 요청이 유효하지 않으면 400을 반환한다.")
    void 청구서_생성_요청이_유효하지_않으면_400을_반환한다() throws Exception {
        // given
        mockCsrfValid();
        String invalidBody = """
                {
                  "groupId": 10,
                  "travelItineraryId": 20,
                  "recipients": [],
                  "title": " ",
                  "description": "렌트비 N빵",
                  "totalAmount": 0,
                  "dueAt": "2020-01-01T00:00:00"
                }
                """;

        // when & then
        mockMvc.perform(post("/invoices")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());

        verify(invoiceService, never()).create(any(), any());
    }

    @Test
    @DisplayName("로그인한 여행장(LEADER)은 청구서 메타 정보를 수정할 수 있다.")
    void 로그인한_여행장_LEADER은_청구서_메타_정보를_수정할_수_있다() throws Exception {
        // given
        Long invoiceId = 1L;
        InvoiceUpdateResponseDto response = new InvoiceUpdateResponseDto(
                invoiceId,
                "수정된 정산 제목",
                "수정된 설명",
                new BigDecimal("70000"),
                LocalDateTime.of(2030, 4, 1, 18, 0),
                InvoiceStatus.UNCONFIRM,
                LocalDateTime.of(2030, 3, 20, 12, 0)
        );
        given(invoiceService.updateMetaInfo(eq(1L), eq(invoiceId), any(InvoiceUpdateRequestDto.class))).willReturn(response);
        mockCsrfValid();

        String body = """
                {
                  "title": "수정된 정산 제목",
                  "description": "수정된 설명",
                  "dueAt": "2030-04-01T18:00:00"
                }
                """;

        // when & then
        mockMvc.perform(patch("/invoices/{invoiceId}", invoiceId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId))
                .andExpect(jsonPath("$.title").value("수정된 정산 제목"))
                .andExpect(jsonPath("$.description").value("수정된 설명"))
                .andExpect(jsonPath("$.totalAmount").value(70000))
                .andExpect(jsonPath("$.dueAt").value("2030-04-01T18:00:00"))
                .andExpect(jsonPath("$.invoiceStatus").value("UNCONFIRM"))
                .andExpect(jsonPath("$.updatedAt").value("2030-03-20T12:00:00"))
                .andDo(document("invoices/update-meta",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("수정할 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("title").description("청구서 제목"),
                                fieldWithPath("description").description("청구서 설명"),
                                fieldWithPath("dueAt").description("납부 기한")
                        ),
                        responseFields(
                                fieldWithPath("invoiceId").description("청구서 ID"),
                                fieldWithPath("title").description("청구서 제목"),
                                fieldWithPath("description").description("청구서 설명"),
                                fieldWithPath("totalAmount").description("총 청구 금액"),
                                fieldWithPath("dueAt").description("납부 기한"),
                                fieldWithPath("invoiceStatus").description("청구서 상태"),
                                fieldWithPath("updatedAt").description("수정 일시")
                        )
                ));

        verify(invoiceService, times(1)).updateMetaInfo(eq(1L), eq(invoiceId), any(InvoiceUpdateRequestDto.class));
    }

    @Test
    @DisplayName("비로그인 사용자가 청구서 메타 정보 수정을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_메타_정보_수정을_요청하면_401을_반환한다() throws Exception {
        String body = """
                {
                  "title": "수정된 정산 제목",
                  "description": "수정된 설명",
                  "dueAt": "2030-04-01T18:00:00"
                }
                """;

        mockMvc.perform(patch("/invoices/{invoiceId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(invoiceService, never()).updateMetaInfo(anyLong(), anyLong(), any(InvoiceUpdateRequestDto.class));
    }

    @Test
    @DisplayName("청구서 메타 정보 수정 요청이 유효하지 않으면 400을 반환한다.")
    void 청구서_메타_정보_수정_요청이_유효하지_않으면_400을_반환한다() throws Exception {
        // given
        mockCsrfValid();
        String invalidBody = """
                {
                  "title": " ",
                  "description": " ",
                  "dueAt": null
                }
                """;

        // when & then
        mockMvc.perform(patch("/invoices/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());

        verify(invoiceService, never()).updateMetaInfo(anyLong(), anyLong(), any(InvoiceUpdateRequestDto.class));
    }

    @Test
    @DisplayName("로그인한 여행장(LEADER)은 청구서 금액/대상 정보를 수정할 수 있다.")
    void 로그인한_여행장_LEADER은_청구서_금액_대상_정보를_수정할_수_있다() throws Exception {
        // given
        Long invoiceId = 1L;
        InvoiceAdjustResponseDto response = new InvoiceAdjustResponseDto(
                invoiceId,
                new BigDecimal("30000"),
                List.of(
                        new org.triple.backend.invoice.dto.RecipientAmountDto(2L, new BigDecimal("10000")),
                        new org.triple.backend.invoice.dto.RecipientAmountDto(3L, new BigDecimal("20000"))
                ),
                InvoiceStatus.UNCONFIRM,
                LocalDateTime.of(2030, 4, 2, 12, 0)
        );
        given(invoiceService.updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class))).willReturn(response);
        mockCsrfValid();

        String body = """
                {
                  "totalAmount": 30000,
                  "recipients": [
                    { "userId": 2, "amount": 10000 },
                    { "userId": 3, "amount": 20000 }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(put("/invoices/{invoiceId}", invoiceId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId))
                .andExpect(jsonPath("$.totalAmount").value(30000))
                .andExpect(jsonPath("$.recipients.length()").value(2))
                .andExpect(jsonPath("$.recipients[0].userId").value(2))
                .andExpect(jsonPath("$.recipients[0].amount").value(10000))
                .andExpect(jsonPath("$.invoiceStatus").value("UNCONFIRM"))
                .andExpect(jsonPath("$.updatedAt").value("2030-04-02T12:00:00"))
                .andDo(document("invoices/update-info",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("수정할 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("totalAmount").description("변경할 총 청구 금액"),
                                fieldWithPath("recipients").description("변경할 청구 대상 목록(전체 교체)"),
                                fieldWithPath("recipients[].userId").description("청구 대상 사용자 ID"),
                                fieldWithPath("recipients[].amount").description("청구 대상 금액")
                        ),
                        responseFields(
                                fieldWithPath("invoiceId").description("청구서 ID"),
                                fieldWithPath("totalAmount").description("변경된 총 청구 금액"),
                                fieldWithPath("recipients").description("최종 청구 대상 목록"),
                                fieldWithPath("recipients[].userId").description("청구 대상 사용자 ID"),
                                fieldWithPath("recipients[].amount").description("청구 대상 금액"),
                                fieldWithPath("invoiceStatus").description("청구서 상태"),
                                fieldWithPath("updatedAt").description("수정 일시")
                        )
                ));

        verify(invoiceService, times(1)).updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class));
    }

    @Test
    @DisplayName("청구서 금액/대상 정보 수정 요청이 유효하지 않으면 400을 반환한다.")
    void 청구서_금액_대상_정보_수정_요청이_유효하지_않으면_400을_반환한다() throws Exception {
        // given
        mockCsrfValid();
        String invalidBody = """
                {
                  "totalAmount": 0,
                  "recipients": []
                }
                """;

        // when & then
        mockMvc.perform(put("/invoices/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());

        verify(invoiceService, never()).updateInfo(anyLong(), anyLong(), any(InvoiceAdjustRequestDto.class));
    }

    @Test
    @DisplayName("비로그인 사용자가 청구서 금액/대상 정보 수정을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_금액_대상_정보_수정을_요청하면_401을_반환한다() throws Exception {
        String body = """
                {
                  "totalAmount": 30000,
                  "recipients": [
                    { "userId": 2, "amount": 10000 }
                  ]
                }
                """;

        mockMvc.perform(put("/invoices/{invoiceId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(invoiceService, never()).updateInfo(anyLong(), anyLong(), any(InvoiceAdjustRequestDto.class));
    }

    @Test
    @DisplayName("청구서 삭제 요청 시 200을 반환한다.")
    void 청구서_삭제_요청_성공() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();

        // when & then
        mockMvc.perform(delete("/invoices/{invoiceId}", invoiceId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("invoices/delete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("삭제할 청구서 ID")
                        )
                ));

        verify(invoiceService, times(1)).delete(1L, invoiceId);
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
