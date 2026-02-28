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
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.invoice.controller.InvoiceController;
import org.triple.backend.invoice.dto.request.InvoiceAdjustRequestDto;
import org.triple.backend.invoice.dto.request.InvoiceCreateRequestDto;
import org.triple.backend.invoice.dto.request.InvoiceUpdateRequestDto;
import org.triple.backend.invoice.dto.response.InvoiceAdjustResponseDto;
import org.triple.backend.invoice.dto.response.InvoiceCreateResponseDto;
import org.triple.backend.invoice.dto.response.InvoiceUpdateResponseDto;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.exception.InvoiceErrorCode;
import org.triple.backend.invoice.service.InvoiceService;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.travel.exception.UserTravelItineraryErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
                InvoiceStatus.UNCONFIRM
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
                                fieldWithPath("invoiceStatus").description("청구서 상태")
                        )
                ));

        verify(invoiceService, times(1)).updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class));
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 청구서 금액/대상 정보 수정 요청 시 403을 반환한다.")
    void 여행장_LEADER가_아니면_청구서_금액_대상_정보_수정_요청_시_403을_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        given(invoiceService.updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class)))
                .willThrow(new BusinessException(InvoiceErrorCode.NOT_TRAVEL_LEADER));

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
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("여행장만 청구서를 생성할 수 있습니다."))
                .andDo(document("invoices/update-info-fail-not-travel-leader",
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
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class));
    }

    @Test
    @DisplayName("결제 내역이 있는 청구서는 금액/대상 정보 수정 요청 시 409를 반환한다.")
    void 결제_내역이_있는_청구서는_금액_대상_정보_수정_요청_시_409를_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        given(invoiceService.updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class)))
                .willThrow(new BusinessException(InvoiceErrorCode.UPDATE_FORBIDDEN_PAYMENT_EXISTS));

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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("결제 내역이 있는 청구서는 수정할 수 없습니다."))
                .andDo(document("invoices/update-info-fail-payment-exists",
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
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class));
    }

    @Test
    @DisplayName("존재하지 않는 청구서 금액/대상 정보 수정 요청 시 404를 반환한다.")
    void 존재하지_않는_청구서_금액_대상_정보_수정_요청_시_404를_반환한다() throws Exception {
        // given
        Long invoiceId = 999L;
        mockCsrfValid();
        given(invoiceService.updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class)))
                .willThrow(new BusinessException(InvoiceErrorCode.NOT_FOUND_INVOICE));

        // when & then
        mockMvc.perform(put("/invoices/{invoiceId}", invoiceId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateInfoBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 청구서 입니다."))
                .andDo(document("invoices/update-info-fail-not-found-invoice",
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
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class));
    }

    @Test
    @DisplayName("수정 불가능한 상태의 청구서 금액/대상 정보 수정 요청 시 409를 반환한다.")
    void 수정_불가능한_상태의_청구서_금액_대상_정보_수정_요청_시_409를_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        given(invoiceService.updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class)))
                .willThrow(new BusinessException(InvoiceErrorCode.INVOICE_UPDATE_NOT_ALLOWED_STATUS));

        // when & then
        mockMvc.perform(put("/invoices/{invoiceId}", invoiceId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateInfoBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("청구서를 수정할 수 없습니다."))
                .andDo(document("invoices/update-info-fail-not-allowed-status",
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
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class));
    }

    @Test
    @DisplayName("그룹 멤버가 아닌 사용자의 청구서 금액/대상 정보 수정 요청 시 403을 반환한다.")
    void 그룹_멤버가_아닌_사용자의_청구서_금액_대상_정보_수정_요청_시_403을_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        given(invoiceService.updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class)))
                .willThrow(new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER));

        // when & then
        mockMvc.perform(put("/invoices/{invoiceId}", invoiceId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateInfoBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹을 조회할 권한이 없습니다."))
                .andDo(document("invoices/update-info-fail-not-group-member",
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
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class));
    }

    @Test
    @DisplayName("여행 멤버십이 없는 사용자의 청구서 금액/대상 정보 수정 요청 시 404를 반환한다.")
    void 여행_멤버십이_없는_사용자의_청구서_금액_대상_정보_수정_요청_시_404를_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        given(invoiceService.updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class)))
                .willThrow(new BusinessException(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND));

        // when & then
        mockMvc.perform(put("/invoices/{invoiceId}", invoiceId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateInfoBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("여행 내 해당 유저를 찾을 수 없습니다."))
                .andDo(document("invoices/update-info-fail-user-travel-not-found",
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
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class));
    }

    @Test
    @DisplayName("수신자 중복이 있는 청구서 금액/대상 정보 수정 요청 시 409를 반환한다.")
    void 수신자_중복이_있는_청구서_금액_대상_정보_수정_요청_시_409를_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        given(invoiceService.updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class)))
                .willThrow(new BusinessException(InvoiceErrorCode.DUPLICATE_RECIPIENT));

        String duplicatedRecipientBody = """
                {
                  "totalAmount": 20000,
                  "recipients": [
                    { "userId": 2, "amount": 10000 },
                    { "userId": 2, "amount": 10000 }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(put("/invoices/{invoiceId}", invoiceId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicatedRecipientBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("청구 대상에 중복된 사용자가 포함되어 있습니다."))
                .andDo(document("invoices/update-info-fail-duplicate-recipient",
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
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class));
    }

    @Test
    @DisplayName("총 금액 불일치 청구서 금액/대상 정보 수정 요청 시 403을 반환한다.")
    void 총_금액_불일치_청구서_금액_대상_정보_수정_요청_시_403을_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        given(invoiceService.updateInfo(eq(1L), eq(invoiceId), any(InvoiceAdjustRequestDto.class)))
                .willThrow(new BusinessException(InvoiceErrorCode.INVALID_TOTAL_AMOUNT));

        String invalidTotalAmountBody = """
                {
                  "totalAmount": 30000,
                  "recipients": [
                    { "userId": 2, "amount": 10000 },
                    { "userId": 3, "amount": 10000 }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(put("/invoices/{invoiceId}", invoiceId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidTotalAmountBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("총 금액과 대상 금액 합계가 일치하지 않습니다."))
                .andDo(document("invoices/update-info-fail-invalid-total-amount",
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
                                fieldWithPath("message").description("에러 메시지")
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
    @DisplayName("로그인한 여행장(LEADER)은 청구서를 확인(CONFIRM)할 수 있다.")
    void 로그인한_여행장_LEADER은_청구서를_확인할_수_있다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoiceId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("invoices/check",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("확인할 청구서 ID")
                        )
                ));

        verify(invoiceService, times(1)).check(1L, invoiceId);
    }

    @Test
    @DisplayName("비로그인 사용자가 청구서 확인을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_확인을_요청하면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", 1L))
                .andExpect(status().isUnauthorized())
                .andDo(document("invoices/check-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("확인할 청구서 ID")
                        )
                ));

        verify(invoiceService, never()).check(anyLong(), anyLong());
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 청구서 확인 요청 시 403을 반환한다.")
    void 여행장_LEADER가_아니면_청구서_확인_요청_시_403을_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(InvoiceErrorCode.NOT_TRAVEL_LEADER))
                .given(invoiceService).check(eq(1L), eq(invoiceId));

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoiceId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("여행장만 청구서를 생성할 수 있습니다."))
                .andDo(document("invoices/check-fail-not-travel-leader",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).check(eq(1L), eq(invoiceId));
    }

    @Test
    @DisplayName("그룹 멤버가 아니면 청구서 확인 요청 시 403을 반환한다.")
    void 그룹_멤버가_아니면_청구서_확인_요청_시_403을_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER))
                .given(invoiceService).check(eq(1L), eq(invoiceId));

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoiceId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹을 조회할 권한이 없습니다."))
                .andDo(document("invoices/check-fail-not-group-member",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).check(eq(1L), eq(invoiceId));
    }

    @Test
    @DisplayName("여행 멤버십이 없으면 청구서 확인 요청 시 404를 반환한다.")
    void 여행_멤버십이_없으면_청구서_확인_요청_시_404를_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND))
                .given(invoiceService).check(eq(1L), eq(invoiceId));

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoiceId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("여행 내 해당 유저를 찾을 수 없습니다."))
                .andDo(document("invoices/check-fail-user-travel-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).check(eq(1L), eq(invoiceId));
    }

    @Test
    @DisplayName("존재하지 않는 청구서 확인 요청 시 404를 반환한다.")
    void 존재하지_않는_청구서_확인_요청_시_404를_반환한다() throws Exception {
        // given
        Long invoiceId = 999L;
        mockCsrfValid();
        willThrow(new BusinessException(InvoiceErrorCode.NOT_FOUND_INVOICE))
                .given(invoiceService).check(eq(1L), eq(invoiceId));

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoiceId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 청구서 입니다."))
                .andDo(document("invoices/check-fail-not-found-invoice",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).check(eq(1L), eq(invoiceId));
    }

    @Test
    @DisplayName("확인할 수 없는 상태의 청구서 확인 요청 시 409를 반환한다.")
    void 확인할_수_없는_상태의_청구서_확인_요청_시_409를_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(InvoiceErrorCode.INVOICE_CHECK_NOT_ALLOWED_STATUS))
                .given(invoiceService).check(eq(1L), eq(invoiceId));

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoiceId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("청구서를 확인할 수 없습니다."))
                .andDo(document("invoices/check-fail-not-allowed-status",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).check(eq(1L), eq(invoiceId));
    }

    @Test
    @DisplayName("결제 내역이 있는 청구서 확인 요청 시 409를 반환한다.")
    void 결제_내역이_있는_청구서_확인_요청_시_409를_반환한다() throws Exception {
        // given
        Long invoiceId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(InvoiceErrorCode.CHECK_FORBIDDEN_PAYMENT_EXISTS))
                .given(invoiceService).check(eq(1L), eq(invoiceId));

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoiceId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("결제 내역이 있는 청구서는 확인할 수 없습니다."))
                .andDo(document("invoices/check-fail-payment-exists",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(invoiceService, times(1)).check(eq(1L), eq(invoiceId));
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

    private String validUpdateInfoBody() {
        return """
                {
                  "totalAmount": 30000,
                  "recipients": [
                    { "userId": 2, "amount": 10000 },
                    { "userId": 3, "amount": 20000 }
                  ]
                }
                """;
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
