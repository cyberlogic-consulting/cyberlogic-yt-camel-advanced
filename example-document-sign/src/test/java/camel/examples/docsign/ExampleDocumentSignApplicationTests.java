package camel.examples.docsign;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("junit")
class ExampleDocumentSignApplicationTests {

	@MockitoBean
	DataSource dataSource;

	@Test
	void contextLoads() {
	}

}
