package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ConflictException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.ScheduleTemplateVersionRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.ScheduleTemplateVersion;
import com.example.tgs_dev.repository.ScheduleTemplateVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleTemplateVersionService")
class ScheduleTemplateVersionServiceTest {

    @Mock ScheduleTemplateVersionRepository repository;
    @Mock ScheduleTemplateService           templateService;
    @Mock TenantService                     tenantService;

    ScheduleTemplateVersionService sut;

    private static final Company         COMPANY  = company(1, "Corp");
    private static final Route           ROUTE    = route(10, "R-10");
    private static final ScheduleTemplate TEMPLATE = template(5, ROUTE, LocalTime.of(6, 0));
    private static final LocalDate        FROM     = LocalDate.of(2024, 1, 15);
    private static final LocalDate        TO       = LocalDate.of(2024, 11, 29);
    private static final LocalTime        T_07_00  = LocalTime.of(7, 0);

    @BeforeEach
    void setUp() {
        sut = new ScheduleTemplateVersionService(repository, templateService, tenantService);
        lenient().when(tenantService.currentCompany()).thenReturn(COMPANY);
        lenient().when(tenantService.currentCompanyId()).thenReturn(1);
        lenient().when(templateService.findById(5)).thenReturn(TEMPLATE);
    }

    // ── findAllByTemplate ─────────────────────────────────────────────────────

    @Nested @DisplayName("findAllByTemplate")
    class FindAllByTemplate {

        @Test @DisplayName("delegates to repository with template and companyId")
        void delegates() {
            ScheduleTemplateVersion v = templateVersion(1, TEMPLATE, T_07_00, FROM, TO);
            when(repository.findAllByTemplateAndCompany(TEMPLATE, 1)).thenReturn(List.of(v));

            assertThat(sut.findAllByTemplate(5)).containsExactly(v);
        }

        @Test @DisplayName("returns empty list when template has no versions")
        void empty() {
            when(repository.findAllByTemplateAndCompany(TEMPLATE, 1)).thenReturn(List.of());
            assertThat(sut.findAllByTemplate(5)).isEmpty();
        }
    }

    // ── findActiveForDate ─────────────────────────────────────────────────────

    @Nested @DisplayName("findActiveForDate")
    class FindActiveForDate {

        @Test @DisplayName("returns version from repository when found")
        void found() {
            ScheduleTemplateVersion v = templateVersion(1, TEMPLATE, T_07_00, FROM, TO);
            when(repository.findActiveForDate(TEMPLATE, 1, FROM)).thenReturn(Optional.of(v));

            assertThat(sut.findActiveForDate(TEMPLATE, 1, FROM)).contains(v);
        }

        @Test @DisplayName("returns empty when no version covers the date")
        void notFound() {
            when(repository.findActiveForDate(TEMPLATE, 1, FROM)).thenReturn(Optional.empty());
            assertThat(sut.findActiveForDate(TEMPLATE, 1, FROM)).isEmpty();
        }
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("create")
    class Create {

        private ScheduleTemplateVersionRequest req(LocalDate from, LocalDate to) {
            return new ScheduleTemplateVersionRequest("Horario vacacional", T_07_00, from, to);
        }

        @Test @DisplayName("persists and returns new version when no overlap")
        void success() {
            when(repository.findOverlapping(eq(TEMPLATE), eq(1), any(), any(), eq(-1)))
                    .thenReturn(List.of());
            ScheduleTemplateVersion saved = templateVersion(1, TEMPLATE, T_07_00, FROM, TO);
            when(repository.save(any())).thenReturn(saved);

            ScheduleTemplateVersion result = sut.create(5, req(FROM, TO));

            assertThat(result).isEqualTo(saved);
            verify(repository).save(any(ScheduleTemplateVersion.class));
        }

        @Test @DisplayName("throws ConflictException when date range overlaps existing version")
        void overlap_throws() {
            ScheduleTemplateVersion existing = templateVersion(3, TEMPLATE, T_07_00, FROM, TO);
            when(repository.findOverlapping(eq(TEMPLATE), eq(1), any(), any(), eq(-1)))
                    .thenReturn(List.of(existing));

            ScheduleTemplateVersionRequest request = req(FROM, TO);
            assertThatThrownBy(() -> sut.create(5, request))
                    .isInstanceOf(ConflictException.class);
            verify(repository, never()).save(any());
        }

        @Test @DisplayName("throws BusinessException when effectiveTo is before effectiveFrom")
        void invalidDateRange_throws() {
            ScheduleTemplateVersionRequest request = req(TO, FROM);
            assertThatThrownBy(() -> sut.create(5, request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test @DisplayName("allows null effectiveTo (open-ended version)")
        void openEnded_success() {
            when(repository.findOverlapping(eq(TEMPLATE), eq(1), any(), isNull(), eq(-1)))
                    .thenReturn(List.of());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> sut.create(5, req(FROM, null))).doesNotThrowAnyException();
        }
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("update")
    class Update {

        private ScheduleTemplateVersionRequest req() {
            return new ScheduleTemplateVersionRequest("Updated", LocalTime.of(8, 0), FROM, TO);
        }

        @Test @DisplayName("updates fields and saves when no overlap")
        void success() {
            ScheduleTemplateVersion existing = templateVersion(1, TEMPLATE, T_07_00, FROM, TO);
            when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
            when(repository.findOverlapping(eq(TEMPLATE), eq(1), any(), any(), eq(1)))
                    .thenReturn(List.of());
            when(repository.save(existing)).thenReturn(existing);

            ScheduleTemplateVersion result = sut.update(5, 1, req());

            assertThat(result.getLabel()).isEqualTo("Updated");
            assertThat(result.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        }

        @Test @DisplayName("throws ResourceNotFoundException when version not found")
        void notFound_throws() {
            when(repository.findOne(any(Specification.class))).thenReturn(Optional.empty());
            ScheduleTemplateVersionRequest request = req();
            assertThatThrownBy(() -> sut.update(5, 99, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("delete")
    class Delete {

        @Test @DisplayName("soft-deletes the version when found")
        void success() {
            ScheduleTemplateVersion v = templateVersion(1, TEMPLATE, T_07_00, FROM, TO);
            when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(v));

            sut.delete(5, 1);

            verify(repository).softDelete(v);
        }

        @Test @DisplayName("throws ResourceNotFoundException when version not found")
        void notFound_throws() {
            when(repository.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.delete(5, 99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
