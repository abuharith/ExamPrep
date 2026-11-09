package com.examprep.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String PROCESSING_EXECUTOR = "processingExecutor";

    /**
     * A bounded pool keeps document extraction and Gemini calls away from request
     * threads without allowing unbounded work to pile up.
     */
    @Bean(name = PROCESSING_EXECUTOR)
    public ThreadPoolTaskExecutor processingExecutor(AppProperties properties) {
        AppProperties.Async async = properties.async();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.corePoolSize());
        executor.setMaxPoolSize(Math.max(async.corePoolSize(), async.maxPoolSize()));
        executor.setQueueCapacity(async.queueCapacity());
        executor.setThreadNamePrefix("examprep-processing-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
