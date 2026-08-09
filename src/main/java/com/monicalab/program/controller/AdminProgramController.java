package com.monicalab.program.controller;

import com.monicalab.common.dto.PageResponse;
import com.monicalab.common.response.ApiResponse;
import com.monicalab.program.dto.ProgramRequest;
import com.monicalab.program.dto.ProgramResponse;
import com.monicalab.program.dto.ProgramStatusRequest;
import com.monicalab.program.dto.ProgramVisibilityRequest;
import com.monicalab.program.service.ProgramService;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/programs")
@RequiredArgsConstructor
public class AdminProgramController {

    private final ProgramService programService;

    @GetMapping
    public ApiResponse<PageResponse<ProgramResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(programService.getAdminList(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProgramResponse> get(@PathVariable Long id) {
        return ApiResponse.success(programService.getAdminById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProgramResponse> create(@Valid @RequestBody ProgramRequest request) {
        return ApiResponse.success(programService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProgramResponse> update(@PathVariable Long id,
            @Validated({Default.class, ProgramRequest.OnUpdate.class}) @RequestBody ProgramRequest request) {
        return ApiResponse.success(programService.update(id, request));
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<ProgramResponse> updateVisibility(@PathVariable Long id,
            @Valid @RequestBody ProgramVisibilityRequest request) {
        return ApiResponse.success(programService.updateVisibility(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ProgramResponse> updateStatus(@PathVariable Long id,
            @Valid @RequestBody ProgramStatusRequest request) {
        return ApiResponse.success(programService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        programService.delete(id);
    }
}
