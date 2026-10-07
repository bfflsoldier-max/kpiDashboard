package com.qa.datafeeder;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(
        origins = "${dashboard.cors.allowed-origin:http://localhost:8080}",
        allowedHeaders = {"Content-Type"})
public class AdminController {

    private final AdminSessionService adminSessionService;

    public AdminController(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
    }

    @PostMapping("/login")
    public AdminLoginResponse login(@Valid @RequestBody AdminLoginRequest request) {
        String token = adminSessionService.login(request.username(), request.password());
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials");
        }
        return new AdminLoginResponse(token);
    }
}
