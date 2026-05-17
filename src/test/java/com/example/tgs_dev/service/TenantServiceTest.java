package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.repository.CompanyRepository;
import com.example.tgs_dev.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService")
class TenantServiceTest {

    @Mock CompanyRepository companyRepository;
    @InjectMocks TenantService sut;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Company company(int id) {
        Company c = new Company("ACME", "123-456");
        c.setId(id);
        return c;
    }

    @Nested
    @DisplayName("currentCompanyId()")
    class CurrentCompanyId {

        @Test
        @DisplayName("retorna el ID almacenado en TenantContext")
        void returnsIdFromContext() {
            TenantContext.set(3);
            assertThat(sut.currentCompanyId()).isEqualTo(3);
        }

        @Test
        @DisplayName("lanza IllegalStateException cuando TenantContext está vacío")
        void throwsWhenContextEmpty() {
            assertThatThrownBy(sut::currentCompanyId)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("currentCompany()")
    class CurrentCompany {

        @Test
        @DisplayName("carga y retorna la Company del repositorio")
        void loadsCompanyFromRepository() {
            TenantContext.set(1);
            Company expected = company(1);
            when(companyRepository.findById(1)).thenReturn(Optional.of(expected));

            Company result = sut.currentCompany();

            assertThat(result).isSameAs(expected);
            verify(companyRepository).findById(1);
        }

        @Test
        @DisplayName("lanza IllegalStateException cuando la empresa no existe en BD")
        void throwsWhenCompanyNotFound() {
            TenantContext.set(99);
            when(companyRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(sut::currentCompany)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("lanza IllegalStateException cuando TenantContext está vacío (sin llamar al repo)")
        void throwsWhenContextEmptyWithoutHittingRepo() {
            assertThatThrownBy(sut::currentCompany)
                    .isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(companyRepository);
        }
    }
}
