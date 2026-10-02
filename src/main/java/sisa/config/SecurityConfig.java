package sisa.config;

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
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));

        return http.build();
    }
}
