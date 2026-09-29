package com.anjoguarda.family;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/children")
public class ChildController {

    private final ChildService childService;

    public ChildController(ChildService childService) {
        this.childService = childService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChildResponse register(@Valid @RequestBody RegisterChildRequest request) {
        return childService.register(currentUserId(), request);
    }

    @GetMapping
    public List<ChildResponse> list() {
        return childService.list(currentUserId());
    }

    @GetMapping("/{id}")
    public ChildResponse get(@PathVariable UUID id) {
        return childService.get(currentUserId(), id);
    }

    private UUID currentUserId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
