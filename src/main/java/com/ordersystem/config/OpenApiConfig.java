package com.ordersystem.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Order Management System API",
                version = "1.0",
                description = "API REST multi-tenant para gerenciamento de pedidos, produtos e usuários. " +
                              "Autenticação via JWT em cookie httpOnly (oms.token) ou Bearer token.",
                contact = @Contact(name = "OMS", url = "https://github.com/wiltonfilho0825")
        )
)
@SecuritySchemes({
        @SecurityScheme(
                name = "bearerAuth",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer",
                bearerFormat = "JWT",
                description = "Informe o JWT obtido em POST /auth/login"
        ),
        @SecurityScheme(
                name = "cookieAuth",
                type = SecuritySchemeType.APIKEY,
                in = SecuritySchemeIn.COOKIE,
                paramName = "oms.token",
                description = "Cookie httpOnly definido automaticamente no login"
        )
})
public class OpenApiConfig {
}
