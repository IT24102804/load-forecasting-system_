package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {


    User findByEmailAndPassword(String email, String password);
    boolean existsByEmail(String email);
}
