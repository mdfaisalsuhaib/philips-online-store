package com.philips.onlinestore.security.service;

import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void loadsNormalizedEmailAndCurrentRoleFromUserRepository() {
        User user = new User(
                "Store Admin",
                "admin@example.com",
                "$2a$10$stored-password-hash",
                UserRole.ADMIN
        );
        user.setEnabled(true);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
        UserDetailsServiceImpl userDetailsService = new UserDetailsServiceImpl(userRepository);

        UserDetails userDetails = userDetailsService.loadUserByUsername(" ADMIN@EXAMPLE.COM ");

        assertEquals("admin@example.com", userDetails.getUsername());
        assertEquals("$2a$10$stored-password-hash", userDetails.getPassword());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
        verify(userRepository).findByEmail("admin@example.com");
    }

    @Test
    void rejectsUnknownUser() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        UserDetailsServiceImpl userDetailsService = new UserDetailsServiceImpl(userRepository);

        assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("missing@example.com")
        );
    }

    @Test
    void marksDisabledDatabaseUserDisabledInSecurityPrincipal() {
        User user = new User(
                "Disabled Customer",
                "disabled@example.com",
                "$2a$10$stored-password-hash",
                UserRole.CUSTOMER
        );
        user.setEnabled(false);
        when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(user));
        UserDetailsServiceImpl userDetailsService = new UserDetailsServiceImpl(userRepository);

        UserDetails userDetails = userDetailsService.loadUserByUsername("disabled@example.com");

        assertFalse(userDetails.isEnabled());
    }
}
