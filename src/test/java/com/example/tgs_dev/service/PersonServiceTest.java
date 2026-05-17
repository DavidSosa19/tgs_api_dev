package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.repository.PersonRepository;
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
@DisplayName("PersonService")
class PersonServiceTest {

    @Mock PersonRepository repo;
    @Mock TenantService    tenantService;
    @InjectMocks PersonService sut;

    private static final int COMPANY_ID = 1;

    private Company company() {
        Company c = new Company("ACME", "NIT-1");
        c.setId(COMPANY_ID);
        return c;
    }

    private Person person() {
        Person p = new Person("DOC", "John", null, "Doe", null);
        p.setId(1);
        return p;
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
            Person p = person();
            when(repo.save(p)).thenReturn(p);

            Person result = sut.save(p);

            assertThat(result).isSameAs(p);
            assertThat(p.getCompany().getId()).isEqualTo(COMPANY_ID);
            verify(repo).save(p);
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("retorna entidad cuando existe en el tenant")
        void found() {
            Person p = person();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(p));
            assertThat(sut.findById(1)).isSameAs(p);
        }

        @Test @DisplayName("lanza ResourceNotFoundException cuando no existe")
        void notFound() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("findByDocumentNumber")
    class FindByDocumentNumber {
        @Test @DisplayName("retorna Optional con la persona cuando existe")
        void delegates() {
            Person p = person();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(p));
            assertThat(sut.findByDocumentNumber("DOC")).contains(p);
        }

        @Test @DisplayName("retorna Optional vacío cuando no existe")
        void empty() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThat(sut.findByDocumentNumber("NONE")).isEmpty();
        }
    }

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("filtra por tenant y retorna lista")
        void filtersByTenant() {
            List<Person> list = List.of(person());
            when(repo.findAll(any(Specification.class))).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
        }
    }

    @Nested @DisplayName("delete")
    class Delete {
        @Test @DisplayName("delega softDelete al repositorio")
        void delegates() {
            Person p = person();
            sut.delete(p);
            verify(repo).softDelete(p);
        }
    }

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("llama al repo con la spec de tenant adicional")
        void delegates() {
            FilterRequest req = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<Person> page = new PageImpl<>(List.of(person()));
            when(repo.filter(eq(req), any(), any(Specification.class))).thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }
}
