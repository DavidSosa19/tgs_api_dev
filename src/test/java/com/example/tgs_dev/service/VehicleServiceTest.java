package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.repository.VehicleRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
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
    @InjectMocks VehicleService sut;

    private Vehicle vehicle() {
        Vehicle v = new Vehicle("V-001", null);
        v.setId(1);
        return v;
    }

    @Nested @DisplayName("save")
    class Save {
        @Test @DisplayName("delegates to repo")
        void delegates() {
            Vehicle v = vehicle();
            when(repo.save(v)).thenReturn(v);
            assertThat(sut.save(v)).isSameAs(v);
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("returns entity when found")
        void found() {
            Vehicle v = vehicle();
            when(repo.findById(1)).thenReturn(Optional.of(v));
            assertThat(sut.findById(1)).isSameAs(v);
        }

        @Test @DisplayName("throws NoSuchElementException when not found")
        void notFound() {
            when(repo.findById(99)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("delegates to repo")
        void delegates() {
            List<Vehicle> list = List.of(vehicle());
            when(repo.findAll()).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
        }
    }

    @Nested @DisplayName("findByNumber")
    class FindByNumber {
        @Test @DisplayName("returns Optional from repo")
        void found() {
            Vehicle v = vehicle();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(v));
            assertThat(sut.findByNumber("V-001")).contains(v);
        }

        @Test @DisplayName("returns empty when not found")
        void empty() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThat(sut.findByNumber("NONE")).isEmpty();
        }
    }

    @Nested @DisplayName("delete")
    class Delete {
        @Test @DisplayName("calls softDelete on repo")
        void delegates() {
            Vehicle v = vehicle();
            sut.delete(v);
            verify(repo).softDelete(v);
        }
    }

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("delegates to repo with pageable")
        void delegates() {
            FilterRequest req = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<Vehicle> page = new PageImpl<>(List.of(vehicle()));
            when(repo.filter(eq(req), any())).thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }
}
