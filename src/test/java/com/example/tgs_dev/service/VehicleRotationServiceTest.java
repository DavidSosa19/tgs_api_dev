package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.repository.VehicleRotationRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
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
    @InjectMocks VehicleRotationService sut;

    private VehicleRotation rotation() {
        VehicleRotation r = new VehicleRotation(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
                true, ShiftDayType.BUSINESS_DAYS);
        r.setId(1);
        return r;
    }

    // ── CRUD delegation ───────────────────────────────────────────────────────

    @Nested @DisplayName("save")
    class Save {
        @Test @DisplayName("delegates to repo")
        void delegates() {
            VehicleRotation r = rotation();
            when(repo.save(r)).thenReturn(r);
            assertThat(sut.save(r)).isSameAs(r);
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("returns entity when found")
        void found() {
            VehicleRotation r = rotation();
            when(repo.findById(1)).thenReturn(Optional.of(r));
            assertThat(sut.findById(1)).isSameAs(r);
        }

        @Test @DisplayName("throws NoSuchElementException when not found")
        void notFound() {
            when(repo.findById(99)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested @DisplayName("delete")
    class Delete {
        @Test @DisplayName("calls softDelete on repo")
        void delegates() {
            VehicleRotation r = rotation();
            sut.delete(r);
            verify(repo).softDelete(r);
        }
    }

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("delegates to repo")
        void delegates() {
            List<VehicleRotation> list = List.of(rotation());
            when(repo.findAll()).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
        }
    }

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("delegates to repo with pageable")
        void delegates() {
            FilterRequest req = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<VehicleRotation> page = new PageImpl<>(List.of(rotation()));
            when(repo.filter(eq(req), any())).thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }

    // ── getBusinessDays ───────────────────────────────────────────────────────

    @Nested @DisplayName("getBusinessDays")
    class GetBusinessDays {

        @Test @DisplayName("same date or earlier target → 0")
        void sameOrBefore_returnsZero() {
            LocalDate base = LocalDate.of(2024, 1, 15); // Monday
            assertThat(sut.getBusinessDays(base, base)).isZero();
            assertThat(sut.getBusinessDays(base, base.minusDays(1))).isZero();
        }

        @ParameterizedTest(name = "Mon→{0} day(s) ahead = {1} business days")
        @CsvSource({
            "1, 1",   // Mon → Tue
            "2, 2",   // Mon → Wed
            "4, 4",   // Mon → Fri
            "5, 4",   // Mon → Sat (weekend skipped)
            "7, 5",   // Mon → next Mon
        })
        @DisplayName("counts only Mon-Fri between dates")
        void countsWeekdays(int daysAhead, int expectedBusinessDays) {
            LocalDate monday = LocalDate.of(2024, 1, 15); // confirmed Monday
            assertThat(sut.getBusinessDays(monday, monday.plusDays(daysAhead)))
                    .isEqualTo(expectedBusinessDays);
        }
    }

    // ── rotatePositions ───────────────────────────────────────────────────────

    @Nested @DisplayName("rotatePositions")
    class RotatePositions {

        @Test @DisplayName("empty list → returns empty list")
        void emptyList() {
            assertThat(sut.rotatePositions(List.of(), 3)).isEmpty();
        }

        @Test @DisplayName("offset 0 → returns same order copy")
        void zeroOffset_returnsCopy() {
            List<String> base = List.of("A", "B", "C");
            assertThat(sut.rotatePositions(base, 0)).containsExactly("A", "B", "C");
        }

        @Test @DisplayName("offset wraps modulo list size")
        void offsetWrapsModulo() {
            List<String> base = List.of("A", "B", "C");
            // offset 4 % 3 = 1 → last 1 element first: C, A, B
            assertThat(sut.rotatePositions(base, 4)).containsExactly("C", "A", "B");
        }

        @Test @DisplayName("offset 1 on 3-element list → last element moves to front")
        void offsetOne() {
            List<String> base = List.of("A", "B", "C");
            assertThat(sut.rotatePositions(base, 1)).containsExactly("C", "A", "B");
        }

        @Test @DisplayName("offset equal to list size → same order")
        void offsetEqualsSize() {
            List<String> base = List.of("A", "B", "C");
            assertThat(sut.rotatePositions(base, 3)).containsExactly("A", "B", "C");
        }
    }

    // ── getRotationFromDate ───────────────────────────────────────────────────

    @Nested @DisplayName("getRotationFromDate")
    class GetRotationFromDate {

        private RotationEntry entryWithTime(LocalTime time) {
            Route route = new Route("R-1", 30, 2);
            ScheduleTemplate t = new ScheduleTemplate(route, "T", "T", time);
            Vehicle v = new Vehicle("V-1", null);
            RotationEntry e = new RotationEntry();
            e.setScheduleTemplate(t);
            e.setVehicle(v);
            return e;
        }

        @Test @DisplayName("throws NoSuchElementException when no rotation matches")
        void noRotationFound_throws() {
            LocalDate date = LocalDate.of(2024, 1, 15);
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, date))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test @DisplayName("returns entries rotated by business-day offset from startDate")
        void returnsRotatedEntries() {
            // startDate=Mon Jan 15, targetDate=Tue Jan 16 → 1 business day offset
            LocalDate startDate  = LocalDate.of(2024, 1, 15); // Monday
            LocalDate targetDate = LocalDate.of(2024, 1, 16); // Tuesday

            RotationEntry e1 = entryWithTime(LocalTime.of(6, 0));
            RotationEntry e2 = entryWithTime(LocalTime.of(7, 0));
            RotationEntry e3 = entryWithTime(LocalTime.of(8, 0));

            VehicleRotation r = new VehicleRotation(startDate, LocalDate.of(2024, 1, 31),
                    true, ShiftDayType.BUSINESS_DAYS);
            r.setEntries(new ArrayList<>(List.of(e1, e2, e3)));

            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(r));

            List<RotationEntry> result = sut.getRotationFromDate(
                    ShiftDayType.BUSINESS_DAYS, targetDate);

            // offset=1, size=3 → last 1 moves to front: [e3, e1, e2]
            assertThat(result).hasSize(3);
            assertThat(result.getFirst().getScheduleTemplate().getStartTime())
                    .isEqualTo(LocalTime.of(8, 0));
        }
    }
}
