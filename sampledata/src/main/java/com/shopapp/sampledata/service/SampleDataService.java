package com.shopapp.sampledata.service;

import com.shopapp.sampledata.viewmodel.SampleDataVm;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the sample catalog (product's dev seed made idempotent) against the product database in one transaction:
 * all or nothing, and an error is returned instead of logged and swallowed like YAS. The new rows reach search and
 * recommendation through the product CDC connector like any admin edit (decisions.md 5-52).
 */
@Slf4j
@Service
public class SampleDataService {

    /** Serializes concurrent loads (two clicks), which would otherwise both see a row as missing. */
    private static final long ADVISORY_LOCK_KEY = 0x5A3D_0001L;

    private final JdbcTemplate jdbcTemplate;
    private final SampleCatalogScript script;

    public SampleDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.script = load();
    }

    private static SampleCatalogScript load() {
        try {
            return SampleCatalogScript.parse(
                new ClassPathResource(SampleCatalogScript.LOCATION).getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Sample data script " + SampleCatalogScript.LOCATION + " missing", e);
        }
    }

    @Transactional
    public SampleDataVm createSampleData() {
        jdbcTemplate.query("select pg_advisory_xact_lock(?)", rs -> null, ADVISORY_LOCK_KEY);
        Map<String, Integer> inserted = new LinkedHashMap<>();
        int republished = 0;
        for (SampleCatalogScript.Statement statement : script.statements()) {
            inserted.merge(statement.table(), jdbcTemplate.update(statement.insertSql()), Integer::sum);
            if (statement.restoreSql() != null) {
                republished += jdbcTemplate.update(statement.restoreSql());
            }
        }
        int total = inserted.values().stream().mapToInt(Integer::intValue).sum();
        log.info("Sample data loaded: {} rows inserted {}, {} products republished", total, inserted, republished);
        String message = total == 0 && republished == 0
            ? "Sample data is already loaded; nothing to do."
            : "Sample data loaded: " + total + " rows inserted, " + republished + " products republished.";
        return new SampleDataVm(message, inserted, republished, total);
    }
}
