package com.anjoguarda.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRunner implements ApplicationRunner {

    private final PasswordResetService passwordResetService;

    public PasswordResetRunner(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("reset-password")) {
            return;
        }
        String email = required(args, "email");
        passwordResetService.reset(email, passwordFrom(System.getenv("ANJO_NEW_PASSWORD")));
        System.exit(0);
    }

    static String passwordFrom(String fromEnv) {
        if (fromEnv == null || fromEnv.isBlank()) {
            throw new IllegalArgumentException("Defina ANJO_NEW_PASSWORD");
        }
        return fromEnv;
    }

    private static String required(ApplicationArguments args, String name) {
        if (!args.containsOption(name) || args.getOptionValues(name).isEmpty() || args.getOptionValues(name).getFirst().isBlank()) {
            throw new IllegalArgumentException("Informe --" + name);
        }
        return args.getOptionValues(name).getFirst();
    }
}
