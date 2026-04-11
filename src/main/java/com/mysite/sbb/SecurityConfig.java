package com.mysite.sbb;

import java.io.IOException;
import java.net.URI;

import com.mysite.sbb.user.UserSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserSecurityService userSecurityService;

    @Bean
    AuthenticationManager authenticationManager(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            UserSecurityService userSecurityService) throws Exception {

        return http.getSharedObject(
                org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder.class)
            .userDetailsService(userSecurityService)
            .passwordEncoder(passwordEncoder)
            .and()
            .build();
    }

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            RequestCache requestCache,
            AuthenticationSuccessHandler authenticationSuccessHandler,
            AccessDeniedHandler accessDeniedHandler) throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                .requestMatchers(
                    "/",
                    "/error",
                    "/ai/chat",
                    "/ai/search",
                    "/user/login",
                    "/user/signup",
                    "/h2-console/**",
                    "/a2c.css",
                    "/bootstrap.min.css",
                    "/bootstrap.min.js",
                    "/guide.css",
                    "/guide.js",
                    "/theme-toggle.js",
                    "/ai-chat.js",
                    "/images/**"
                ).permitAll()
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/boards/**", "/skilltree/**", "/guide/**").permitAll()
                .anyRequest().authenticated()
            )
            .requestCache(cache -> cache.requestCache(requestCache))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .exceptionHandling(exception -> exception.accessDeniedHandler(accessDeniedHandler))
            .formLogin(form -> form
                .loginPage("/user/login")
                .loginProcessingUrl("/user/login")
                .successHandler(authenticationSuccessHandler)
                .failureUrl("/user/login?error")
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/user/logout"))
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );

        return http.build();
    }

    @Bean
    RequestCache requestCache() {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(request -> {
            String uri = request.getRequestURI();

            // 정적 리소스 제외
            return request.getMethod().equals("GET")
                    && !uri.startsWith("/css")
                    && !uri.startsWith("/js")
                    && !uri.startsWith("/images")
                    && !uri.endsWith(".js")
                    && !uri.endsWith(".css")
                    && !uri.endsWith(".png")
                    && !uri.endsWith(".jpg")
                    && !uri.endsWith(".jpeg")
                    && !uri.endsWith(".gif")
                    && !uri.endsWith(".ico");
        });
        return requestCache;
    }

    @Bean
    AuthenticationSuccessHandler authenticationSuccessHandler(RequestCache requestCache) {
        SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
                String redirectTarget = sanitizeLocalRedirect(request.getParameter("redirect"));
                if (redirectTarget != null) {
                    return redirectTarget;
                }
                return super.determineTargetUrl(request, response);
            }
        };
        handler.setRequestCache(requestCache);
        handler.setDefaultTargetUrl("/");
        return handler;
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler() {
        AccessDeniedHandlerImpl fallbackHandler = new AccessDeniedHandlerImpl();
        return (request, response, accessDeniedException) -> {
            if (isHtmlRequest(request) && accessDeniedException instanceof CsrfException) {
                redirectAfterCsrfFailure(request, response);
                return;
            }
            fallbackHandler.handle(request, response, accessDeniedException);
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return userSecurityService;
    }

    private void redirectAfterCsrfFailure(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String redirectTarget = resolveRedirectTarget(request);
        if (request.getUserPrincipal() != null) {
            response.sendRedirect(redirectTarget);
            return;
        }
        response.sendRedirect(buildLoginRedirectUrl(redirectTarget));
    }

    private boolean isHtmlRequest(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
        return !StringUtils.hasText(acceptHeader)
                || acceptHeader.contains("text/html")
                || acceptHeader.contains("*/*");
    }

    private String buildLoginRedirectUrl(String redirectTarget) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/user/login")
                .queryParam("expired", "1");
        if (StringUtils.hasText(redirectTarget) && !"/".equals(redirectTarget)) {
            builder.queryParam("redirect", redirectTarget);
        }
        return builder.build().encode().toUriString();
    }

    private String resolveRedirectTarget(HttpServletRequest request) {
        String refererTarget = sanitizeRedirectTarget(request.getHeader("Referer"), request);
        if (refererTarget != null && !refererTarget.startsWith("/user/login")) {
            return refererTarget;
        }

        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/skilltree")) {
            return "/skilltree";
        }
        if (requestUri.startsWith("/trade")) {
            return "/trade/items";
        }
        if (requestUri.startsWith("/boards")) {
            return "/";
        }
        return "/";
    }

    private String sanitizeRedirectTarget(String candidate, HttpServletRequest request) {
        String localTarget = sanitizeLocalRedirect(candidate);
        if (localTarget != null) {
            return localTarget;
        }
        if (!StringUtils.hasText(candidate)) {
            return null;
        }

        try {
            URI uri = URI.create(candidate);
            if (!uri.isAbsolute()) {
                return null;
            }

            String host = uri.getHost();
            if (!StringUtils.hasText(host) || !request.getServerName().equalsIgnoreCase(host)) {
                return null;
            }

            int candidatePort = uri.getPort() == -1 ? defaultPort(uri.getScheme()) : uri.getPort();
            if (candidatePort != request.getServerPort()) {
                return null;
            }

            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            String fragment = uri.getRawFragment();
            String redirectTarget = StringUtils.hasText(query) ? path + "?" + query : path;
            if (StringUtils.hasText(fragment)) {
                redirectTarget = redirectTarget + "#" + fragment;
            }
            return sanitizeLocalRedirect(redirectTarget);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String sanitizeLocalRedirect(String candidate) {
        if (!StringUtils.hasText(candidate) || !candidate.startsWith("/") || candidate.startsWith("//")) {
            return null;
        }
        return candidate;
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
