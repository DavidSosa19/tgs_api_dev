package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.repository.VehicleRotationRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VehicleRotationService")
class VehicleRotationServiceTest {

    @Mock VehicleRotationRepository repo;
    @Mock TenantService             tenantService;
    @InjectMocks VehicleRotationService sut;

    private static final int COMPANY_ID = 1;

    private Company company() {
        Company c = new Company("ACME", "NIT-1");
        c.setId(COMPANY_ID);
        return c;
    }

    private VehicleRotation rotation() {
        VehicleRotation r = new VehicleRotation(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
                true, ShiftDayType.BUSINESS_DAYS);
        r.setId(1);
        return r;
    }

    @BeforeEach
    void stubTenant() {
        lenient().when(tenantService.currentCompanyId()).thenReturn(COMPANY_ID);
        lenient().when(tenantService.currentCompany()).thenReturn(company());
    }

    // ── CRUD delegation ───────────────────────────────────────────────────────

    @Nested @DisplayName("save")
    class Save {
        @Test @DisplayName("stampa la empresa del tenant y delega en repo")
        void stampsTenantCompanyAndSaves() {
            VehicleRotation r = rotation();
            when(repo.save(r)).thenReturn(r);

            VehicleRotation result = sut.save(r);

            assertThat(result).isSameAs(r);
            assertThat(r.getCompany().getId()).isEqualTo(COMPANY_ID);
            verify(repo).save(r);
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("retorna entidad cuando existe en el tenant")
        void found() {
            VehicleRotation r = rotation();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(r));
            assertThat(sut.findById(1)).isSameAs(r);
        }

        @Test @DisplayName("lanza NoSuchElementException cuando no existe")
        void notFound() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested @DisplayName("delete")
    class Delete {
        @Test @DisplayName("delega softDelete al repositorio")
        void delegates() {
            VehicleRotation r = rotation();
            sut.delete(r);
            verify(repo).softDelete(r);
        }
    }

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("filtra por tenant y retorna lista")
        void filtersByTenant() {
            List<VehicleRotation> list = List.of(rotation());
            when(repo.findAll(any(Specification.class))).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
        }
    }

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("llama al repo con la spec de tenant adicional")
        void delegates() {
            FilterRequest req = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<VehicleRotation> page = new PageImpl<>(List.of(rotation()));
            when(repo.filter(eq(req), any(), any(Specification.class))).thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }

    // ── getBusinessDays ───────────────────────────────────────────────────────

    @Nested @DisplayName("getBusinessDays")
    class GetBusinessDays {

        @Test @DisplayName("misma fecha o target anterior → 0")
        void sameOrBefore_returnsZero() {
            LocalDate base = LocalDate.of(2024, 1, 15); // Monday
            assertThat(sut.getBusinessDays(base, base)).isZero();
            assertThat(sut.getBusinessDays(base, base.minusDays(1))).isZero();
        }

        @ParameterizedTest(name = "Mon→{0} día(s) adelante = {1} días hábiles")
        @CsvSource({
            "1, 1",   // Mon → Tue
            "2, 2",   // Mon → Wed
            "4, 4",   // Mon → Fri
            "5, 4",   // Mon → Sat (weekend skipped)
            "7, 5",   // Mon → next Mon
        })
        @DisplayName("cuenta solo lunes-viernes entre fechas")
        void countsWeekdays(int daysAhead, int expectedBusinessDays) {
            LocalDate monday = LocalDate.of(2024, 1, 15);
            assertThat(sut.getBusinessDays(monday, monday.plusDays(daysAhead)))
                    .isEqualTo(expectedBusinessDays);
        }
    }

    // ── rotatePositions ───────────────────────────────────────────────────────

    @Nested @DisplayName("rotatePositions")
    class RotatePositions {

        @Test @DisplayName("lista vacía → retorna lista vacía")
        void emptyList() {
            assertThat(sut.rotatePositions(List.of(), 3)).isEmpty();
        }

        @Test @DisplayName("offset 0 → retorna copia en el mismo orden")
        void zeroOffset_returnsCopy() {
            List<String> base = List.of("A", "B", "C");
            assertThat(sut.rotatePositions(base, 0)).containsExactly("A", "B", "C");
        }

        @Test @DisplayName("offset > size → aplica módulo")
        void offsetWrapsModulo() {
            List<String> base = List.of("A", "B", "C");
            // offset 4 % 3 = 1 → C, A, B
            assertThat(sut.rotatePositions(base, 4)).containsExactly("C", "A", "B");
        }

        @Test @DisplayName("offset 1 sobre lista de 3 → último elemento al frente")
        void offsetOne() {
            List<String> base = List.of("A", "B", "C");
            assertThat(sut.rotatePositions(base, 1)).containsExactly("C", "A", "B");
        }

        @Test @DisplayName("offset igual a tamaño → mismo orden")
        void offsetEqualsSize() {
            List<String> base = List.of("A", "B", "C");
            assertThat(sut.rotatePositions(base, 3)).containsExactly("A", "B", "C");
        }
    }

    // ── getRotationFromDate ───────────────────────────────────────────────────

    @Nested @DisplayName("getRotationFromDate")
    class GetRotationFromDate {

        // startDate=Mon Jan 15 → targetDate=Tue Jan 16 is 1 business day ahead
        private static final LocalDate START   = LocalDate.of(2024, 1, 15); // Monday
        private static final LocalDate DAY_0   = LocalDate.of(2024, 1, 15); // offset=0
        private static final LocalDate DAY_1   = LocalDate.of(2024, 1, 16); // offset=1
        private static final LocalDate DAY_2   = LocalDate.of(2024, 1, 17); // offset=2

        /** Builds a VehicleRotation whose entries are in the given list. */
        private VehicleRotation rotation(List<RotationEntry> entries) {
            VehicleRotation r = new VehicleRotation(START, LocalDate.of(2024, 1, 31),
                    true, ShiftDayType.BUSINESS_DAYS);
            r.setEntries(new ArrayList<>(entries));
            return r;
        }

        /** Creates a RotationEntry with a distinct vehicle and a template at {@code time}. */
        private RotationEntry entry(String vehiclePlate, LocalTime time) {
            Route route = new Route("");
            ScheduleTemplate t = new ScheduleTemplate(route, "T-" + time, "T-" + time, time);
            Vehicle v = new Vehicle(vehiclePlate, null);
            RotationEntry e = new RotationEntry();
            e.setScheduleTemplate(t);
            e.setVehicle(v);
            return e;
        }

        @Test @DisplayName("lanza NoSuchElementException cuando no existe rotación")
        void noRotationFound_throws() {
            when(repo.findByDateAndTypeEager(DAY_1, ShiftDayType.BUSINESS_DAYS, COMPANY_ID))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, DAY_1))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test @DisplayName("offset=0 (día de inicio): cada vehículo ocupa su slot original")
        void offsetZero_vehiclesInOriginalSlots() {
            // Entries deliberately added in reverse time order to prove the sort happens
            RotationEntry eC = entry("V-C", LocalTime.of(10, 0)); // latest
            RotationEntry eB = entry("V-B", LocalTime.of(8,  0));
            RotationEntry eA = entry("V-A", LocalTime.of(6,  0)); // earliest

            when(repo.findByDateAndTypeEager(DAY_0, ShiftDayType.BUSINESS_DAYS, COMPANY_ID))
                    .thenReturn(Optional.of(rotation(List.of(eC, eB, eA))));

            List<RotationEntry> result = sut.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, DAY_0);

            // Templates are sorted ascending: 6:00, 8:00, 10:00
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getScheduleTemplate().getStartTime()).isEqualTo(LocalTime.of(6,  0));
            assertThat(result.get(1).getScheduleTemplate().getStartTime()).isEqualTo(LocalTime.of(8,  0));
            assertThat(result.get(2).getScheduleTemplate().getStartTime()).isEqualTo(LocalTime.of(10, 0));
            // Vehicles match their original (offset=0) template slots
            assertThat(result.get(0).getVehicle().getVehicleNumber()).isEqualTo("V-A");
            assertThat(result.get(1).getVehicle().getVehicleNumber()).isEqualTo("V-B");
            assertThat(result.get(2).getVehicle().getVehicleNumber()).isEqualTo("V-C");
        }

        @Test @DisplayName("offset=1: sólo los vehículos rotan, las cartulinas mantienen su hora")
        void offsetOne_vehiclesRotate_templatesStayInPosition() {
            RotationEntry eA = entry("V-A", LocalTime.of(6,  0));
            RotationEntry eB = entry("V-B", LocalTime.of(8,  0));
            RotationEntry eC = entry("V-C", LocalTime.of(10, 0));

            when(repo.findByDateAndTypeEager(DAY_1, ShiftDayType.BUSINESS_DAYS, COMPANY_ID))
                    .thenReturn(Optional.of(rotation(List.of(eA, eB, eC))));

            List<RotationEntry> result = sut.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, DAY_1);

            // Templates are UNCHANGED: slot 0 = 6:00, slot 1 = 8:00, slot 2 = 10:00
            assertThat(result.get(0).getScheduleTemplate().getStartTime()).isEqualTo(LocalTime.of(6,  0));
            assertThat(result.get(1).getScheduleTemplate().getStartTime()).isEqualTo(LocalTime.of(8,  0));
            assertThat(result.get(2).getScheduleTemplate().getStartTime()).isEqualTo(LocalTime.of(10, 0));
            // Vehicles rotated by 1: V-C (was last) now fills the earliest slot
            assertThat(result.get(0).getVehicle().getVehicleNumber()).isEqualTo("V-C");
            assertThat(result.get(1).getVehicle().getVehicleNumber()).isEqualTo("V-A");
            assertThat(result.get(2).getVehicle().getVehicleNumber()).isEqualTo("V-B");
        }

        @Test @DisplayName("offset=2: vehículos rotan dos posiciones, cartulinas siguen ordenadas")
        void offsetTwo_vehiclesRotateTwoPositions() {
            RotationEntry eA = entry("V-A", LocalTime.of(6,  0));
            RotationEntry eB = entry("V-B", LocalTime.of(8,  0));
            RotationEntry eC = entry("V-C", LocalTime.of(10, 0));

            when(repo.findByDateAndTypeEager(DAY_2, ShiftDayType.BUSINESS_DAYS, COMPANY_ID))
                    .thenReturn(Optional.of(rotation(List.of(eA, eB, eC))));

            List<RotationEntry> result = sut.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, DAY_2);

            // Templates unchanged
            assertThat(result.get(0).getScheduleTemplate().getStartTime()).isEqualTo(LocalTime.of(6,  0));
            assertThat(result.get(1).getScheduleTemplate().getStartTime()).isEqualTo(LocalTime.of(8,  0));
            assertThat(result.get(2).getScheduleTemplate().getStartTime()).isEqualTo(LocalTime.of(10, 0));
            // Vehicles rotated by 2: V-B fills slot 0, V-C fills slot 1, V-A fills slot 2
            assertThat(result.get(0).getVehicle().getVehicleNumber()).isEqualTo("V-B");
            assertThat(result.get(1).getVehicle().getVehicleNumber()).isEqualTo("V-C");
            assertThat(result.get(2).getVehicle().getVehicleNumber()).isEqualTo("V-A");
        }

        @Test @DisplayName("entradas desordenadas en BD son ordenadas por hora antes de rotar")
        void entriesOutOfOrder_areSortedBeforeRotation() {
            // The entries arrive from DB in arbitrary order (e.g. by PK or insertion)
            RotationEntry eC = entry("V-C", LocalTime.of(10, 0)); // added first but latest
            RotationEntry eA = entry("V-A", LocalTime.of(6,  0));
            RotationEntry eB = entry("V-B", LocalTime.of(8,  0));

            when(repo.findByDateAndTypeEager(DAY_1, ShiftDayType.BUSINESS_DAYS, COMPANY_ID))
                    .thenReturn(Optional.of(rotation(List.of(eC, eA, eB))));

            List<RotationEntry> result = sut.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, DAY_1);

            // Result is always time-sorted regardless of input order
            assertThat(result)
                    .extracting(e -> e.getScheduleTemplate().getStartTime())
                    .containsExactly(LocalTime.of(6, 0), LocalTime.of(8, 0), LocalTime.of(10, 0));
        }

        @Test @DisplayName("resultado siempre contiene el mismo número de entradas que la rotación original")
        void resultSizeMatchesInputSize() {
            List<RotationEntry> entries = List.of(
                    entry("V-A", LocalTime.of(6, 0)),
                    entry("V-B", LocalTime.of(8, 0)));

            when(repo.findByDateAndTypeEager(DAY_1, ShiftDayType.BUSINESS_DAYS, COMPANY_ID))
                    .thenReturn(Optional.of(rotation(entries)));

            assertThat(sut.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, DAY_1)).hasSize(2);
        }
    }
}
