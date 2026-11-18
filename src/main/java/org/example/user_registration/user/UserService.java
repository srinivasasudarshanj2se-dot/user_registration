package org.example.user_registration.user;

import org.example.user_registration.common.EmailAlreadyExistsException;
import org.example.user_registration.common.ResourceNotFoundException;
import org.example.user_registration.user.UserDtos.UserRequest;
import org.example.user_registration.user.UserDtos.UserResponse;
import org.example.user_registration.user.UserDtos.SelfUserUpdateRequest;
import org.example.user_registration.user.UserDtos.UserUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

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
        logger.info("User created successfully (userId={})", user.getId());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return UserResponse.from(findUserByEmail(email));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = findUser(id);
        return updateProfile(user, request);
    }

    @Transactional
    public UserResponse updateCurrentUser(String email, SelfUserUpdateRequest request) {
        User user = findUserByEmail(email);
        user.updateProfile(request.firstName().trim(), request.lastName().trim(), user.getEmail(),
                request.mobile().trim());
        User updatedUser = userRepository.save(user);
        logger.info("User profile updated successfully (userId={})", updatedUser.getId());
        return UserResponse.from(updatedUser);
    }

    private UserResponse updateProfile(User user, UserUpdateRequest request) {
        String email = normalizeEmail(request.email());
        if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        user.updateProfile(request.firstName().trim(), request.lastName().trim(), email, request.mobile().trim());
        User updatedUser = userRepository.save(user);
        logger.info("User profile updated successfully (userId={})", updatedUser.getId());
        return UserResponse.from(updatedUser);
    }

    @Transactional
    public void delete(Long id) {
        userRepository.delete(findUser(id));
        logger.info("User deleted successfully (userId={})", id);
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

    private User findUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user was not found"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
