package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.repository.PersonRepository;
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
@DisplayName("PersonService")
class PersonServiceTest {

    @Mock PersonRepository repo;
    @InjectMocks PersonService sut;

    private Person person() {
        Person p = new Person("DOC", "John", null, "Doe", null);
        p.setId(1);
        return p;
    }

    @Nested @DisplayName("save")
    class Save {
        @Test @DisplayName("delegates to repo and returns saved entity")
        void delegates() {
            Person p = person();
            when(repo.save(p)).thenReturn(p);
            assertThat(sut.save(p)).isSameAs(p);
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("returns entity when found")
        void found() {
            Person p = person();
            when(repo.findById(1)).thenReturn(Optional.of(p));
            assertThat(sut.findById(1)).isSameAs(p);
        }

        @Test @DisplayName("throws ResourceNotFoundException when not found")
        void notFound() {
            when(repo.findById(99)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("findByDocumentNumber")
    class FindByDocumentNumber {
        @Test @DisplayName("returns Optional from repo")
        void delegates() {
            Person p = person();
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(p));
            assertThat(sut.findByDocumentNumber("DOC")).contains(p);
        }

        @Test @DisplayName("returns empty when not found")
        void empty() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThat(sut.findByDocumentNumber("NONE")).isEmpty();
        }
    }

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("delegates to repo")
        void delegates() {
            List<Person> list = List.of(person());
            when(repo.findAll()).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
        }
    }

    @Nested @DisplayName("delete")
    class Delete {
        @Test @DisplayName("calls softDelete on repo")
        void delegates() {
            Person p = person();
            sut.delete(p);
            verify(repo).softDelete(p);
        }
    }

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("delegates to repo with pageable derived from request")
        void delegates() {
            FilterRequest req = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<Person> page = new PageImpl<>(List.of(person()));
            when(repo.filter(eq(req), any())).thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }
}
