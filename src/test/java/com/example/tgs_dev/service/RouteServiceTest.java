package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.repository.RouteRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RouteService")
class RouteServiceTest {

    @Mock RouteRepository repo;
    @Mock TenantService   tenantService;
    @InjectMocks RouteService sut;

    private static final int COMPANY_ID = 1;

    private Company company() {
        Company c = new Company("ACME", "NIT-1");
        c.setId(COMPANY_ID);
        return c;
    }

    private Route route() {
        Route r = new Route("R-1", 30, 3);
        r.setId(1);
        return r;
    }

    @BeforeEach
    void stubTenant() {
        lenient().when(tenantService.currentCompanyId()).thenReturn(COMPANY_ID);
        lenient().when(tenantService.currentCompany()).thenReturn(company());
    }

    @Nested @DisplayName("save")
    class Save {
        @Test @DisplayName("stampa la empresa del tenant y delega en repo")
        void stampsTenantCompanyAndSaves() {
            Route r = route();
            when(repo.save(r)).thenReturn(r);

            Route result = sut.save(r);

            assertThat(result).isSameAs(r);
            assertThat(r.getCompany().getId()).isEqualTo(COMPANY_ID);
            verify(repo).save(r);
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("retorna entidad cuando existe en el tenant")
        void found() {
            Route r = route();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(r));
            assertThat(sut.findById(1)).isSameAs(r);
        }

        @Test @DisplayName("lanza ResourceNotFoundException cuando no existe")
        void notFound() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("filtra por tenant y retorna lista")
        void filtersByTenant() {
            List<Route> list = List.of(route());
            when(repo.findAll(any(Specification.class))).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
        }
    }

    @Nested @DisplayName("findByNumber")
    class FindByNumber {
        @Test @DisplayName("retorna Optional con la ruta cuando existe")
        void found() {
            Route r = route();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(r));
            assertThat(sut.findByNumber("R-1")).contains(r);
        }

        @Test @DisplayName("retorna Optional vacío cuando no existe")
        void empty() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThat(sut.findByNumber("NONE")).isEmpty();
        }
    }

    @Nested @DisplayName("delete")
    class Delete {
        @Test @DisplayName("delega softDelete al repositorio")
        void delegates() {
            Route r = route();
            sut.delete(r);
            verify(repo).softDelete(r);
        }
    }

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("llama al repo con la spec de tenant adicional")
        void delegates() {
            FilterRequest req = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<Route> page = new PageImpl<>(List.of(route()));
            when(repo.filter(eq(req), any(), any(Specification.class))).thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }
}
