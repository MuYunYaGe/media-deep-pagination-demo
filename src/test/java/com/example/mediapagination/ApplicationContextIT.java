package com.example.mediapagination;

import com.example.mediapagination.support.ContainerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextIT extends ContainerIntegrationTest {

    @Test
    void contextLoads() {
    }
}
