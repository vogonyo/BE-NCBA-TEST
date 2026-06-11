package com.backend.ncba.BE_Demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.backend.ncba.BE_Demo.service.ReferenceDataCache;

/**
 * Verifies the Spring application context loads correctly.
 * The ReferenceDataCache is mocked to avoid requiring a live SOAP endpoint.
 */
@SpringBootTest
class BeDemoApplicationTests {

    @MockitoBean
    private ReferenceDataCache referenceDataCache;

    @Test
    void contextLoads() {
        // Passes if the application context starts without errors
    }
}

