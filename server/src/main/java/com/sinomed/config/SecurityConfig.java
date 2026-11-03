package com.sinomed.config;
import com.sinomed.security.JwtRequestFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtRequestFilter jwtRequestFilter;
    private final Environment environment;
    private final String LoginAPI = "/api/v1/user/login";

    /** API 文档面（Swagger/Knife4j）：仅在非 prod profile 放行，prod 下按 anyRequest 需认证。 */
    private static final String[] DOCS_PUBLIC = {
            "/v3/api-docs/**",  // Swagger v3 API docs
            "/swagger-resources/**",  // Swagger resources
            "/swagger-ui.html",  // Swagger UI HTML page
            "/webjars/**",  // Webjars (for Swagger UI)
            "/swagger-ui/**",  // Swagger UI path
            "/doc.html"  // Knife4j HTML page
    };

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // prod 可能经 spring.profiles.default 激活（默认 profile，getActiveProfiles() 为空），
        // 用 acceptsProfiles 同时覆盖 active 与 default，避免 C11 收紧在默认 profile 下静默失效。
        boolean prod = environment.acceptsProfiles("prod");

        http.cors(Customizer.withDefaults()).csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> {
                    // 业务公开端点（无 JWT 的来源：平板/Kiosk/公众号/粉丝 H5/打印模板/媒体/登录）
                    authorize.requestMatchers(
                            "/api/v1/ads/playlist",  // 平板拉排期（只读 + 屏 code 校验，见 docs/design/tablet.md）
                            "/api/v1/calls/latest",  // 平板拉叫号（只读 + 屏 code 校验）
                            "/api/v1/kiosk/**",  // 顾客选服务 Kiosk：浏览只读 + 下单唯一写入口（见 docs/design/kiosk.md）
                            "/api/v1/wx/callback/**",  // 公众号消息回调：微信服务器无 JWT，来源可信靠签名验证（见 docs/design/wechat-official-account.md §5.1）
                            "/api/v1/wx/bind/**",  // 公众号粉丝绑定（H5 粉丝侧无 JWT，手机号+短信验证码双因子自证，同 Kiosk 口径；见 docs/design/wechat-official-account.md §4.7）
                            "/api/v1/wx/oauth/**",  // 公众号网页授权 start/callback（粉丝侧浏览器无 JWT，state HMAC 防伪 + ticket 兑换，见 docs/design/wechat-official-account.md §5.6）
                            "/api/v1/print/templates",  // 打印模板下发：空白版式无业务数据，桌面壳免登录拉取（见 docs/design/desktop.md D7）
                            "/media/**",  // 广告媒体静态资源（Nginx 托管时不受此约束）
                            "/h5/**",  // 公众号粉丝 H5 绑定静态页（粉丝侧无 JWT；页面只携带一次性 ticket，身份靠手机号+短信码双因子自证，见 docs/design/wechat-official-account.md §5.6）
                            LoginAPI).permitAll();
                    // API 文档面 / Actuator：prod 收紧为需认证（C11），其余 profile 维持放行
                    if (!prod) {
                        authorize.requestMatchers(DOCS_PUBLIC).permitAll();
                    }
                    authorize.anyRequest().authenticated();
                }).exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write("Unauthorized: " + authException.getMessage());
                        })
                );
        http.authenticationProvider(authenticationProvider()).
                addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
