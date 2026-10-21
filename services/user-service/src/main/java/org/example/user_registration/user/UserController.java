package org.example.user_registration.user;

import jakarta.validation.Valid;
import org.example.user_registration.user.UserDtos.UserRequest;
import org.example.user_registration.user.UserDtos.UserResponse;
import org.example.user_registration.user.UserDtos.UserUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Manage registered users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user")
    public UserResponse create(@Valid @RequestBody UserRequest request) {
        UserResponse response = userService.create(request);
        logger.info("Created user id={}", response.id());
        return response;
    }

    @GetMapping
    @Operation(summary = "List users", description = "Optionally filter by email and paginate the results.")
    public Page<UserResponse> list(@RequestParam(required = false) String email,
                                   @PageableDefault(size = 10) Pageable pageable) {
        logger.info("Listing users page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return userService.list(email, pageable);
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by email")
    public Page<UserResponse> search(@RequestParam String email,
                                     @PageableDefault(size = 10) Pageable pageable) {
        logger.info("Searching users page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return userService.list(email, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user")
    public UserResponse get(@PathVariable Long id) {
        logger.info("Fetching user id={}", id);
        return userService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        logger.info("Updating user id={}", id);
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a user")
    public void delete(@PathVariable Long id) {
        logger.info("Deleting user id={}", id);
        userService.delete(id);
    }
}
