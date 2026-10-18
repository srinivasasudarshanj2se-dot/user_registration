package org.example.user_registration.user;

import org.example.user_registration.common.EmailAlreadyExistsException;
import org.example.user_registration.common.ResourceNotFoundException;
import org.example.user_registration.user.UserDtos.UserRequest;
import org.example.user_registration.user.UserDtos.UserResponse;
import org.example.user_registration.user.UserDtos.UserUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        Role role = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Required ROLE_USER seed data is missing"));
        User user = userRepository.save(new User(request.firstName().trim(), request.lastName().trim(), email,
                request.mobile().trim(), passwordEncoder.encode(request.password()), role));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = findUser(id);
        String email = normalizeEmail(request.email());
        if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        user.updateProfile(request.firstName().trim(), request.lastName().trim(), email, request.mobile().trim());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        userRepository.delete(findUser(id));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String email, Pageable pageable) {
        Page<User> users = email == null || email.isBlank()
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCase(email.trim(), pageable);
        return users.map(UserResponse::from);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " was not found"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
