package com.example.tgs_dev.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TenantContext")
class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("set / get")
    class SetGet {

        @Test
        @DisplayName("get() retorna el valor almacenado por set()")
        void set_thenGet_returnsValue() {
            TenantContext.set(42);
            assertThat(TenantContext.get()).isEqualTo(42);
        }

        @Test
        @DisplayName("get() retorna null cuando no se ha establecido contexto")
        void get_withoutSet_returnsNull() {
            assertThat(TenantContext.get()).isNull();
        }
    }

    @Nested
    @DisplayName("require()")
    class Require {

        @Test
        @DisplayName("require() retorna el valor cuando está establecido")
        void require_withValue_returnsValue() {
            TenantContext.set(7);
            assertThat(TenantContext.require()).isEqualTo(7);
        }

        @Test
        @DisplayName("require() lanza IllegalStateException cuando no hay contexto")
        void require_withoutValue_throwsIllegalState() {
            assertThatThrownBy(TenantContext::require)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No tenant context");
        }
    }

    @Nested
    @DisplayName("clear()")
    class Clear {

        @Test
        @DisplayName("clear() elimina el valor; get() retorna null después")
        void clear_removesValue() {
            TenantContext.set(99);
            TenantContext.clear();
            assertThat(TenantContext.get()).isNull();
        }

        @Test
        @DisplayName("clear() después de require() hace que require() lance excepción")
        void clear_thenRequire_throws() {
            TenantContext.set(5);
            TenantContext.clear();
            assertThatThrownBy(TenantContext::require)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("aislamiento de hilos")
    class ThreadIsolation {

        @Test
        @DisplayName("el contexto del hilo principal no es visible en un hilo secundario")
        void mainContextNotVisibleInChildThread() throws InterruptedException {
            TenantContext.set(100);

            Integer[] captured = {-1};
            Thread child = new Thread(() -> captured[0] = TenantContext.get());
            child.start();
            child.join();

            assertThat(captured[0]).isNull();
        }
    }
}
