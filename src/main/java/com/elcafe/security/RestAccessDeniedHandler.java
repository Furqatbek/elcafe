package com.elcafe.security;

import com.elcafe.exception.ApiErrorWriter;
import com.elcafe.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * EH-0.4: authenticated-but-forbidden requests rejected at the security-filter layer (URL matchers)
 * get the standard JSON envelope. AccessDeniedExceptions thrown inside MVC (@PreAuthorize) are
 * already handled by GlobalExceptionHandler with the same code/message, so the two paths look
 * identical to clients.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ApiErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN,
                ErrorCode.FORBIDDEN, "Access denied");
    }
}
