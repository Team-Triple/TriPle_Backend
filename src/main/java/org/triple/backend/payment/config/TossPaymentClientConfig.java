package org.triple.backend.payment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TossPaymentProperties.class)
public class TossPaymentClientConfig {
    private final TossPaymentProperties tossPaymentProperties;

    @Bean
    public RestClient tossPaymentClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(tossPaymentProperties.timeout()))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(tossPaymentProperties.timeout()));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
