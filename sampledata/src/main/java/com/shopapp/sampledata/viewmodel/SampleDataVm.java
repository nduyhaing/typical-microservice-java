package com.shopapp.sampledata.viewmodel;

import java.util.Map;

/**
 * Result of a sample data load (YAS SampleDataVm had only the message).
 *
 * @param message     human-readable summary
 * @param inserted    rows inserted per catalog table (0 for rows that already existed)
 * @param republished sample products that were soft-deleted (unpublished) and are published again
 * @param total       all inserted rows
 */
public record SampleDataVm(String message, Map<String, Integer> inserted, int republished, int total) {
}
