package com.lqragent.backend.config;

import com.lqragent.backend.user.entity.User;
import com.lqragent.backend.user.entity.User.Role;
import com.lqragent.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 应用启动后自动初始化基础数据。
 * 幂等：已存在的账号不会重复创建。
 *
 * 默认测试账号（启动时创建或同步密码）：
 *   admin    / 123456 → ADMIN
 *   student1 / 123456 → STUDENT
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        syncDefaultUsersNow();
        log.info("[DataInitializer] 初始化完成");
    }

    /** 供控制台 sync 命令调用 */
    public void syncDefaultUsersNow() {
        upsertDefaultUser("admin",    "123456", "系统管理员", Role.ADMIN);
        upsertDefaultUser("student1", "123456", "测试学生",   Role.STUDENT);
    }

    /**
     * 创建或同步内置测试账号，避免「账号已存在但密码仍是旧值」导致无法登录。
     */
    private void upsertDefaultUser(String username, String rawPassword,
                                   String displayName, Role role) {
        userRepository.findByUsername(username).ifPresentOrElse(
                user -> {
                    boolean passwordOk = passwordEncoder.matches(rawPassword, user.getPassword());
                    if (passwordOk && role.equals(user.getRole()) && user.getEnabled()) {
                        log.info("[DataInitializer] 测试账号已就绪：{}", username);
                        return;
                    }
                    user.setPassword(passwordEncoder.encode(rawPassword));
                    user.setDisplayName(displayName);
                    user.setRole(role);
                    user.setEnabled(true);
                    userRepository.save(user);
                    log.info("[DataInitializer] 已同步测试账号：{} ({})", username, role);
                },
                () -> {
                    User user = User.builder()
                            .username(username)
                            .password(passwordEncoder.encode(rawPassword))
                            .displayName(displayName)
                            .role(role)
                            .enabled(true)
                            .build();
                    userRepository.save(user);
                    log.info("[DataInitializer] 创建测试账号：{} ({})", username, role);
                }
        );
    }
}
