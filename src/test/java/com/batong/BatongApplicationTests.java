package com.batong;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class BatongApplicationTests {

    @Test
    void contextLoads() {
    }
}
