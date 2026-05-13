package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.PersonRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.PersonDTO;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.mapper.PersonMapper;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.service.PersonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/person")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;
    private final PersonMapper personMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PersonDTO>>> getAll() {
        List<PersonDTO> persons = personMapper.toDTOList(personService.findAll());
        return ResponseEntity.ok(ApiResponse.ok(persons));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PersonDTO>> getById(@PathVariable Integer id) {
        PersonDTO dto = personMapper.toDTO(personService.findById(id));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PersonDTO>> create(@RequestBody @Valid PersonRequest request) {
        Person person = personMapper.toEntity(request);
        PersonDTO dto = personMapper.toDTO(personService.save(person));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Person created successfully", dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PersonDTO>> update(@PathVariable Integer id,
                                                         @RequestBody @Valid PersonRequest request) {
        Person person = personService.findById(id);
        personMapper.updateEntity(person, request);
        PersonDTO dto = personMapper.toDTO(personService.save(person));
        return ResponseEntity.ok(ApiResponse.ok("Person updated successfully", dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        personService.delete(personService.findById(id));
        return ResponseEntity.ok(ApiResponse.ok("Person deleted successfully", null));
    }

    @PostMapping("/filter")
    public ResponseEntity<ApiResponse<Page<PersonDTO>>> filter(@RequestBody @Valid FilterRequest request) {
        Page<PersonDTO> page = personService.filter(request).map(personMapper::toDTO);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
