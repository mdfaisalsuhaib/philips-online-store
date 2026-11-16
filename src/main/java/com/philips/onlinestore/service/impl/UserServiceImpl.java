package com.philips.onlinestore.service.impl;

import com.philips.onlinestore.dto.RegisterRequest;
import com.philips.onlinestore.dto.LoginRequest;
import com.philips.onlinestore.dto.LoginResponse;
import com.philips.onlinestore.dto.UserResponse;
import com.philips.onlinestore.entity.User;
import com.philips.onlinestore.entity.UserRole;
import com.philips.onlinestore.exception.EmailAlreadyExistsException;
import com.philips.onlinestore.exception.InvalidCredentialsException;
import com.philips.onlinestore.repository.UserRepository;
import com.philips.onlinestore.security.jwt.JwtService;
import com.philips.onlinestore.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long jwtExpirationMs;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${jwt.expiration-ms}") long jwtExpirationMs
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        User user = new User(
                request.fullName(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                UserRole.CUSTOMER
        );
        user.setEnabled(true);

        User savedUser = userRepository.save(user);
        return toResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword()) || !user.isEnabled()) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.getEmail());
        return new LoginResponse(
                token,
                "Bearer",
                jwtExpirationMs,
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
