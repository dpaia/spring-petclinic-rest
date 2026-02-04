package org.springframework.samples.petclinic.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;

@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes default
public class SessionConfig extends AbstractHttpSessionApplicationInitializer {

    public SessionConfig() {
        super(SessionConfig.class);
    }
}