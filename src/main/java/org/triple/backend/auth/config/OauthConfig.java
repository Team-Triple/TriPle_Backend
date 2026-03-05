package org.triple.backend.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.triple.backend.auth.config.property.CookieProperties;
import org.triple.backend.auth.config.property.KakaoOauthProperties;
import org.triple.backend.auth.config.property.RestClientProperties;
import org.triple.backend.auth.oauth.OauthClient;
import org.triple.backend.auth.oauth.OauthProvider;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({KakaoOauthProperties.class, CookieProperties.class, RestClientProperties.class})
public class OauthConfig {

    private final RestClientProperties restClientProperties;

    @Bean
    public Map<OauthProvider, OauthClient> oauthClients(List<OauthClient> clients) {
        return clients.stream()
                .collect(Collectors.toUnmodifiableMap(OauthClient::provider, c -> c));
    }

    @Bean
    public RestClient restClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(restClientProperties.connectTimeout()))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(restClientProperties.readTimeout()));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
