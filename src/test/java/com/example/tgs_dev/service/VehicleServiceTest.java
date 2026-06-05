package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.request.VehicleRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.entity.VehicleGroup;
import com.example.tgs_dev.repository.VehicleGroupRepository;
import com.example.tgs_dev.repository.VehicleRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VehicleService} — SCD Type-2 behaviour.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VehicleService")
class VehicleServiceTest {

    @Mock VehicleRepository      vehicleRepository;
    @Mock VehicleGroupRepository vehicleGroupRepository;
    @Mock PersonService          personService;
    @Mock TenantService          tenantService;
    @InjectMocks VehicleService sut;

    private static final int  COMPANY_ID = 1;
    private static final Long GROUP_ID   = 50L;

    @BeforeEach
    void setUp() {
        lenient().when(tenantService.currentCompanyId()).thenReturn(COMPANY_ID);
        lenient().when(tenantService.currentCompany()).thenReturn(company());
    }

    private static Company company() {
        Company c = new Company("ACME", "NIT-1");
        c.setId(COMPANY_ID);
        return c;
    }

    private static VehicleGroup group() {
        VehicleGroup g = new VehicleGroup(company(), "V-001");
        g.setId(GROUP_ID);
        return g;
    }

    private static Vehicle vehicle() {
        Vehicle v = new Vehicle("V-001", null);
        v.setId(1);
        v.setLicensePlate("ABC-123");
        v.setCompany(company());
        v.setGroup(group());
        v.setIsCurrent(true);
        return v;
    }

    private static VehicleRequest request() {
        return new VehicleRequest("V-001", "ABC-123", null);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("create")
    class Create {
        @Test @DisplayName("creates a group + first version stamped with the tenant company")
        void createsGroupAndFirstVersion() {
            when(vehicleGroupRepository.save(any(VehicleGroup.class))).thenAnswer(i -> i.getArgument(0));
            when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

            Vehicle result = sut.create(request());

            assertThat(result.getCompany().getId()).isEqualTo(COMPANY_ID);
            assertThat(result.getGroup()).isNotNull();
            assertThat(result.getIsCurrent()).isTrue();
            assertThat(result.getVersionTo()).isNull();
            verify(vehicleGroupRepository).save(any(VehicleGroup.class));
            verify(vehicleRepository).save(any(Vehicle.class));
        }

        @Test @DisplayName("resolves owner by group id when ownerId is provided")
        void resolvesOwnerByGroupId() {
            Person owner = new Person("D", "O", null, "W", null);
            when(vehicleGroupRepository.save(any(VehicleGroup.class))).thenAnswer(i -> i.getArgument(0));
            when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));
            when(personService.findByGroupId(7L)).thenReturn(owner);

            Vehicle result = sut.create(new VehicleRequest("V-002", "XYZ-9", 7L));

            assertThat(result.getOwner()).isSameAs(owner);
            verify(personService).findByGroupId(7L);
        }
    }

    // ── findByGroupId ──────────────────────────────────────────────────────────

    @Nested @DisplayName("findByGroupId")
    class FindByGroupId {
        @Test @DisplayName("returns current version (active or deactivated) so the UI can offer reactivation")
        void found() {
            Vehicle v = vehicle();
            when(vehicleRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(v));
            assertThat(sut.findByGroupId(GROUP_ID)).isSameAs(v);
        }

        @Test @DisplayName("throws NoSuchElementException when no current version exists")
        void notFound() {
            when(vehicleRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findByGroupId(GROUP_ID))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(GROUP_ID.toString());
        }
    }

    // ── update ──────────────────────────────────────────────────────────────────

    @Nested @DisplayName("update")
    class Update {
        @Test @DisplayName("closes current version and opens a new one preserving the group")
        void closesAndOpensVersion() {
            Vehicle current = vehicle();
            when(vehicleRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));
            when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

            Vehicle next = sut.update(GROUP_ID, request());

            assertThat(current.getIsCurrent()).isFalse();
            assertThat(current.getVersionTo()).isNotNull();
            assertThat(next.getIsCurrent()).isTrue();
            assertThat(next.getGroup()).isSameAs(current.getGroup());
            verify(vehicleRepository, times(2)).save(any(Vehicle.class));
        }

        @Test @DisplayName("throws when group has no current version")
        void notFound() {
            when(vehicleRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.empty());
            VehicleRequest req = request();
            assertThatThrownBy(() -> sut.update(GROUP_ID, req))
                    .isInstanceOf(NoSuchElementException.class);
            verify(vehicleRepository, never()).save(any());
        }
    }

    // ── deactivate ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("deactivate")
    class Deactivate {
        @Test @DisplayName("soft-deletes the current version")
        void delegates() {
            Vehicle current = vehicle();
            when(vehicleRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));

            sut.deactivate(GROUP_ID);

            verify(vehicleRepository).softDelete(current);
        }

        @Test @DisplayName("throws when group has no current version")
        void notFound() {
            when(vehicleRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.deactivate(GROUP_ID))
                    .isInstanceOf(NoSuchElementException.class);
            verify(vehicleRepository, never()).softDelete(any());
        }
    }

    // ── reactivate ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("reactivate")
    class Reactivate {
        @Test @DisplayName("closes last version and opens a new active version copying data")
        void createsNewActiveVersion() {
            Vehicle last = vehicle();
            last.setActive(false);
            when(vehicleRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(last));
            when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

            Vehicle next = sut.reactivate(GROUP_ID);

            assertThat(last.getIsCurrent()).isFalse();
            assertThat(next.getActive()).isTrue();
            assertThat(next.getIsCurrent()).isTrue();
            assertThat(next.getVehicleNumber()).isEqualTo("V-001");
            assertThat(next.getLicensePlate()).isEqualTo("ABC-123");
            assertThat(next.getGroup()).isSameAs(last.getGroup());
        }
    }

    // ── findByNumber ──────────────────────────────────────────────────────────

    @Nested @DisplayName("findByNumber")
    class FindByNumber {
        @Test @DisplayName("returns Optional with vehicle when found and active")
        void found() {
            Vehicle v = vehicle();
            when(vehicleRepository.findOne(any(Specification.class))).thenReturn(Optional.of(v));
            assertThat(sut.findByNumber("V-001")).contains(v);
        }

        @Test @DisplayName("returns empty Optional when not found")
        void empty() {
            when(vehicleRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThat(sut.findByNumber("NONE")).isEmpty();
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("returns all current versions scoped to the tenant")
        void returnsAllCurrent() {
            List<Vehicle> list = List.of(vehicle());
            when(vehicleRepository.findAllCurrentByCompany(COMPANY_ID)).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
            verify(vehicleRepository).findAllCurrentByCompany(COMPANY_ID);
        }
    }

    // ── filter ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("delegates to repo with tenant + active Specification")
        void scopedFilter() {
            FilterRequest req  = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<Vehicle> page = new PageImpl<>(List.of(vehicle()));
            when(vehicleRepository.filter(eq(req), any(), any(Specification.class))).thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }
}
