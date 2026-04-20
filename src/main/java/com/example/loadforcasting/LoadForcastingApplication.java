package com.example.loadforcasting;

import com.example.loadforcasting.Entity.User;
import com.example.loadforcasting.Repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LoadForcastingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadForcastingApplication.class, args);
    }

    @Bean
    CommandLineRunner createDefaultAdmin(UserRepository userRepository) {
        return args -> {
            // Only create if admin doesn't already exist
            if (!userRepository.existsByEmail("admin@loadforecast.com")) {
                User admin = new User();
                admin.setName("Administrator");
                admin.setEmail("admin@loadforecast.com");
                admin.setPassword("admin123");
                admin.setRole("Admin");
                admin.setStatus("Active");
                userRepository.save(admin);
                System.out.println("Default admin created: admin@loadforecast.com / admin123");
            }
        };
    }
}