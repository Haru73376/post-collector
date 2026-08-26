package com.github.haru73376.post_collector.user;

import com.github.haru73376.post_collector.common.dto.ErrorResponse;
import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "The currently authenticated user's own profile. There is no endpoint to view or manage other users.")
public class UserController {

    private final UserService userService;
    private final SecurityContextUtils securityContextUtils;

    @GetMapping
    @Operation(summary = "Get my profile")
    public ResponseEntity<UserResponse> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile(securityContextUtils.getCurrentUserId()));
    }

    @PatchMapping
    @Operation(summary = "Update my profile", description = "Only the fields provided are changed. Rejects a username that's already taken.")
    @ApiResponse(responseCode = "409", description = "Username is already taken",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"status\":409,\"error\":\"Conflict\",\"message\":\"Username already exists\"}")))
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(securityContextUtils.getCurrentUserId(), request));
    }

    @PatchMapping("/password")
    @Operation(summary = "Change my password", description = "Requires the current password to be provided and correct.")
    @ApiResponse(responseCode = "400", description = "currentPassword is incorrect",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"status\":400,\"error\":\"Bad Request\",\"message\":\"Current password is incorrect\"}")))
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(securityContextUtils.getCurrentUserId(), request);
        return ResponseEntity.noContent().build();
    }
}
