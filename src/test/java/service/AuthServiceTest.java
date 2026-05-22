package service;

import database.UserDAO;
import exception.AuthenticationException;
import exception.ValidationException;
import model.user.Bidder;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private AuthService authService;

    @Mock
    private UserDAO userDAO;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(userDAO);
    }

    @Test
    void testLoginSuccess() throws AuthenticationException {
        User mockUser = new Bidder("u-1", "testuser", "test@example.com", "hashed");
        when(userDAO.login("testuser", "password")).thenReturn(mockUser);

        User result = authService.login("testuser", "password");
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
    }

    @Test
    void testLoginFailure() {
        when(userDAO.login("wrong", "pass")).thenReturn(null);
        assertThrows(AuthenticationException.class, () -> authService.login("wrong", "pass"));
    }

    @Test
    void testValidateFullNameInvalid() {
        assertThrows(ValidationException.class, () -> authService.validateFullName("A"));
        assertThrows(ValidationException.class, () -> authService.validateFullName("123"));
    }

    @Test
    void testValidateFullNameValid() throws ValidationException {
        authService.validateFullName("Nguyen Van A");
    }

    @Test
    void testValidateDobUnderage() {
        LocalDate underage = LocalDate.now().minusYears(17);
        assertThrows(ValidationException.class, () -> authService.validateDob(underage));
    }

    @Test
    void testValidateDobFuture() {
        LocalDate future = LocalDate.now().plusDays(1);
        assertThrows(ValidationException.class, () -> authService.validateDob(future));
    }

    @Test
    void testValidateDobValid() throws ValidationException {
        LocalDate adult = LocalDate.now().minusYears(20);
        authService.validateDob(adult);
    }

    @Test
    void testValidateEmailInvalid() {
        assertThrows(ValidationException.class, () -> authService.validateEmail("invalid-email"));
    }

    @Test
    void testValidateEmailValid() throws ValidationException {
        authService.validateEmail("test@example.com");
    }

    @Test
    void testRegisterDuplicateUsername() {
        when(userDAO.existsByUsername("existing")).thenReturn(true);
        assertThrows(ValidationException.class, () -> authService.register(
                "Full Name", LocalDate.now().minusYears(20), "0987654321", 
                "new@example.com", "existing", "Password123"
        ));
    }

    @Test
    void testRegisterSuccess() throws ValidationException {
        when(userDAO.existsByUsername(anyString())).thenReturn(false);
        when(userDAO.existsByEmail(anyString())).thenReturn(false);
        when(userDAO.save(any(User.class))).thenReturn(true);

        User result = authService.register(
                "Nguyen Van A", LocalDate.now().minusYears(20), "0987654321",
                "a@example.com", "newuser", "Password123"
        );

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        verify(userDAO, times(1)).save(any(User.class));
    }
}
