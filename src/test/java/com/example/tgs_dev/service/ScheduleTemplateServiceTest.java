package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.request.ScheduleTemplateRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.ScheduleTemplateGroup;
import com.example.tgs_dev.repository.ScheduleTemplateGroupRepository;
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

/**
 * Unit tests for {@link ScheduleTemplateService} — SCD Type-2 behaviour.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleTemplateService")
class ScheduleTemplateServiceTest {

    @Mock ScheduleTemplateRepository      scheduleTemplateRepository;
    @Mock ScheduleTemplateGroupRepository scheduleTemplateGroupRepository;
    @Mock TenantService                   tenantService;
    @InjectMocks ScheduleTemplateService sut;

    private static final int     COMPANY_ID = 1;
    private static final Long    GROUP_ID   = 50L;
    private static final Company COMPANY    = company(COMPANY_ID, "Test Corp");

    @BeforeEach
    void setUp() {
        lenient().when(tenantService.currentCompanyId()).thenReturn(COMPANY_ID);
        lenient().when(tenantService.currentCompany()).thenReturn(COMPANY);
    }

    private static Route route() {
        Route r = new Route("R-01");
        r.setId(10);
        return r;
    }

    private static ScheduleTemplateGroup group() {
        ScheduleTemplateGroup g = new ScheduleTemplateGroup(COMPANY, "T-01");
        g.setId(GROUP_ID);
        return g;
    }

    private static ScheduleTemplate template() {
        ScheduleTemplate t = new ScheduleTemplate(route(), "T-01", "Morning", LocalTime.of(6, 0));
        t.setId(1);
        t.setCompany(COMPANY);
        t.setGroup(group());
        t.setIsCurrent(true);
        return t;
    }

    private static ScheduleTemplateRequest request() {
        return new ScheduleTemplateRequest(100L, null, "T-01", "Morning", LocalTime.of(6, 0), null);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("create")
    class Create {
        @Test @DisplayName("creates a group + first version with the resolved route")
        void createsGroupAndFirstVersion() {
            Route route = route();
            when(scheduleTemplateGroupRepository.save(any(ScheduleTemplateGroup.class)))
                    .thenAnswer(i -> i.getArgument(0));
            when(scheduleTemplateRepository.save(any(ScheduleTemplate.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ScheduleTemplate result = sut.create(request(), route, null);

            assertThat(result.getCompany()).isEqualTo(COMPANY);
            assertThat(result.getGroup()).isNotNull();
            assertThat(result.getRoute()).isSameAs(route);
            assertThat(result.getIsCurrent()).isTrue();
            verify(scheduleTemplateGroupRepository).save(any(ScheduleTemplateGroup.class));
            verify(scheduleTemplateRepository).save(any(ScheduleTemplate.class));
        }
    }

    // ── findByGroupId ──────────────────────────────────────────────────────────

    @Nested @DisplayName("findByGroupId")
    class FindByGroupId {
        @Test @DisplayName("returns current version (active or deactivated) so the UI can offer reactivation")
        void found() {
            ScheduleTemplate t = template();
            when(scheduleTemplateRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(t));
            assertThat(sut.findByGroupId(GROUP_ID)).isSameAs(t);
        }

        @Test @DisplayName("throws NoSuchElementException when no current version exists")
        void notFound() {
            when(scheduleTemplateRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findByGroupId(GROUP_ID))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(GROUP_ID.toString());
        }
    }

    // ── findById (internal FK resolution) ───────────────────────────────────────

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("returns current active version by entity surrogate id")
        void found() {
            ScheduleTemplate t = template();
            when(scheduleTemplateRepository.findByIdActiveWithRoutes(1, COMPANY_ID))
                    .thenReturn(Optional.of(t));
            assertThat(sut.findById(1)).isSameAs(t);
        }

        @Test @DisplayName("throws when not found")
        void notFound() {
            when(scheduleTemplateRepository.findByIdActiveWithRoutes(99, COMPANY_ID))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    // ── update ──────────────────────────────────────────────────────────────────

    @Nested @DisplayName("update")
    class Update {
        @Test @DisplayName("closes current version and opens a new one preserving the group")
        void closesAndOpensVersion() {
            ScheduleTemplate current = template();
            Route route = route();
            when(scheduleTemplateRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));
            when(scheduleTemplateRepository.save(any(ScheduleTemplate.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ScheduleTemplate next = sut.update(GROUP_ID, request(), route, null);

            assertThat(current.getIsCurrent()).isFalse();
            assertThat(current.getVersionTo()).isNotNull();
            assertThat(next.getIsCurrent()).isTrue();
            assertThat(next.getGroup()).isSameAs(current.getGroup());
            assertThat(next.getRoute()).isSameAs(route);
            verify(scheduleTemplateRepository, times(2)).save(any(ScheduleTemplate.class));
        }

        @Test @DisplayName("throws when group has no current version")
        void notFound() {
            when(scheduleTemplateRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.update(GROUP_ID, request(), route(), null))
                    .isInstanceOf(NoSuchElementException.class);
            verify(scheduleTemplateRepository, never()).save(any());
        }
    }

    // ── deactivate ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("deactivate")
    class Deactivate {
        @Test @DisplayName("soft-deletes the current version")
        void delegates() {
            ScheduleTemplate current = template();
            when(scheduleTemplateRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));

            sut.deactivate(GROUP_ID);

            verify(scheduleTemplateRepository).softDelete(current);
        }
    }

    // ── reactivate ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("reactivate")
    class Reactivate {
        @Test @DisplayName("closes last version and opens a new active version copying data")
        void createsNewActiveVersion() {
            ScheduleTemplate last = template();
            last.setActive(false);
            when(scheduleTemplateRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(last));
            when(scheduleTemplateRepository.save(any(ScheduleTemplate.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ScheduleTemplate next = sut.reactivate(GROUP_ID);

            assertThat(last.getIsCurrent()).isFalse();
            assertThat(next.getActive()).isTrue();
            assertThat(next.getIsCurrent()).isTrue();
            assertThat(next.getTemplateNumber()).isEqualTo("T-01");
            assertThat(next.getGroup()).isSameAs(last.getGroup());
        }
    }

    // ── findByNumber ──────────────────────────────────────────────────────────

    @Nested @DisplayName("findByNumber")
    class FindByNumber {
        @Test @DisplayName("returns Optional with template when found")
        void found() {
            ScheduleTemplate t = template();
            when(scheduleTemplateRepository.findOne(any(Specification.class)))
                    .thenReturn(Optional.of(t));
            assertThat(sut.findByNumber("T-01")).contains(t);
        }

        @Test @DisplayName("returns empty Optional when not found")
        void empty() {
            when(scheduleTemplateRepository.findOne(any(Specification.class)))
                    .thenReturn(Optional.empty());
            assertThat(sut.findByNumber("NONE")).isEmpty();
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("returns all current versions scoped to the tenant")
        void returnsAllCurrent() {
            List<ScheduleTemplate> list = List.of(template());
            when(scheduleTemplateRepository.findAllCurrentByCompanyWithRoutes(COMPANY_ID))
                    .thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
            verify(scheduleTemplateRepository).findAllCurrentByCompanyWithRoutes(COMPANY_ID);
        }
    }

    // ── filter ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("delegates to 3-arg repo.filter with tenant + active Specification")
        void scopedFilter() {
            FilterRequest          req  = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<ScheduleTemplate> page = new PageImpl<>(List.of(template()));
            when(scheduleTemplateRepository.filter(eq(req), any(), any(Specification.class)))
                    .thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }
}
