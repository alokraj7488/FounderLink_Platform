package com.capgemini.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
	"jwt.secret=dummy_secret_for_tests_only_audit_pass",
	"spring.cloud.config.enabled=false",
	"eureka.client.enabled=false"
})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
