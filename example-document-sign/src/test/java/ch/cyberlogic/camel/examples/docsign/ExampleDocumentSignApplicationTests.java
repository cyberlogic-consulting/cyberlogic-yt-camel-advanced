package ch.cyberlogic.camel.examples.docsign;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("junit")
class ExampleDocumentSignApplicationTests {

	@MockBean
	DataSource dataSource;

	@Test
	void contextLoads() {
	}

}
