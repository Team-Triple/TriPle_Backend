package org.triple.backend.payment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(PaymentEventProperties.class)
@RequiredArgsConstructor
public class PaymentEventConfig {
    private final PaymentEventProperties paymentEventProperties;

    @Bean
    public ThreadPoolTaskExecutor paymentEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(paymentEventProperties.workerCoreSize());
        executor.setMaxPoolSize(paymentEventProperties.workerMaxSize());
        executor.setQueueCapacity(paymentEventProperties.workerQueueCapacity());
        executor.setThreadNamePrefix("paymentConfirmExecutor-");
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor paymentEventExecutorRetry() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(paymentEventProperties.retryWorkerCoreSize());
        executor.setMaxPoolSize(paymentEventProperties.retryWorkerMaxSize());
        executor.setQueueCapacity(paymentEventProperties.retryWorkerQueueCapacity());
        executor.setThreadNamePrefix("paymentConfirmExecutorRetry-");
        executor.initialize();
        return executor;
    }

}
