package com.example.tgs_dev.service.admin;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.admin.CreateAdminUserRequest;
import com.example.tgs_dev.controller.request.admin.UpdateAdminUserRequest;
import com.example.tgs_dev.controller.response.PersonDTO;
import com.example.tgs_dev.controller.response.admin.UserAdminDTO;
import com.example.tgs_dev.entity.AppRoleEntity;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.PersonGroup;
import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.repository.AppRoleRepository;
import com.example.tgs_dev.repository.CompanyRepository;
import com.example.tgs_dev.repository.PersonGroupRepository;
import com.example.tgs_dev.repository.PersonRepository;
import com.example.tgs_dev.repository.UserRepository;

import java.time.LocalDateTime;
import com.example.tgs_dev.security.AppRole;
import com.example.tgs_dev.security.Permissions;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Cross-tenant user management service for the SUPER_ADMIN admin module.
 *
 * <p>Business constraints enforced here:
 * <ul>
 *   <li>Person→User is 1:1: a person cannot be linked to more than one user account.</li>
 *   <li>A user may hold only one role at a time.</li>
 *   <li>The SUPER_ADMIN role cannot be assigned via this endpoint.</li>
 * </ul>
 *
 * <p>This service deliberately does <strong>not</strong> use
 * {@link com.example.tgs_dev.repository.specification.TenantSpecifications} —
 * operations here are intentionally cross-tenant.
 */
@Service
public class AdminUserService {

    private final UserRepository        userRepository;
    private final PersonRepository      personRepository;
    private final PersonGroupRepository personGroupRepository;
    private final CompanyRepository     companyRepository;
    private final AppRoleRepository     appRoleRepository;
    private final PasswordEncoder       passwordEncoder;

    public AdminUserService(UserRepository        userRepository,
                            PersonRepository      personRepository,
                            PersonGroupRepository personGroupRepository,
                            CompanyRepository     companyRepository,
                            AppRoleRepository     appRoleRepository,
                            PasswordEncoder       passwordEncoder) {
        this.userRepository        = userRepository;
        this.personRepository      = personRepository;
        this.personGroupRepository = personGroupRepository;
        this.companyRepository = companyRepository;
        this.appRoleRepository = appRoleRepository;
        this.passwordEncoder   = passwordEncoder;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns ALL users (active + inactive) for admin view. */
    @Transactional(readOnly = true)
    public List<UserAdminDTO> findAll() {
        assertSuperAdmin();
        return userRepository.findAllAdmin().stream()
                .map(this::toDTO)
                .toList();
    }

    /** Returns ALL users (active + inactive) for a given company. */
    @Transactional(readOnly = true)
    public List<UserAdminDTO> findByCompany(Integer companyId) {
        assertSuperAdmin();
        return userRepository.findAllByCompanyIdAdmin(companyId).stream()
                .map(this::toDTO)
                .toList();
    }

    /** Finds a user by ID regardless of active status. */
    @Transactional(readOnly = true)
    public UserAdminDTO findById(Long id) {
        assertSuperAdmin();
        return toDTO(loadUserOrThrow(id));
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    public UserAdminDTO create(CreateAdminUserRequest request) {
        assertSuperAdmin();

        Company company = loadCompanyOrThrow(request.companyId());
        AppRoleEntity role = loadRoleOrThrow(request.roleId());
        assertNotSuperAdminRole(role);

        Person person = resolvePerson(request, company);
        String encodedPassword = Objects.requireNonNull(passwordEncoder.encode(request.password()));

        User user = new User(
                request.userName(),
                encodedPassword,
                Set.of(role),
                person,
                company
        );
        return toDTO(userRepository.save(user));
    }

    @Transactional
    public UserAdminDTO update(Long id, UpdateAdminUserRequest request) {
        assertSuperAdmin();

        User user = loadUserOrThrow(id);
        AppRoleEntity role = loadRoleOrThrow(request.roleId());
        assertNotSuperAdminRole(role);

        user.setRoles(Set.of(role));
        user.setActive(request.active());
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            String newPassword = request.newPassword();
            user.setPassword(Objects.requireNonNull(passwordEncoder.encode(newPassword)));
        }
        return toDTO(userRepository.save(user));
    }

    @Transactional
    public void deactivate(Long id) {
        assertSuperAdmin();
        userRepository.softDelete(loadUserOrThrow(id));
    }

    @Transactional
    public void reactivate(Long id) {
        assertSuperAdmin();
        userRepository.findByIdAdmin(id)
                .orElseThrow(() -> new ResourceNotFoundException("notFound.user|" + id));
        userRepository.reactivateById(id);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves the Person for a new user.
     * If {@code personId} is provided, links an existing person (must belong to the company).
     * Otherwise, creates a new person inline from the request fields.
     */
    private Person resolvePerson(CreateAdminUserRequest request, Company company) {
        if (request.personId() != null) {
            Integer personId = Objects.requireNonNull(request.personId());
            Person existing = personRepository.findById(personId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "notFound.person|" + request.personId()));

            if (!existing.getCompany().getId().equals(company.getId())) {
                throw new BusinessException("admin.person.wrongCompany");
            }
            if (userRepository.existsByPerson_Id(existing.getId())) {
                throw new BusinessException("admin.person.alreadyHasUser");
            }
            return existing;
        }

        // Inline person creation — validate required fields
        if (request.documentNumber() == null || request.firstName() == null
                || request.firstLastName() == null) {
            throw new BusinessException("admin.person.requiredFieldsMissing");
        }

        // SCD-aware inline creation: build the person_group + first version.
        PersonGroup group = personGroupRepository.save(
                new PersonGroup(company, request.documentNumber()));

        Person newPerson = new Person(
                request.documentNumber(),
                request.firstName(),
                request.secondName(),
                request.firstLastName(),
                request.secondLastName()
        );
        newPerson.setCompany(company);
        newPerson.setGroup(group);
        newPerson.setVersionFrom(LocalDateTime.now());
        newPerson.setIsCurrent(true);
        return personRepository.save(newPerson);
    }

    private User loadUserOrThrow(Long id) {
        return userRepository.findByIdAdmin(id)
                .orElseThrow(() -> new ResourceNotFoundException("notFound.user|" + id));
    }

    private Company loadCompanyOrThrow(Integer id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("notFound.company|" + id));
    }

    private AppRoleEntity loadRoleOrThrow(Integer id) {
        return appRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("notFound.role|" + id));
    }

    /** SUPER_ADMIN role cannot be granted via the user management API. */
    private void assertNotSuperAdminRole(AppRoleEntity role) {
        if (AppRole.SUPER_ADMIN.equals(role.getName())) {
            throw new BusinessException("admin.role.superAdminForbidden");
        }
    }

    private UserAdminDTO toDTO(User u) {
        List<String> roles = u.getRoles().stream()
                .map(AppRoleEntity::getName)
                .sorted()
                .toList();

        PersonDTO personDTO = null;
        if (u.getPerson() != null) {
            Person p = u.getPerson();
            personDTO = new PersonDTO(
                    p.getId(),
                    p.getGroup() != null ? p.getGroup().getId() : null,
                    p.getDocumentNumber(),
                    p.getFirstName(),
                    p.getSecondName(),
                    p.getFirstLastName(),
                    p.getSecondLastName(),
                    p.getActive()
            );
        }

        return new UserAdminDTO(
                u.getId(),
                u.getUsername(),
                roles,
                u.getActive(),
                u.getCompany().getId(),
                u.getCompany().getName(),
                personDTO
        );
    }

    /**
     * Defense-in-depth: verifies the calling thread has the SUPER_ADMIN_ACCESS authority.
     */
    private void assertSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals(Permissions.SUPER_ADMIN_ACCESS))) {
            throw new AccessDeniedException("admin.access.denied");
        }
    }
}
