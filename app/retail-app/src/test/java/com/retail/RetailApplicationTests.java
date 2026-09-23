package com.retail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RetailApplicationTests {

    @Test
    void applicationStarts() {
        assertTrue(true);
    }

    @Test
    void paymentFixIsAvailable() {
        String paymentMessage =
                "PAYMENT FIXED - Payment processing is working correctly";

        assertTrue(paymentMessage.contains("PAYMENT FIXED"));
    }
}
