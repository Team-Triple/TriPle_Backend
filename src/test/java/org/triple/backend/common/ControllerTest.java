package org.triple.backend.common;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.triple.backend.auth.jwt.JwtManager;
import org.triple.backend.common.annotation.WebMvcDocsTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.auth.exception.AuthErrorCode;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@WebMvcDocsTest
public class ControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected JwtManager jwtManager;

    @BeforeEach
    void setup(WebApplicationContext webApplicationContext, RestDocumentationContextProvider restDocumentation) {
        given(jwtManager.resolveUserId(any()))
                .willAnswer(invocation -> {
                    String authorizationHeader = invocation.getArgument(0, String.class);
                    if (authorizationHeader == null || authorizationHeader.isBlank()) {
                        return null;
                    }
                    if (!authorizationHeader.startsWith("Bearer ")) {
                        throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
                    }
                    return 1L;
                });

        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
