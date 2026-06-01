package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.PersonRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.PersonGroup;
import com.example.tgs_dev.repository.PersonGroupRepository;
import com.example.tgs_dev.repository.PersonRepository;
import com.example.tgs_dev.repository.VehicleRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PersonService} — SCD Type-2 behaviour.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonService")
class PersonServiceTest {

    @Mock PersonRepository      personRepository;
    @Mock PersonGroupRepository personGroupRepository;
    @Mock VehicleRepository     vehicleRepository;
    @Mock TenantService         tenantService;
    @InjectMocks PersonService sut;

    private static final int  COMPANY_ID = 1;
    private static final Long GROUP_ID   = 50L;

    @BeforeEach
    void stubTenant() {
        lenient().when(tenantService.currentCompanyId()).thenReturn(COMPANY_ID);
        lenient().when(tenantService.currentCompany()).thenReturn(company());
    }

    private static Company company() {
        Company c = new Company("ACME", "NIT-1");
        c.setId(COMPANY_ID);
        return c;
    }

    private static PersonGroup group() {
        PersonGroup g = new PersonGroup(company(), "DOC");
        g.setId(GROUP_ID);
        return g;
    }

    private static Person person() {
        Person p = new Person("DOC", "John", null, "Doe", null);
        p.setId(1);
        p.setCompany(company());
        p.setGroup(group());
        p.setIsCurrent(true);
        return p;
    }

    private static PersonRequest request() {
        return new PersonRequest("DOC", "John", null, "Doe", null, null);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("create")
    class Create {
        @Test @DisplayName("creates a group + first version stamped with the tenant company")
        void createsGroupAndFirstVersion() {
            when(personGroupRepository.save(any(PersonGroup.class))).thenAnswer(i -> i.getArgument(0));
            when(personRepository.save(any(Person.class))).thenAnswer(i -> i.getArgument(0));

            Person result = sut.create(request());

            assertThat(result.getCompany().getId()).isEqualTo(COMPANY_ID);
            assertThat(result.getGroup()).isNotNull();
            assertThat(result.getIsCurrent()).isTrue();
            assertThat(result.getVersionFrom()).isNotNull();
            assertThat(result.getVersionTo()).isNull();
            verify(personGroupRepository).save(any(PersonGroup.class));
            verify(personRepository).save(any(Person.class));
        }
    }

    // ── findByGroupId ──────────────────────────────────────────────────────────

    @Nested @DisplayName("findByGroupId")
    class FindByGroupId {
        @Test @DisplayName("returns current version (active or deactivated) so the UI can offer reactivation")
        void found() {
            Person p = person();
            when(personRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(p));
            assertThat(sut.findByGroupId(GROUP_ID)).isSameAs(p);
        }

        @Test @DisplayName("throws ResourceNotFoundException when no current version exists")
        void notFound() {
            when(personRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findByGroupId(GROUP_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(GROUP_ID.toString());
        }
    }

    // ── update ──────────────────────────────────────────────────────────────────

    @Nested @DisplayName("update")
    class Update {
        @Test @DisplayName("closes the current version and opens a new one preserving the group")
        void closesAndOpensVersion() {
            Person current = person();
            when(personRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));
            when(personRepository.save(any(Person.class))).thenAnswer(i -> i.getArgument(0));

            Person next = sut.update(GROUP_ID, request());

            // current version closed
            assertThat(current.getIsCurrent()).isFalse();
            assertThat(current.getVersionTo()).isNotNull();
            // new version opened, same group
            assertThat(next.getIsCurrent()).isTrue();
            assertThat(next.getVersionTo()).isNull();
            assertThat(next.getGroup()).isSameAs(current.getGroup());

            ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
            verify(personRepository, times(2)).save(captor.capture());
        }

        @Test @DisplayName("throws when group has no current version")
        void notFound() {
            when(personRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.update(GROUP_ID, request()))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(personRepository, never()).save(any());
        }
    }

    // ── deactivate ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("deactivate")
    class Deactivate {
        @Test @DisplayName("soft-deletes the current version when no active vehicle owns it")
        void delegates() {
            Person current = person();
            when(personRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));
            when(vehicleRepository.existsByOwnerIdAndActiveTrue(current.getId())).thenReturn(false);

            sut.deactivate(GROUP_ID);

            verify(personRepository).softDelete(current);
        }

        @Test @DisplayName("throws BusinessException when person owns an active vehicle")
        void blockedWhenOwnerOfActiveVehicle() {
            Person current = person();
            when(personRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));
            when(vehicleRepository.existsByOwnerIdAndActiveTrue(current.getId())).thenReturn(true);

            assertThatThrownBy(() -> sut.deactivate(GROUP_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("fk.personIsVehicleOwner");
            verify(personRepository, never()).softDelete(any());
        }
    }

    // ── reactivate ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("reactivate")
    class Reactivate {
        @Test @DisplayName("closes last version and opens a new active version with same data")
        void createsNewActiveVersion() {
            Person last = person();
            last.setActive(false);
            when(personRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(last));
            when(personRepository.save(any(Person.class))).thenAnswer(i -> i.getArgument(0));

            Person next = sut.reactivate(GROUP_ID);

            assertThat(last.getIsCurrent()).isFalse();
            assertThat(next.getActive()).isTrue();
            assertThat(next.getIsCurrent()).isTrue();
            assertThat(next.getGroup()).isSameAs(last.getGroup());
            assertThat(next.getDocumentNumber()).isEqualTo("DOC");
        }
    }

    // ── findByDocumentNumber ──────────────────────────────────────────────────

    @Nested @DisplayName("findByDocumentNumber")
    class FindByDocumentNumber {
        @Test @DisplayName("returns Optional with person when found")
        void found() {
            Person p = person();
            when(personRepository.findOne(any(Specification.class))).thenReturn(Optional.of(p));
            assertThat(sut.findByDocumentNumber("DOC")).contains(p);
        }

        @Test @DisplayName("returns empty Optional when not found")
        void empty() {
            when(personRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThat(sut.findByDocumentNumber("NONE")).isEmpty();
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("returns all current versions scoped to the tenant")
        void returnsAllCurrent() {
            List<Person> list = List.of(person());
            when(personRepository.findAllCurrentByCompany(COMPANY_ID)).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
            verify(personRepository).findAllCurrentByCompany(COMPANY_ID);
        }
    }

    // ── filter ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("delegates to repo with tenant + active Specification")
        void delegates() {
            FilterRequest req  = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<Person>  page = new PageImpl<>(List.of(person()));
            when(personRepository.filter(eq(req), any(), any(Specification.class))).thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }
}
