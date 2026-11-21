package com.eatclub.deals.security;

import com.eatclub.deals.error.ApiErrorResponse;
import com.eatclub.deals.error.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Turns security failures into the API's usual error body.
 * <ul>
 *   <li>Missing or invalid token: {@code 401} with code {@code unauthorized}.</li>
 *   <li>Valid token without the required scope: {@code 403} with code {@code forbidden}.</li>
 * </ul>
 * Spring's bearer-token handlers set the status and the standard {@code WWW-Authenticate} header first; the body
 * doesn't say why a token was rejected.
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();
    private final JsonMapper jsonMapper;

    public JsonSecurityErrorHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, e);
        writeBody(response, ErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException, ServletException {
        bearerAccessDeniedHandler.handle(request, response, e);
        writeBody(response, ErrorCode.FORBIDDEN);
    }

    private void writeBody(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code, code.defaultMessage(), null));
    }
}
