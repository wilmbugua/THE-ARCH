package com.kalcpos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultUserInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DefaultUserInitializer.class);

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final boolean syncDefaultUsers;

    public DefaultUserInitializer(
            JdbcTemplate jdbc,
            @Value("${kalcpos.default-users.sync:true}") boolean syncDefaultUsers) {
        this.jdbc = jdbc;
        this.syncDefaultUsers = syncDefaultUsers;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!syncDefaultUsers) {
            return;
        }

        List<DefaultUser> users = List.of(
                new DefaultUser("admin", "Administrator", "admin", "11112222"),
                new DefaultUser("manager", "Manager", "manager", "22223333"),
                new DefaultUser("super_waiter", "Super Waiter", "super_waiter", "33334444"),
                new DefaultUser("waiter", "Waiter", "waiter", "44445555"),
                new DefaultUser("supervisor", "Supervisor", "supervisor", "55556666")
        );

        for (DefaultUser user : users) {
            String hash = passwordEncoder.encode(user.pin());
            int updated = jdbc.update("""
                    UPDATE users
                    SET real_name = ?, role = ?, pin_hash = ?, active = 1
                    WHERE username = ?
                    """, user.realName(), user.role(), hash, user.username());

            if (updated == 0) {
                jdbc.update("""
                        INSERT INTO users (username, real_name, role, pin_hash, active)
                        VALUES (?, ?, ?, ?, 1)
                        """, user.username(), user.realName(), user.role(), hash);
            }
        }

        log.info("Default login users are synced and active");
    }

    private record DefaultUser(String username, String realName, String role, String pin) {
    }
}
