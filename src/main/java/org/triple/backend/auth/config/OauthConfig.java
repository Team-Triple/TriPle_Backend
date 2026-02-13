package org.triple.backend.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.triple.backend.auth.oauth.OauthClient;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.oauth.kakao.KakaoOauthProperties;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(KakaoOauthProperties.class)
public class OauthConfig {

    @Value("${restClient.connectTimeout}")
    private int connectTimeout;

    @Value("${restClient.readTimeout}")
    private int readTimeout;

    @Bean
    public Map<OauthProvider, OauthClient> oauthClients(List<OauthClient> clients) {
        return clients.stream()
                .collect(Collectors.toUnmodifiableMap(OauthClient::provider, c -> c));
    }

    @Bean
    public RestClient restClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeout))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
