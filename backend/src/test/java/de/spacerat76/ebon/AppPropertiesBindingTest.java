package de.spacerat76.ebon;

import de.spacerat76.ebon.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "ebon.app-api-token=test-token",
    "ebon.paperless-ebon-tag=test-tag",
    "spring.datasource.url=jdbc:h2:mem:ebon;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false"
})
class AppPropertiesBindingTest {

    @Autowired
    AppProperties props;

    @Test
    void propertiesAreBound() {
        assertEquals("test-token", props.getAppApiToken());
        assertEquals("test-tag", props.getPaperlessEbonTag());
    }
}
