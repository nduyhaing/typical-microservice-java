package com.shopapp.commonlibrary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Proves the build toolchain (compiler, annotation processors, test runner) works on Java 27.
 */
class ToolchainProbeTest {

    @lombok.Value
    static class LombokProbe {
        String name;
    }

    record Source(String name) {
    }

    record Target(String name) {
    }

    @Mapper
    interface ProbeMapper {
        Target map(Source source);
    }

    @Test
    void runsOnJava27() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(27);
    }

    @Test
    void lombokGeneratesAccessors() {
        assertThat(new LombokProbe("x").getName()).isEqualTo("x");
    }

    @Test
    void mapstructGeneratesMapper() {
        assertThat(Mappers.getMapper(ProbeMapper.class).map(new Source("x")).name()).isEqualTo("x");
    }
}
