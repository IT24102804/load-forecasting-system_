package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.User;
import com.example.loadforcasting.Service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerAdminWebTest {

    @Mock
    private UserService service;

    @Mock
    private Model model;

    @InjectMocks
    private AuthController authController;

    private MockHttpSession adminSession;
    private MockHttpSession nonAdminSession;
    private MockHttpSession loggedOutSession;

    @BeforeEach
    void setUp() {
        adminSession = new MockHttpSession();
        adminSession.setAttribute("role", "Admin");

        nonAdminSession = new MockHttpSession();
        nonAdminSession.setAttribute("role", "User");

        loggedOutSession = new MockHttpSession();
    }

    @Test
    void viewUsers_AdminSession_ReturnsUserListView() {
        List<User> users = List.of(user(1, "Main Admin", "Admin"), user(2, "Operator", "User"));
        when(service.getAllUsers()).thenReturn(users);

        String view = authController.viewUsers(adminSession, model);

        assertEquals("viewUsers", view);
        verify(service).getAllUsers();
        verify(model).addAttribute("users", users);
    }

    @Test
    void viewUsers_NonAdminSession_RedirectsToRoot() {
        String view = authController.viewUsers(nonAdminSession, model);

        assertEquals("redirect:/", view);
        verify(service, never()).getAllUsers();
    }

    @Test
    void viewUsers_MissingRole_RedirectsToRoot() {
        String view = authController.viewUsers(loggedOutSession, model);

        assertEquals("redirect:/", view);
        verify(service, never()).getAllUsers();
    }

    @Test
    void adminDelete_AdminDeletesNonAdminUser() {
        when(service.findById(5)).thenReturn(user(5, "Operator", "User"));

        String view = authController.adminDelete(5, adminSession);

        assertEquals("redirect:/viewUsers", view);
        verify(service).findById(5);
        verify(service).delete(5);
    }

    @Test
    void adminDelete_AdminCannotDeleteAdminUser() {
        when(service.findById(1)).thenReturn(user(1, "Main Admin", "Admin"));

        String view = authController.adminDelete(1, adminSession);

        assertEquals("redirect:/viewUsers", view);
        verify(service).findById(1);
        verify(service, never()).delete(1);
    }

    @Test
    void adminDelete_NonAdminSession_RedirectsWithoutDeletion() {
        String view = authController.adminDelete(7, nonAdminSession);

        assertEquals("redirect:/", view);
        verify(service, never()).findById(7);
        verify(service, never()).delete(7);
    }

    private User user(int id, String name, String role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + "@example.com");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return user;
    }
}
