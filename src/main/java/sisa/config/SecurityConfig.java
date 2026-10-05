package sisa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Value("${sisa.security.remember-me-key:#{T(java.util.UUID).randomUUID().toString()}}")
    private String rememberMeKey;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/login", "/error", "/h2-console/**").permitAll()
                .requestMatchers("/principal/**").hasRole("PRINCIPAL")
                .requestMatchers("/registrar/students/new", "/registrar/teachers/new", "/registrar/accounts/new-parent")
                    .hasRole("REGISTRAR")
                .requestMatchers("/registrar/**").hasAnyRole("PRINCIPAL", "REGISTRAR")
                .requestMatchers("/teacher/**").hasAnyRole("PRINCIPAL", "TEACHER")
                .requestMatchers("/student/**").hasAnyRole("PRINCIPAL", "STUDENT")
                .requestMatchers("/parent/**").hasAnyRole("PRINCIPAL", "PARENT")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .rememberMe(remember -> remember
                .key(rememberMeKey)
                .rememberMeParameter("remember-me")
                .tokenValiditySeconds(14 * 24 * 60 * 60)
            )
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").deleteCookies("JSESSIONID", "remember-me").permitAll())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));

        return http.build();
    }
}
