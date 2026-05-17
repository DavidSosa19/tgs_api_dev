package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Vehicle;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("VehicleService")
class VehicleServiceTest {

    @Mock VehicleRepository repo;
    @Mock TenantService     tenantService;
    @InjectMocks VehicleService sut;

    private static final int COMPANY_ID = 1;

    private Company company() {
        Company c = new Company("ACME", "NIT-1");
        c.setId(COMPANY_ID);
        return c;
    }

    private Vehicle vehicle() {
        Vehicle v = new Vehicle("V-001", null);
        v.setId(1);
        return v;
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
            Vehicle v = vehicle();
            when(repo.save(v)).thenReturn(v);

            Vehicle result = sut.save(v);

            assertThat(result).isSameAs(v);
            assertThat(v.getCompany().getId()).isEqualTo(COMPANY_ID);
            verify(repo).save(v);
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("retorna entidad cuando existe en el tenant")
        void found() {
            Vehicle v = vehicle();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(v));
            assertThat(sut.findById(1)).isSameAs(v);
        }

        @Test @DisplayName("lanza NoSuchElementException cuando no existe")
        void notFound() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("filtra por tenant y retorna lista")
        void filtersByTenant() {
            List<Vehicle> list = List.of(vehicle());
            when(repo.findAll(any(Specification.class))).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
        }
    }

    @Nested @DisplayName("findByNumber")
    class FindByNumber {
        @Test @DisplayName("retorna Optional con el vehículo cuando existe")
        void found() {
            Vehicle v = vehicle();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(v));
            assertThat(sut.findByNumber("V-001")).contains(v);
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
            Vehicle v = vehicle();
            sut.delete(v);
            verify(repo).softDelete(v);
        }
    }

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("llama al repo con la spec de tenant adicional")
        void delegates() {
            FilterRequest req = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<Vehicle> page = new PageImpl<>(List.of(vehicle()));
            when(repo.filter(eq(req), any(), any(Specification.class))).thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }
}
