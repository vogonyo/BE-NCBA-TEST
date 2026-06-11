package com.backend.ncba.BE_Demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Reference Data Aggregation Service (RDAS) API")
                        .description("""
                                Centralised reference data service exposing REST/JSON APIs for country,
                                currency, language and geographical data. Internally consumes the
                                CountryInfo SOAP service (webservices.oorsprong.org) and serves all
                                channels from a single, cached, paginated API.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LOOP DFS – Digital Business Team")
                                .email("digital-backend@loopdfs.io"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("/").description("Current environment")));
    }
}
