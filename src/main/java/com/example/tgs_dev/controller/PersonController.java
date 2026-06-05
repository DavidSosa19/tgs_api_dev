package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.PersonRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.PersonDTO;
import com.example.tgs_dev.mapper.PersonMapper;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.PersonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for {@link com.example.tgs_dev.entity.Person}.
 *
 * <p>Path-variable {@code groupId} is the SCD stable business identity
 * ({@code person_group.id}), not the surrogate version id.
 */
@RestController
@RequestMapping("/api/persons")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;
    private final PersonMapper  personMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.PERSON_READ + "')")
    public ResponseEntity<ApiResponse<List<PersonDTO>>> getAll() {
        List<PersonDTO> persons = personMapper.toDTOList(personService.findAll());
        return ResponseEntity.ok(ApiResponse.ok(persons));
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.PERSON_READ + "')")
    public ResponseEntity<ApiResponse<PersonDTO>> getById(@PathVariable Long groupId) {
        PersonDTO dto = personMapper.toDTO(personService.findByGroupId(groupId));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.PERSON_WRITE + "')")
    public ResponseEntity<ApiResponse<PersonDTO>> create(@RequestBody @Valid PersonRequest request) {
        PersonDTO dto = personMapper.toDTO(personService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Person created successfully", dto));
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.PERSON_WRITE + "')")
    public ResponseEntity<ApiResponse<PersonDTO>> update(@PathVariable Long groupId,
                                                         @RequestBody @Valid PersonRequest request) {
        PersonDTO dto = personMapper.toDTO(personService.update(groupId, request));
        return ResponseEntity.ok(ApiResponse.ok("Person updated successfully", dto));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.PERSON_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long groupId) {
        personService.deactivate(groupId);
        return ResponseEntity.ok(ApiResponse.ok("Person deleted successfully", null));
    }

    @PatchMapping("/{groupId}/reactivate")
    @PreAuthorize("hasAuthority('" + Permissions.PERSON_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> reactivate(@PathVariable Long groupId) {
        personService.reactivate(groupId);
        return ResponseEntity.ok(ApiResponse.ok("person.reactivated", null));
    }

    @PostMapping("/filter")
    @PreAuthorize("hasAuthority('" + Permissions.PERSON_READ + "')")
    public ResponseEntity<ApiResponse<Page<PersonDTO>>> filter(@RequestBody @Valid FilterRequest request) {
        Page<PersonDTO> page = personService.filter(request).map(personMapper::toDTO);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
