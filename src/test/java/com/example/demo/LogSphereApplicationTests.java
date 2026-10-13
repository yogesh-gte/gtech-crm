package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LogSphereApplicationTests {

	@org.springframework.boot.test.mock.mockito.MockBean
	private com.example.demo.service.EmailService emailService;

	@Test
	void contextLoads() {
	}

}
