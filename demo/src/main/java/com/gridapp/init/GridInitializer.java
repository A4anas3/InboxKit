package com.gridapp.init;

import com.gridapp.service.GridService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Warms up Redis from PostgreSQL on cold start by delegating to GridService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GridInitializer implements ApplicationRunner {

    private final GridService gridService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== GridInitializer: checking if Redis warm-up is needed ===");
        gridService.warmUpFromDatabase();
    }
}
