package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.repository.ScheduleTemplateRepository;
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

import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.example.tgs_dev.TestFixtures.company;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleTemplateService")
class ScheduleTemplateServiceTest {

    @Mock ScheduleTemplateRepository repo;
    @Mock TenantService              tenantService;
    @InjectMocks ScheduleTemplateService sut;

    private static final int     COMPANY_ID = 1;
    private static final Company COMPANY    = company(COMPANY_ID, "Test Corp");

    @BeforeEach
    void stubTenant() {
        lenient().when(tenantService.currentCompanyId()).thenReturn(COMPANY_ID);
        lenient().when(tenantService.currentCompany()).thenReturn(COMPANY);
    }

    private ScheduleTemplate template() {
        Route r = new Route("R-1", 30, 3);
        ScheduleTemplate t = new ScheduleTemplate(r, "T-01", "Morning", LocalTime.of(6, 0));
        t.setId(1);
        return t;
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Nested @DisplayName("save")
    class Save {
        @Test @DisplayName("sets company from TenantService before persisting")
        void setsCompany() {
            ScheduleTemplate t = template();
            when(repo.save(t)).thenReturn(t);

            ScheduleTemplate result = sut.save(t);

            assertThat(result.getCompany()).isEqualTo(COMPANY);
            verify(repo).save(t);
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("returns entity when found")
        void found() {
            ScheduleTemplate t = template();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(t));
            assertThat(sut.findById(1)).isSameAs(t);
        }

        @Test @DisplayName("throws NoSuchElementException when not found")
        void notFound() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("99");
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("delegates to repo with tenant specification")
        void scopedToTenant() {
            List<ScheduleTemplate> list = List.of(template());
            when(repo.findAll(any(Specification.class))).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
            verify(tenantService).currentCompanyId();
        }
    }

    // ── findByNumber ──────────────────────────────────────────────────────────

    @Nested @DisplayName("findByNumber")
    class FindByNumber {
        @Test @DisplayName("returns Optional from repo with tenant specification")
        void found() {
            ScheduleTemplate t = template();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(t));
            assertThat(sut.findByNumber("T-01")).contains(t);
        }

        @Test @DisplayName("returns empty when not found")
        void empty() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThat(sut.findByNumber("NONE")).isEmpty();
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("delete")
    class Delete {
        @Test @DisplayName("calls softDelete on repo")
        void delegates() {
            ScheduleTemplate t = template();
            sut.delete(t);
            verify(repo).softDelete(t);
        }
    }

    // ── filter ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("delegates to 3-arg repo.filter with tenant specification")
        void scopedFilter() {
            FilterRequest req  = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<ScheduleTemplate> page = new PageImpl<>(List.of(template()));
            when(repo.filter(eq(req), any(), any(Specification.class))).thenReturn(page);

            assertThat(sut.filter(req)).isSameAs(page);
            verify(tenantService).currentCompanyId();
        }
    }
}
