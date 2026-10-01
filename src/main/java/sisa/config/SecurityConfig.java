package sisa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Central login/permission gate for every module (report section 7, item 12:
 * "User & Access <-> All Other Modules"). Role checks per-page are refined
 * on each module's branch as it is built.
 */
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
                // Account creation is the Registrar's job alone (report section 6.2/6.3) — the
                // Principal's role in User & Access is to approve/reject/disable/reset, never to
                // create Student/Teacher/Parent accounts, so these three need their own tighter
                // rule ahead of the general /registrar/** one below (the Principal still creates
                // Registrar accounts under /principal/accounts/new-registrar — unaffected, that's
                // the one account type only the Principal creates).
                .requestMatchers("/registrar/students/new", "/registrar/teachers/new", "/registrar/accounts/new-parent")
                    .hasRole("REGISTRAR")
                .requestMatchers("/registrar/**").hasAnyRole("PRINCIPAL", "REGISTRAR")
                .requestMatchers("/teacher/**").hasAnyRole("PRINCIPAL", "TEACHER")
                .requestMatchers("/student/**").hasAnyRole("PRINCIPAL", "STUDENT")
                .requestMatchers("/parent/**").hasAnyRole("PRINCIPAL", "PARENT")
                // Module 8 (Resources & Facilities): every role may view/use this space —
                // Student ("view lab schedules", "book a study room, if allowed") and Parent
                // ("view facility schedules and closures") included — so it now falls through
                // to the general anyRequest() rule below; BookingController itself narrows
                // what each role may actually POST (Parent view-only, Student limited to
                // resources flagged studentBookable).
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
            // H2 console convenience for local dev only - remove before real deployment
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));

        return http.build();
    }
}
