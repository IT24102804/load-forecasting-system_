package com.example.loadforcasting.Service; // Adjust this if your package name is slightly different

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

import com.example.loadforcasting.Entity.User;
import com.example.loadforcasting.Repository.UserRepository;

@Service
public class UserService {

    @Autowired
    private UserRepository repo;

    public String register(User user) {

        if (repo.existsByEmail(user.getEmail())) {
            return "Email already exists";
        }

        user.setRole("User"); // Sets default role for new registrations
        repo.save(user);
        return "success";
    }

    public User login(String email, String password) {
        return repo.findByEmailAndPassword(email, password);
    }

    // FIX: Changed from Long to Integer
    public User findById(Integer id) {
        return repo.findById(id).orElse(null);
    }

    public void update(User user) {
        repo.save(user);
    }

    // FIX: Changed from Long to Integer
    public void delete(Integer id) {
        repo.deleteById(id);
    }

    public List<User> getAllUsers() {
        return repo.findAll();
    }
}