package org.triple.backend.transfer.unit.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.crypto.UserIdentityResolver;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.transfer.controller.TransferController;
import org.triple.backend.transfer.dto.request.TransferAdjustRequestDto;
import org.triple.backend.transfer.dto.request.TransferCreateRequestDto;
import org.triple.backend.transfer.dto.request.TransferUpdateRequestDto;
import org.triple.backend.transfer.dto.response.TransferAdjustResponseDto;
import org.triple.backend.transfer.dto.response.TransferCreateResponseDto;
import org.triple.backend.transfer.dto.response.TransferDetailResponseDto;
import org.triple.backend.transfer.dto.response.TransferUpdateResponseDto;
import org.triple.backend.transfer.entity.TransferStatus;
import org.triple.backend.transfer.exception.TransferErrorCode;
import org.triple.backend.transfer.mapper.TransferUserIdMapper;
import org.triple.backend.transfer.service.TransferService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
class TransferControllerTest extends ControllerTest {

    @MockitoBean
    private TransferService transferService;

    @MockitoBean
    private UserIdentityResolver userIdentityResolver;

    @MockitoBean
    private TransferUserIdMapper transferUserIdMapper;

    @BeforeEach
    void setUp() {
        when(userIdentityResolver.resolve(any())).thenReturn(1L);
        when(transferUserIdMapper.decryptRecipientUserIds(any(TransferCreateRequestDto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transferUserIdMapper.decryptRecipientUserIds(any(TransferAdjustRequestDto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transferUserIdMapper.encryptRecipientUserIds(any(TransferCreateResponseDto.class)))
                .thenAnswer(invocation -> encryptCreateResponse(invocation.getArgument(0)));
        when(transferUserIdMapper.encryptRecipientUserIds(any(TransferAdjustResponseDto.class)))
                .thenAnswer(invocation -> encryptAdjustResponse(invocation.getArgument(0)));
        when(transferUserIdMapper.encryptUserIds(any(TransferDetailResponseDto.class)))
                .thenAnswer(invocation -> encryptDetailResponse(invocation.getArgument(0)));
    }

    private TransferCreateResponseDto encryptCreateResponse(final TransferCreateResponseDto response) {
        return new TransferCreateResponseDto(
                response.transferId(),
                response.accountNumber(),
                response.bankName(),
                response.accountHolder(),
                response.totalAmount(),
                response.members().stream()
                        .map(member -> new TransferCreateResponseDto.MemberDto(
                                "enc-" + member.id(),
                                member.name(),
                                member.avatar(),
                                member.amount(),
                                member.settled()
                        ))
                        .toList()
        );
    }

    private TransferAdjustResponseDto encryptAdjustResponse(final TransferAdjustResponseDto response) {
        return new TransferAdjustResponseDto(
                response.transferId(),
                response.accountNumber(),
                response.bankName(),
                response.accountHolder(),
                response.totalAmount(),
                response.members().stream()
                        .map(member -> new TransferCreateResponseDto.MemberDto(
                                "enc-" + member.id(),
                                member.name(),
                                member.avatar(),
                                member.amount(),
                                member.settled()
                        ))
                        .toList(),
                response.transferStatus()
        );
    }

    private TransferDetailResponseDto encryptDetailResponse(final TransferDetailResponseDto response) {
        List<TransferDetailResponseDto.MemberDto> encryptedMembers = response.members().stream()
                .map(member -> new TransferDetailResponseDto.MemberDto(
                        "enc-" + member.id(),
                        member.name(),
                        member.avatar(),
                        member.amount(),
                        member.settled()
                ))
                .toList();
        return new TransferDetailResponseDto(
                response.accountNumber(),
                response.bankName(),
                response.accountHolder(),
                response.totalAmount(),
                encryptedMembers,
                response.remainingAmount(),
                response.isDone()
        );
    }

    @Test
    @DisplayName("로그인한 여행장(LEADER)은 정산서를 생성할 수 있다.")
    void 로그인한_여행장_LEADER은_청구서를_생성할_수_있다() throws Exception {
        // given
        String recipient1PublicUuid = "00000000-0000-0000-0000-000000000002";
        String recipient2PublicUuid = "00000000-0000-0000-0000-000000000003";
        TransferCreateResponseDto response = new TransferCreateResponseDto(
                1L,
                "999999-00-999999",
                "KB국민",
                "김민준",
                new BigDecimal("70000"),
                List.of(
                        new TransferCreateResponseDto.MemberDto(recipient1PublicUuid, "멤버1", "http://profile/2", new BigDecimal("30000"), false),
                        new TransferCreateResponseDto.MemberDto(recipient2PublicUuid, "멤버2", "http://profile/3", new BigDecimal("40000"), false)
                )
        );
        given(transferService.create(eq(1L), any(TransferCreateRequestDto.class))).willReturn(response);
        mockCsrfValid();

        String body = """
                {
                  "accountNumber": "999999-00-999999",
                  "bankName": "KB국민",
                  "accountHolder": "김민준",
                  "groupId": 10,
                  "travelItineraryId": 20,
                  "members": [
                    { "id": "2", "name": "멤버1", "avatar": "http://profile/2", "amount": 30000, "settled": false },
                    { "id": "3", "name": "멤버2", "avatar": "http://profile/3", "amount": 40000, "settled": false }
                  ],
                  "totalAmount": 70000,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """;

        // when & then
        mockMvc.perform(post("/transfers")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(1L))
                .andExpect(jsonPath("$.accountNumber").value("999999-00-999999"))
                .andExpect(jsonPath("$.bankName").value("KB국민"))
                .andExpect(jsonPath("$.accountHolder").value("김민준"))
                .andExpect(jsonPath("$.totalAmount").value(70000))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].id").value("enc-" + recipient1PublicUuid))
                .andExpect(jsonPath("$.members[0].amount").value(30000))
                .andDo(document("transfers/create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("accountNumber").description("계좌번호"),
                                fieldWithPath("bankName").description("은행명"),
                                fieldWithPath("accountHolder").description("예금주"),
                                fieldWithPath("groupId").description("그룹 ID"),
                                fieldWithPath("travelItineraryId").description("여행 일정 ID"),
                                fieldWithPath("members").description("정산 멤버 목록"),
                                fieldWithPath("members[].id").description("정산 멤버 사용자 ID"),
                                fieldWithPath("members[].name").description("정산 멤버 이름"),
                                fieldWithPath("members[].avatar").description("정산 멤버 아바타 URL"),
                                fieldWithPath("members[].amount").description("정산 금액"),
                                fieldWithPath("members[].settled").description("정산 완료 여부"),
                                fieldWithPath("totalAmount").description("총 청구 금액"),
                                fieldWithPath("dueAt").description("납부 기한")
                        ),
                        responseFields(
                                fieldWithPath("transferId").description("생성된 청구서 ID"),
                                fieldWithPath("accountNumber").description("계좌번호"),
                                fieldWithPath("bankName").description("은행명"),
                                fieldWithPath("accountHolder").description("예금주"),
                                fieldWithPath("totalAmount").description("총 청구 금액"),
                                fieldWithPath("members").description("정산 멤버 목록"),
                                fieldWithPath("members[].id").description("정산 멤버 사용자 ID"),
                                fieldWithPath("members[].name").description("정산 멤버 이름"),
                                fieldWithPath("members[].avatar").description("정산 멤버 아바타 URL"),
                                fieldWithPath("members[].amount").description("정산 금액"),
                                fieldWithPath("members[].settled").description("정산 완료 여부")
                        )
                ));

        verify(transferService, times(1)).create(eq(1L), any(TransferCreateRequestDto.class));
    }

    @Test
    @DisplayName("비로그인 사용자가 정산서 생성을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_생성을_요청하면_401을_반환한다() throws Exception {
        String body = validCreateBody();

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("transfers/create-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(createRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, never()).create(any(), any());
    }

    @Test
    @DisplayName("정산서 생성 요청이 유효하지 않으면 400을 반환한다.")
    void 청구서_생성_요청이_유효하지_않으면_400을_반환한다() throws Exception {
        // given
        mockCsrfValid();
        String invalidBody = invalidCreateBody();

        // when & then
        mockMvc.perform(post("/transfers")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andDo(document("transfers/create-fail-bad-request",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(createRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, never()).create(any(), any());
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 정산서 생성 요청 시 403을 반환한다.")
    void 여행장_LEADER가_아니면_청구서_생성_요청_시_403을_반환한다() throws Exception {
        mockCsrfValid();
        given(transferService.create(eq(1L), any(TransferCreateRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.NOT_TRAVEL_LEADER));

        String body = validCreateBody();

        mockMvc.perform(post("/transfers")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("여행장 권한이 필요합니다."))
                .andDo(document("transfers/create-fail-not-travel-leader",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(createRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("총 금액 불일치 정산서 생성 요청 시 403을 반환한다.")
    void 총_금액_불일치_청구서_생성_요청_시_403을_반환한다() throws Exception {
        mockCsrfValid();
        given(transferService.create(eq(1L), any(TransferCreateRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.INVALID_TOTAL_AMOUNT));

        String body = """
                {
                  "accountNumber": "999999-00-999999",
                  "bankName": "KB국민",
                  "accountHolder": "김민준",
                  "groupId": 10,
                  "travelItineraryId": 20,
                  "members": [
                    { "id": "2", "name": "멤버1", "avatar": "http://profile/2", "amount": 10000, "settled": false },
                    { "id": "3", "name": "멤버2", "avatar": "http://profile/3", "amount": 10000, "settled": false }
                  ],
                  "totalAmount": 30000,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """;

        mockMvc.perform(post("/transfers")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("총 금액과 대상 금액 합계가 일치하지 않습니다."))
                .andDo(document("transfers/create-fail-invalid-total-amount",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(createRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("정산 완료 멤버 금액이 양수인 정산서 생성 요청 시 400을 반환한다.")
    void 정산_완료_멤버_금액이_양수인_청구서_생성_요청_시_400을_반환한다() throws Exception {
        mockCsrfValid();
        given(transferService.create(eq(1L), any(TransferCreateRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.INVALID_SETTLED_AMOUNT));

        mockMvc.perform(post("/transfers")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("정산 완료 멤버의 금액은 0이어야 합니다."))
                .andDo(document("transfers/create-fail-invalid-settled-amount",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(createRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("로그인한 여행장(LEADER)은 정산서 메타 정보를 수정할 수 있다.")
    void 로그인한_여행장_LEADER은_청구서_메타_정보를_수정할_수_있다() throws Exception {
        // given
        Long transferId = 1L;
        TransferUpdateResponseDto response = new TransferUpdateResponseDto(
                transferId,
                new BigDecimal("70000"),
                LocalDateTime.of(2030, 4, 1, 18, 0),
                TransferStatus.UNCONFIRM,
                LocalDateTime.of(2030, 3, 20, 12, 0)
        );
        given(transferService.updateMetaInfo(eq(1L), eq(transferId), any(TransferUpdateRequestDto.class))).willReturn(response);
        mockCsrfValid();

        String body = """
                {
                  "dueAt": "2030-04-01T18:00:00"
                }
                """;

        // when & then
        mockMvc.perform(patch("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(transferId))
                .andExpect(jsonPath("$.totalAmount").value(70000))
                .andExpect(jsonPath("$.dueAt").value("2030-04-01T18:00:00"))
                .andExpect(jsonPath("$.transferStatus").value("UNCONFIRM"))
                .andExpect(jsonPath("$.updatedAt").value("2030-03-20T12:00:00"))
                .andDo(document("transfers/update-meta",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("dueAt").description("납부 기한")
                        ),
                        responseFields(
                                fieldWithPath("transferId").description("청구서 ID"),
                                fieldWithPath("totalAmount").description("총 청구 금액"),
                                fieldWithPath("dueAt").description("납부 기한"),
                                fieldWithPath("transferStatus").description("청구서 상태"),
                                fieldWithPath("updatedAt").description("수정 일시")
                        )
                ));

        verify(transferService, times(1)).updateMetaInfo(eq(1L), eq(transferId), any(TransferUpdateRequestDto.class));
    }

    @Test
    @DisplayName("비로그인 사용자가 정산서 메타 정보 수정을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_메타_정보_수정을_요청하면_401을_반환한다() throws Exception {
        String body = """
                {
                  "dueAt": "2030-04-01T18:00:00"
                }
                """;

        mockMvc.perform(patch("/transfers/{transferId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("transfers/update-meta-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("dueAt").description("납부 기한")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, never()).updateMetaInfo(anyLong(), anyLong(), any(TransferUpdateRequestDto.class));
    }

    @Test
    @DisplayName("정산서 메타 정보 수정 요청이 유효하지 않으면 400을 반환한다.")
    void 청구서_메타_정보_수정_요청이_유효하지_않으면_400을_반환한다() throws Exception {
        // given
        mockCsrfValid();
        String invalidBody = """
                {
                  "dueAt": null
                }
                """;

        // when & then
        mockMvc.perform(patch("/transfers/{transferId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andDo(document("transfers/update-meta-fail-bad-request",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("dueAt").description("납부 기한")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, never()).updateMetaInfo(anyLong(), anyLong(), any(TransferUpdateRequestDto.class));
    }

    @Test
    @DisplayName("존재하지 않는 정산서 메타 정보 수정 요청 시 404를 반환한다.")
    void 존재하지_않는_청구서_메타_정보_수정_요청_시_404를_반환한다() throws Exception {
        Long transferId = 999L;
        mockCsrfValid();
        given(transferService.updateMetaInfo(eq(1L), eq(transferId), any(TransferUpdateRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE));

        String body = """
                {
                  "dueAt": "2030-04-01T18:00:00"
                }
                """;

        mockMvc.perform(patch("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 청구서 입니다."))
                .andDo(document("transfers/update-meta-fail-not-found-transfer",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("dueAt").description("납부 기한")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 정산서 메타 정보 수정 요청 시 403을 반환한다.")
    void 여행장_LEADER가_아니면_청구서_메타_정보_수정_요청_시_403을_반환한다() throws Exception {
        Long transferId = 1L;
        mockCsrfValid();
        given(transferService.updateMetaInfo(eq(1L), eq(transferId), any(TransferUpdateRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.NOT_TRAVEL_LEADER));

        String body = """
                {
                  "dueAt": "2030-04-01T18:00:00"
                }
                """;

        mockMvc.perform(patch("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("여행장 권한이 필요합니다."))
                .andDo(document("transfers/update-meta-fail-not-travel-leader",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("dueAt").description("납부 기한")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("수정 불가능한 상태의 정산서 메타 정보 수정 요청 시 409를 반환한다.")
    void 수정_불가능한_상태의_청구서_메타_정보_수정_요청_시_409를_반환한다() throws Exception {
        Long transferId = 1L;
        mockCsrfValid();
        given(transferService.updateMetaInfo(eq(1L), eq(transferId), any(TransferUpdateRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.INVOICE_UPDATE_NOT_ALLOWED_STATUS));

        String body = """
                {
                  "dueAt": "2030-04-01T18:00:00"
                }
                """;

        mockMvc.perform(patch("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("청구서를 수정할 수 없습니다."))
                .andDo(document("transfers/update-meta-fail-not-allowed-status",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("dueAt").description("납부 기한")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("여행 멤버는 정산서를 조회할 수 있다.")
    void 여행_멤버는_청구서를_조회할_수_있다() throws Exception {
        // given
        String creatorPublicUuid = "00000000-0000-0000-0000-000000000001";
        String member1PublicUuid = "00000000-0000-0000-0000-000000000002";
        String member2PublicUuid = "00000000-0000-0000-0000-000000000003";
        TransferDetailResponseDto response = new TransferDetailResponseDto(
                "999999-00-999999",
                "KB국민",
                "김민준",
                new BigDecimal("70000"),
                List.of(
                        new TransferDetailResponseDto.MemberDto(creatorPublicUuid, "생성자", "http://profile/1", new BigDecimal("70000"), false),
                        new TransferDetailResponseDto.MemberDto(member1PublicUuid, "멤버1", "http://profile/2", BigDecimal.ZERO, true),
                        new TransferDetailResponseDto.MemberDto(member2PublicUuid, "멤버2", "http://profile/3", BigDecimal.ZERO, true)
                ),
                BigDecimal.ZERO,
                true
        );
        given(transferService.searchTransfer(eq(1L), eq(20L))).willReturn(response);
        mockCsrfValid();

        // when & then
        mockMvc.perform(get("/transfers/travels/{travelItineraryId}", 20L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("999999-00-999999"))
                .andExpect(jsonPath("$.bankName").value("KB국민"))
                .andExpect(jsonPath("$.accountHolder").value("김민준"))
                .andExpect(jsonPath("$.totalAmount").value(70000))
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.members[0].id").value("enc-" + creatorPublicUuid))
                .andExpect(jsonPath("$.remainingAmount").value(0))
                .andExpect(jsonPath("$.isDone").value(true))
                .andDo(document("transfers/search",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("travelItineraryId").description("조회할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("accountNumber").description("계좌번호"),
                                fieldWithPath("bankName").description("은행명"),
                                fieldWithPath("accountHolder").description("예금주"),
                                fieldWithPath("totalAmount").description("총 청구 금액"),
                                fieldWithPath("members").description("정산 멤버 목록"),
                                fieldWithPath("members[].id").description("정산 멤버 사용자 ID"),
                                fieldWithPath("members[].name").description("정산 멤버 이름"),
                                fieldWithPath("members[].avatar").description("정산 멤버 아바타 URL").optional(),
                                fieldWithPath("members[].amount").description("정산 금액"),
                                fieldWithPath("members[].settled").description("정산 완료 여부"),
                                fieldWithPath("remainingAmount").description("전체 남은 청구 금액"),
                                fieldWithPath("isDone").description("정산 완료 여부")
                        )
                ));

        verify(transferService, times(1)).searchTransfer(1L, 20L);
    }

    @Test
    @DisplayName("비로그인 사용자가 정산서 조회를 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_조회를_요청하면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/transfers/travels/{travelItineraryId}", 20L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("transfers/search-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("travelItineraryId").description("조회할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, never()).searchTransfer(anyLong(), anyLong());
    }

    @Test
    @DisplayName("여행 멤버십이 없으면 정산서 조회 요청 시 404를 반환한다.")
    void 여행_멤버십이_없으면_청구서_조회_요청_시_404를_반환한다() throws Exception {
        given(transferService.searchTransfer(eq(1L), eq(20L)))
                .willThrow(new BusinessException(TransferErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND));
        mockCsrfValid();

        mockMvc.perform(get("/transfers/travels/{travelItineraryId}", 20L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("해당 여행의 참여 멤버가 아닙니다."))
                .andDo(document("transfers/search-fail-user-travel-itinerary-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("travelItineraryId").description("조회할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("존재하지 않는 정산서 조회 요청 시 404를 반환한다.")
    void 존재하지_않는_청구서_조회_요청_시_404를_반환한다() throws Exception {
        given(transferService.searchTransfer(eq(1L), eq(20L)))
                .willThrow(new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE));
        mockCsrfValid();

        mockMvc.perform(get("/transfers/travels/{travelItineraryId}", 20L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 청구서 입니다."))
                .andDo(document("transfers/search-fail-not-found-transfer",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("travelItineraryId").description("조회할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("로그인한 여행장(LEADER)은 정산서 금액/대상 정보를 수정할 수 있다.")
    void 로그인한_여행장_LEADER은_청구서_금액_대상_정보를_수정할_수_있다() throws Exception {
        // given
        Long transferId = 1L;
        String recipient1PublicUuid = "00000000-0000-0000-0000-000000000002";
        String recipient2PublicUuid = "00000000-0000-0000-0000-000000000003";
        TransferAdjustResponseDto response = new TransferAdjustResponseDto(
                transferId,
                "999999-00-999999",
                "KB국민",
                "김민준",
                new BigDecimal("30000"),
                List.of(
                        new TransferCreateResponseDto.MemberDto(recipient1PublicUuid, "멤버1", "http://profile/2", new BigDecimal("10000"), false),
                        new TransferCreateResponseDto.MemberDto(recipient2PublicUuid, "멤버2", "http://profile/3", new BigDecimal("20000"), false)
                ),
                TransferStatus.UNCONFIRM
        );
        given(transferService.updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class))).willReturn(response);
        mockCsrfValid();

        String body = """
                {
                  "accountNumber": "999999-00-999999",
                  "bankName": "KB국민",
                  "accountHolder": "김민준",
                  "totalAmount": 30000,
                  "members": [
                    { "id": "2", "name": "멤버1", "avatar": "http://profile/2", "amount": 10000, "settled": false },
                    { "id": "3", "name": "멤버2", "avatar": "http://profile/3", "amount": 20000, "settled": false }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(transferId))
                .andExpect(jsonPath("$.accountNumber").value("999999-00-999999"))
                .andExpect(jsonPath("$.bankName").value("KB국민"))
                .andExpect(jsonPath("$.accountHolder").value("김민준"))
                .andExpect(jsonPath("$.totalAmount").value(30000))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].id").value("enc-" + recipient1PublicUuid))
                .andExpect(jsonPath("$.members[0].amount").value(10000))
                .andExpect(jsonPath("$.transferStatus").value("UNCONFIRM"))
                .andDo(document("transfers/update-info",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("accountNumber").description("계좌번호"),
                                fieldWithPath("bankName").description("은행명"),
                                fieldWithPath("accountHolder").description("예금주"),
                                fieldWithPath("totalAmount").description("변경할 총 청구 금액"),
                                fieldWithPath("members").description("변경할 정산 멤버 목록(전체 교체)"),
                                fieldWithPath("members[].id").description("정산 멤버 사용자 ID"),
                                fieldWithPath("members[].name").description("정산 멤버 이름"),
                                fieldWithPath("members[].avatar").description("정산 멤버 아바타 URL"),
                                fieldWithPath("members[].amount").description("정산 멤버 금액"),
                                fieldWithPath("members[].settled").description("정산 완료 여부")
                        ),
                        responseFields(
                                fieldWithPath("transferId").description("청구서 ID"),
                                fieldWithPath("accountNumber").description("계좌번호"),
                                fieldWithPath("bankName").description("은행명"),
                                fieldWithPath("accountHolder").description("예금주"),
                                fieldWithPath("totalAmount").description("변경된 총 청구 금액"),
                                fieldWithPath("members").description("최종 정산 멤버 목록"),
                                fieldWithPath("members[].id").description("정산 멤버 사용자 ID"),
                                fieldWithPath("members[].name").description("정산 멤버 이름"),
                                fieldWithPath("members[].avatar").description("정산 멤버 아바타 URL"),
                                fieldWithPath("members[].amount").description("정산 멤버 금액"),
                                fieldWithPath("members[].settled").description("정산 완료 여부"),
                                fieldWithPath("transferStatus").description("청구서 상태")
                        )
                ));

        verify(transferService, times(1)).updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class));
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 정산서 금액/대상 정보 수정 요청 시 403을 반환한다.")
    void 여행장_LEADER가_아니면_청구서_금액_대상_정보_수정_요청_시_403을_반환한다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();
        given(transferService.updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.NOT_TRAVEL_LEADER));

        String body = """
                {
                  "accountNumber": "999999-00-999999",
                  "bankName": "KB국민",
                  "accountHolder": "김민준",
                  "totalAmount": 30000,
                  "members": [
                    { "id": "2", "name": "멤버1", "avatar": "http://profile/2", "amount": 10000, "settled": false },
                    { "id": "3", "name": "멤버2", "avatar": "http://profile/3", "amount": 20000, "settled": false }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("여행장 권한이 필요합니다."))
                .andDo(document("transfers/update-info-fail-not-travel-leader",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(updateInfoRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class));
    }

    @Test
    @DisplayName("존재하지 않는 정산서 금액/대상 정보 수정 요청 시 404를 반환한다.")
    void 존재하지_않는_청구서_금액_대상_정보_수정_요청_시_404를_반환한다() throws Exception {
        // given
        Long transferId = 999L;
        mockCsrfValid();
        given(transferService.updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE));

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateInfoBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 청구서 입니다."))
                .andDo(document("transfers/update-info-fail-not-found-transfer",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(updateInfoRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class));
    }

    @Test
    @DisplayName("수정 불가능한 상태의 정산서 금액/대상 정보 수정 요청 시 409를 반환한다.")
    void 수정_불가능한_상태의_청구서_금액_대상_정보_수정_요청_시_409를_반환한다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();
        given(transferService.updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.INVOICE_UPDATE_NOT_ALLOWED_STATUS));

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateInfoBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("청구서를 수정할 수 없습니다."))
                .andDo(document("transfers/update-info-fail-not-allowed-status",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(updateInfoRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class));
    }

    @Test
    @DisplayName("그룹 멤버가 아닌 사용자의 정산서 금액/대상 정보 수정 요청 시 403을 반환한다.")
    void 그룹_멤버가_아닌_사용자의_청구서_금액_대상_정보_수정_요청_시_403을_반환한다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();
        given(transferService.updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class)))
                .willThrow(new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER));

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateInfoBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹을 조회할 권한이 없습니다."))
                .andDo(document("transfers/update-info-fail-not-group-member",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(updateInfoRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class));
    }

    @Test
    @DisplayName("여행 멤버십이 없는 사용자의 정산서 금액/대상 정보 수정 요청 시 404를 반환한다.")
    void 여행_멤버십이_없는_사용자의_청구서_금액_대상_정보_수정_요청_시_404를_반환한다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();
        given(transferService.updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class)))
                .willThrow(new BusinessException(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND));

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateInfoBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("여행 내 해당 유저를 찾을 수 없습니다."))
                .andDo(document("transfers/update-info-fail-user-travel-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(updateInfoRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class));
    }

    @Test
    @DisplayName("수신자 중복이 있는 정산서 금액/대상 정보 수정 요청 시 409를 반환한다.")
    void 수신자_중복이_있는_청구서_금액_대상_정보_수정_요청_시_409를_반환한다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();
        given(transferService.updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.DUPLICATE_RECIPIENT));

        String duplicatedRecipientBody = """
                {
                  "accountNumber": "999999-00-999999",
                  "bankName": "KB국민",
                  "accountHolder": "김민준",
                  "totalAmount": 20000,
                  "members": [
                    { "id": "2", "name": "멤버1", "avatar": "http://profile/2", "amount": 10000, "settled": false },
                    { "id": "2", "name": "멤버1", "avatar": "http://profile/2", "amount": 10000, "settled": false }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicatedRecipientBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("청구 대상에 중복된 사용자가 포함되어 있습니다."))
                .andDo(document("transfers/update-info-fail-duplicate-recipient",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(updateInfoRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class));
    }

    @Test
    @DisplayName("총 금액 불일치 정산서 금액/대상 정보 수정 요청 시 403을 반환한다.")
    void 총_금액_불일치_청구서_금액_대상_정보_수정_요청_시_403을_반환한다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();
        given(transferService.updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.INVALID_TOTAL_AMOUNT));

        String invalidTotalAmountBody = """
                {
                  "accountNumber": "999999-00-999999",
                  "bankName": "KB국민",
                  "accountHolder": "김민준",
                  "totalAmount": 30000,
                  "members": [
                    { "id": "2", "name": "멤버1", "avatar": "http://profile/2", "amount": 10000, "settled": false },
                    { "id": "3", "name": "멤버2", "avatar": "http://profile/3", "amount": 10000, "settled": false }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidTotalAmountBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("총 금액과 대상 금액 합계가 일치하지 않습니다."))
                .andDo(document("transfers/update-info-fail-invalid-total-amount",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(updateInfoRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class));
    }

    @Test
    @DisplayName("정산 완료 멤버 금액이 양수인 정산서 금액/대상 정보 수정 요청 시 400을 반환한다.")
    void 정산_완료_멤버_금액이_양수인_청구서_금액_대상_정보_수정_요청_시_400을_반환한다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();
        given(transferService.updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class)))
                .willThrow(new BusinessException(TransferErrorCode.INVALID_SETTLED_AMOUNT));

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateInfoBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("정산 완료 멤버의 금액은 0이어야 합니다."))
                .andDo(document("transfers/update-info-fail-invalid-settled-amount",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(updateInfoRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).updateInfo(eq(1L), eq(transferId), any(TransferAdjustRequestDto.class));
    }

    @Test
    @DisplayName("정산서 금액/대상 정보 수정 요청이 유효하지 않으면 400을 반환한다.")
    void 청구서_금액_대상_정보_수정_요청이_유효하지_않으면_400을_반환한다() throws Exception {
        // given
        mockCsrfValid();
        String invalidBody = """
                {
                  "accountNumber": "",
                  "bankName": "",
                  "accountHolder": "",
                  "totalAmount": 0,
                  "members": [
                    { "id": "2", "name": "멤버1", "avatar": "http://profile/2", "amount": 10000, "settled": false }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andDo(document("transfers/update-info-fail-bad-request",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(updateInfoRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, never()).updateInfo(anyLong(), anyLong(), any(TransferAdjustRequestDto.class));
    }

    @Test
    @DisplayName("비로그인 사용자가 정산서 금액/대상 정보 수정을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_금액_대상_정보_수정을_요청하면_401을_반환한다() throws Exception {
        String body = validUpdateInfoBody();

        mockMvc.perform(put("/transfers/{transferId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("transfers/update-info-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("수정할 청구서 ID")
                        ),
                        requestFields(updateInfoRequestFields()),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, never()).updateInfo(anyLong(), anyLong(), any(TransferAdjustRequestDto.class));
    }

    @Test
    @DisplayName("로그인한 여행장(LEADER)은 정산서를 확인(CONFIRM)할 수 있다.")
    void 로그인한_여행장_LEADER은_청구서를_확인할_수_있다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("transfers/check",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("확인할 청구서 ID")
                        )
                ));

        verify(transferService, times(1)).check(1L, transferId);
    }

    @Test
    @DisplayName("비로그인 사용자가 정산서 확인을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_확인을_요청하면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("transfers/check-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, never()).check(anyLong(), anyLong());
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 정산서 확인 요청 시 403을 반환한다.")
    void 여행장_LEADER가_아니면_청구서_확인_요청_시_403을_반환한다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(TransferErrorCode.NOT_TRAVEL_LEADER))
                .given(transferService).check(eq(1L), eq(transferId));

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("여행장 권한이 필요합니다."))
                .andDo(document("transfers/check-fail-not-travel-leader",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).check(eq(1L), eq(transferId));
    }

    @Test
    @DisplayName("그룹 멤버가 아니면 정산서 확인 요청 시 403을 반환한다.")
    void 그룹_멤버가_아니면_청구서_확인_요청_시_403을_반환한다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER))
                .given(transferService).check(eq(1L), eq(transferId));

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹을 조회할 권한이 없습니다."))
                .andDo(document("transfers/check-fail-not-group-member",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).check(eq(1L), eq(transferId));
    }

    @Test
    @DisplayName("여행 멤버십이 없으면 정산서 확인 요청 시 404를 반환한다.")
    void 여행_멤버십이_없으면_청구서_확인_요청_시_404를_반환한다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND))
                .given(transferService).check(eq(1L), eq(transferId));

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("여행 내 해당 유저를 찾을 수 없습니다."))
                .andDo(document("transfers/check-fail-user-travel-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).check(eq(1L), eq(transferId));
    }

    @Test
    @DisplayName("존재하지 않는 정산서 확인 요청 시 404를 반환한다.")
    void 존재하지_않는_청구서_확인_요청_시_404를_반환한다() throws Exception {
        // given
        Long transferId = 999L;
        mockCsrfValid();
        willThrow(new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE))
                .given(transferService).check(eq(1L), eq(transferId));

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 청구서 입니다."))
                .andDo(document("transfers/check-fail-not-found-transfer",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).check(eq(1L), eq(transferId));
    }

    @Test
    @DisplayName("확인할 수 없는 상태의 정산서 확인 요청 시 409를 반환한다.")
    void 확인할_수_없는_상태의_청구서_확인_요청_시_409를_반환한다() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(TransferErrorCode.INVOICE_CHECK_NOT_ALLOWED_STATUS))
                .given(transferService).check(eq(1L), eq(transferId));

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("청구서를 확인할 수 없습니다."))
                .andDo(document("transfers/check-fail-not-allowed-status",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("확인할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).check(eq(1L), eq(transferId));
    }

    @Test
    @DisplayName("정산서 삭제 요청 시 200을 반환한다.")
    void 청구서_삭제_요청_성공() throws Exception {
        // given
        Long transferId = 1L;
        mockCsrfValid();

        // when & then
        mockMvc.perform(delete("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("transfers/delete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("삭제할 청구서 ID")
                        )
                ));

        verify(transferService, times(1)).delete(1L, transferId);
    }

    @Test
    @DisplayName("비로그인 사용자가 정산서 삭제 요청 시 401을 반환한다.")
    void 비로그인_사용자가_청구서_삭제_요청_시_401을_반환한다() throws Exception {
        mockMvc.perform(delete("/transfers/{transferId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("transfers/delete-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("삭제할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("정산서 생성자가 아닌 사용자의 삭제 요청 시 403을 반환한다.")
    void 청구서_생성자가_아닌_사용자의_삭제_요청_시_403을_반환한다() throws Exception {
        Long transferId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(TransferErrorCode.DELETE_UNAUTHORIZED))
                .given(transferService).delete(eq(1L), eq(transferId));

        mockMvc.perform(delete("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("청구서 생성자만 삭제할 수 있습니다."))
                .andDo(document("transfers/delete-fail-delete-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("삭제할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).delete(1L, transferId);
    }

    @Test
    @DisplayName("존재하지 않는 정산서 삭제 요청 시 404를 반환한다.")
    void 존재하지_않는_청구서_삭제_요청_시_404를_반환한다() throws Exception {
        Long transferId = 999L;
        mockCsrfValid();
        willThrow(new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE))
                .given(transferService).delete(eq(1L), eq(transferId));

        mockMvc.perform(delete("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 청구서 입니다."))
                .andDo(document("transfers/delete-fail-not-found-transfer",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("삭제할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).delete(1L, transferId);
    }

    @Test
    @DisplayName("삭제할 수 없는 상태의 정산서 삭제 요청 시 409를 반환한다.")
    void 삭제할_수_없는_상태의_청구서_삭제_요청_시_409를_반환한다() throws Exception {
        Long transferId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(TransferErrorCode.DELETE_FORBIDDEN_STATUS))
                .given(transferService).delete(eq(1L), eq(transferId));

        mockMvc.perform(delete("/transfers/{transferId}", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("현재 상태에서는 청구서를 삭제할 수 없습니다."))
                .andDo(document("transfers/delete-fail-forbidden-status",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("삭제할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, times(1)).delete(1L, transferId);
    }

    @Test
    @DisplayName("이체 완료 요청 성공 시 200을 반환한다.")
    void 이체_완료_요청_성공() throws Exception {
        Long transferId = 1L;
        mockCsrfValid();

        mockMvc.perform(patch("/transfers/{transferId}/users/me/done", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("transfers/complete-my-transfer",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("이체 완료 처리할 청구서 ID")
                        )
                ));

        verify(transferService, times(1)).completeMyTransfer(1L, transferId);
    }

    @Test
    @DisplayName("비로그인 사용자가 이체 완료 요청 시 401을 반환한다.")
    void 비로그인_사용자가_이체_완료_요청_시_401을_반환한다() throws Exception {
        mockMvc.perform(patch("/transfers/{transferId}/users/me/done", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("transfers/complete-my-transfer-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("이체 완료 처리할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(transferService, never()).completeMyTransfer(anyLong(), anyLong());
    }

    @Test
    @DisplayName("존재하지 않는 청구서에 이체 완료 요청 시 404를 반환한다.")
    void 존재하지_않는_청구서에_이체_완료_요청_시_404를_반환한다() throws Exception {
        Long transferId = 999L;
        mockCsrfValid();
        willThrow(new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE))
                .given(transferService).completeMyTransfer(eq(1L), eq(transferId));

        mockMvc.perform(patch("/transfers/{transferId}/users/me/done", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 청구서 입니다."))
                .andDo(document("transfers/complete-my-transfer-fail-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("이체 완료 처리할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("CONFIRM 상태가 아닌 청구서에 이체 완료 요청 시 409를 반환한다.")
    void CONFIRM_상태가_아닌_청구서에_이체_완료_요청_시_409를_반환한다() throws Exception {
        Long transferId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(TransferErrorCode.INVOICE_DONE_NOT_ALLOWED_STATUS))
                .given(transferService).completeMyTransfer(eq(1L), eq(transferId));

        mockMvc.perform(patch("/transfers/{transferId}/users/me/done", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("확정된 청구서만 완료 처리할 수 있습니다."))
                .andDo(document("transfers/complete-my-transfer-fail-invalid-status",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("이체 완료 처리할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("청구서 멤버가 아닌 사용자의 이체 완료 요청 시 404를 반환한다.")
    void 청구서_멤버가_아닌_사용자의_이체_완료_요청_시_404를_반환한다() throws Exception {
        Long transferId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(TransferErrorCode.TRANSFER_USER_NOT_FOUND))
                .given(transferService).completeMyTransfer(eq(1L), eq(transferId));

        mockMvc.perform(patch("/transfers/{transferId}/users/me/done", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("해당 청구서의 멤버가 아닙니다."))
                .andDo(document("transfers/complete-my-transfer-fail-not-member",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("이체 완료 처리할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("이미 이체 완료된 멤버의 중복 요청 시 409를 반환한다.")
    void 이미_이체_완료된_멤버의_중복_요청_시_409를_반환한다() throws Exception {
        Long transferId = 1L;
        mockCsrfValid();
        willThrow(new BusinessException(TransferErrorCode.ALREADY_TRANSFERRED))
                .given(transferService).completeMyTransfer(eq(1L), eq(transferId));

        mockMvc.perform(patch("/transfers/{transferId}/users/me/done", transferId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 이체 완료된 멤버입니다."))
                .andDo(document("transfers/complete-my-transfer-fail-already-transferred",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("transferId").description("이체 완료 처리할 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));
    }

    private String validUpdateInfoBody() {
        return """
                {
                  "accountNumber": "999999-00-999999",
                  "bankName": "KB국민",
                  "accountHolder": "김민준",
                  "totalAmount": 30000,
                  "members": [
                    { "id": "2", "name": "멤버1", "avatar": "http://profile/2", "amount": 10000, "settled": false },
                    { "id": "3", "name": "멤버2", "avatar": "http://profile/3", "amount": 20000, "settled": false }
                  ]
                }
                """;
    }

    private String validCreateBody() {
        return """
                {
                  "accountNumber": "999999-00-999999",
                  "bankName": "KB국민",
                  "accountHolder": "김민준",
                  "groupId": 10,
                  "travelItineraryId": 20,
                  "members": [ { "id": "2", "name": "멤버1", "avatar": "http://profile/2", "amount": 30000, "settled": false } ],
                  "totalAmount": 30000,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """;
    }

    private String invalidCreateBody() {
        return """
                {
                  "accountNumber": "",
                  "bankName": "",
                  "accountHolder": "",
                  "groupId": 10,
                  "travelItineraryId": 20,
                  "members": [ { "id": "2", "name": "멤버1", "avatar": "http://profile/2", "amount": 30000, "settled": false } ],
                  "totalAmount": 0,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """;
    }

    private FieldDescriptor[] createRequestFields() {
        return new FieldDescriptor[]{
                fieldWithPath("accountNumber").description("계좌번호"),
                fieldWithPath("bankName").description("은행명"),
                fieldWithPath("accountHolder").description("예금주"),
                fieldWithPath("groupId").description("그룹 ID"),
                fieldWithPath("travelItineraryId").description("여행 일정 ID"),
                fieldWithPath("members").description("정산 멤버 목록"),
                fieldWithPath("members[].id").description("정산 멤버 사용자 ID"),
                fieldWithPath("members[].name").description("정산 멤버 이름"),
                fieldWithPath("members[].avatar").description("정산 멤버 아바타 URL"),
                fieldWithPath("members[].amount").description("정산 멤버 금액"),
                fieldWithPath("members[].settled").description("정산 완료 여부"),
                fieldWithPath("totalAmount").description("총 청구 금액"),
                fieldWithPath("dueAt").description("납부 기한")
        };
    }

    private FieldDescriptor[] updateInfoRequestFields() {
        return new FieldDescriptor[]{
                fieldWithPath("accountNumber").description("계좌번호"),
                fieldWithPath("bankName").description("은행명"),
                fieldWithPath("accountHolder").description("예금주"),
                fieldWithPath("totalAmount").description("변경할 총 청구 금액"),
                fieldWithPath("members").description("변경할 정산 멤버 목록(전체 교체)"),
                fieldWithPath("members[].id").description("정산 멤버 사용자 ID"),
                fieldWithPath("members[].name").description("정산 멤버 이름"),
                fieldWithPath("members[].avatar").description("정산 멤버 아바타 URL"),
                fieldWithPath("members[].amount").description("정산 멤버 금액"),
                fieldWithPath("members[].settled").description("정산 완료 여부")
        };
    }

    private void mockCsrfValid() {
        // no-op in JWT-based test auth
    }

    private RequestPostProcessor loginSessionAndCsrf() {
        return request -> {
            request.addHeader("Authorization", "Bearer test-token");
            return request;
        };
    }
}
