package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.repository.RouteRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RouteService")
class RouteServiceTest {

    @Mock RouteRepository repo;
    @InjectMocks RouteService sut;

    private Route route() {
        Route r = new Route("R-1", 30, 3);
        r.setId(1);
        return r;
    }

    @Nested @DisplayName("save")
    class Save {
        @Test @DisplayName("delegates to repo")
        void delegates() {
            Route r = route();
            when(repo.save(r)).thenReturn(r);
            assertThat(sut.save(r)).isSameAs(r);
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("returns entity when found")
        void found() {
            Route r = route();
            when(repo.findById(1)).thenReturn(Optional.of(r));
            assertThat(sut.findById(1)).isSameAs(r);
        }

        @Test @DisplayName("throws ResourceNotFoundException when not found")
        void notFound() {
            when(repo.findById(99)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("delegates to repo ordered")
        void delegates() {
            List<Route> list = List.of(route());
            when(repo.findAllByOrderByRouteNumberAsc()).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
        }
    }

    @Nested @DisplayName("findByNumber")
    class FindByNumber {
        @Test @DisplayName("returns Optional from repo")
        void found() {
            Route r = route();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(r));
            assertThat(sut.findByNumber("R-1")).contains(r);
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
            Route r = route();
            sut.delete(r);
            verify(repo).softDelete(r);
        }
    }

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("delegates to repo with pageable")
        void delegates() {
            FilterRequest req = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<Route> page = new PageImpl<>(List.of(route()));
            when(repo.filter(eq(req), any())).thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }
}
