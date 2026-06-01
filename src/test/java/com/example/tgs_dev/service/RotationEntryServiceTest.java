package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.repository.RotationEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.example.tgs_dev.TestFixtures.company;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RotationEntryService")
class RotationEntryServiceTest {

    @Mock RotationEntryRepository repo;
    @Mock TenantService           tenantService;
    @InjectMocks RotationEntryService sut;

    private static final int     COMPANY_ID = 1;
    private static final Company COMPANY    = company(COMPANY_ID, "Test Corp");

    @BeforeEach
    void stubTenant() {
        lenient().when(tenantService.currentCompanyId()).thenReturn(COMPANY_ID);
        lenient().when(tenantService.currentCompany()).thenReturn(COMPANY);
    }

    private VehicleRotation rotation() {
        VehicleRotation r = new VehicleRotation(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
                true, ShiftDayType.BUSINESS_DAYS);
        r.setId(1);
        return r;
    }

    private RotationEntry entry(int id) {
        RotationEntry e = new RotationEntry();
        e.setId(id);
        return e;
    }

    @Nested @DisplayName("save")
    class Save {
        @Test @DisplayName("sets company and delegates to repo")
        void setsCompanyAndDelegates() {
            RotationEntry e = entry(1);
            when(repo.save(e)).thenReturn(e);

            RotationEntry result = sut.save(e);

            assertThat(result.getCompany()).isEqualTo(COMPANY);
            verify(repo).save(e);
        }
    }

    @Nested @DisplayName("saveAll")
    class SaveAll {
        @Test @DisplayName("sets rotation and company on every entry before persisting")
        void setsRotationAndCompanyOnEntries() {
            VehicleRotation r = rotation();
            RotationEntry e1 = entry(1);
            RotationEntry e2 = entry(2);
            when(repo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            sut.saveAll(r, List.of(e1, e2));

            assertThat(e1.getVehicleRotation()).isSameAs(r);
            assertThat(e2.getVehicleRotation()).isSameAs(r);
            assertThat(e1.getCompany()).isEqualTo(COMPANY);
            assertThat(e2.getCompany()).isEqualTo(COMPANY);
        }

        @Test @DisplayName("returns saved entries from repo")
        void returnsSaved() {
            VehicleRotation r = rotation();
            RotationEntry e = entry(1);
            when(repo.saveAll(any())).thenReturn(List.of(e));
            assertThat(sut.saveAll(r, List.of(e))).containsExactly(e);
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("returns Optional from repo using tenant-scoped spec")
        void found() {
            RotationEntry e = entry(1);
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(e));
            assertThat(sut.findById(1)).contains(e);
        }

        @Test @DisplayName("returns empty when not found")
        void notFound() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThat(sut.findById(99)).isEmpty();
        }
    }

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("scopes to current tenant")
        void scopedToTenant() {
            List<RotationEntry> list = List.of(entry(1));
            when(repo.findAll(any(Specification.class))).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
            verify(tenantService).currentCompanyId();
        }
    }

    @Nested @DisplayName("delete / deleteAll")
    class Delete {
        @Test @DisplayName("delete delegates to repo.delete")
        void deleteSingle() {
            RotationEntry e = entry(1);
            sut.delete(e);
            verify(repo).delete(e);
        }

        @Test @DisplayName("deleteAll delegates to repo.deleteAll")
        void deleteAll() {
            List<RotationEntry> list = List.of(entry(1), entry(2));
            sut.deleteAll(list);
            verify(repo).deleteAll(list);
        }
    }

    @Nested @DisplayName("findByRotation")
    class FindByRotation {
        /**
         * The service now delegates to {@code findByRotationEager} — a single JOIN
         * FETCH query that loads all associations and orders by {@code listPosition ASC}
         * in the JPQL itself.  No Sort parameter is needed at the Java level.
         */
        @Test @DisplayName("delegates to findByRotationEager with the correct rotation")
        void queriesRepo() {
            VehicleRotation r = rotation();
            List<RotationEntry> list = List.of(entry(1));
            when(repo.findByRotationEager(r)).thenReturn(list);

            List<RotationEntry> result = sut.findByRotation(r);

            assertThat(result).isSameAs(list);
            verify(repo).findByRotationEager(r);
        }
    }
}
