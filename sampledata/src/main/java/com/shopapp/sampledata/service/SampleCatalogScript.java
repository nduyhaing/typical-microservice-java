package com.shopapp.sampledata.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Product's dev seed changeset turned into idempotent SQL (decisions.md 5-51).
 *
 * <p>The seed is one {@code insert into <table> (<columns>) values (<expressions>);} per line, with foreign keys as
 * subselects by slug or name and no hard-coded ids. Each insert becomes
 * {@code insert into <table> (<columns>) select <expressions> where not exists (<row with the same natural key>)},
 * so ids still come from the sequences, a second run inserts nothing and rows an admin added or edited are left
 * alone. A published sample product that an admin soft-deleted (product's delete only unpublishes it, 3a-17) is
 * published again. Only the catalog tables below are accepted: anything else in the file (another table, a delete,
 * an update, a second statement on the line) fails the parse, so the action can never write orders, customers,
 * carts or stock reservations.
 */
public final class SampleCatalogScript {

    /** Classpath location of product's seed changeset, bundled by the build (see pom.xml). */
    public static final String LOCATION = "db/product/sample-data.sql";

    /** Catalog tables and the columns that identify a sample row, in seed order. */
    static final Map<String, List<String>> NATURAL_KEYS = Map.ofEntries(
        Map.entry("brand", List.of("slug")),
        Map.entry("category", List.of("slug")),
        Map.entry("product", List.of("slug")),
        Map.entry("product_category", List.of("product_id", "category_id")),
        Map.entry("product_option", List.of("name")),
        Map.entry("product_option_combination", List.of("product_id", "product_option_id")),
        Map.entry("product_option_value", List.of("product_id", "product_option_id", "value")),
        Map.entry("product_attribute_group", List.of("name")),
        Map.entry("product_attribute", List.of("name", "product_attribute_group_id")),
        Map.entry("product_attribute_value", List.of("product_id", "product_attribute_id")),
        Map.entry("product_related", List.of("product_id", "related_product_id")));

    private static final Pattern INSERT = Pattern.compile(
        "insert into ([a-z_]+) \\(([a-z_, ]+)\\) values \\((.*)\\);", Pattern.CASE_INSENSITIVE);

    private final List<Statement> statements;

    private SampleCatalogScript(List<Statement> statements) {
        this.statements = List.copyOf(statements);
    }

    public List<Statement> statements() {
        return statements;
    }

    /**
     * One seed row.
     *
     * @param table      catalog table
     * @param insertSql  insert-if-missing
     * @param restoreSql republishes a soft-deleted sample product, or {@code null}
     */
    public record Statement(String table, String insertSql, String restoreSql) {
    }

    /** Parses the seed; throws {@link IllegalStateException} on any line that is not a catalog insert. */
    public static SampleCatalogScript parse(String sql) {
        List<Statement> statements = new ArrayList<>();
        int lineNo = 0;
        for (String rawLine : sql.split("\\R")) {
            lineNo++;
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            statements.add(toStatement(line, lineNo));
        }
        return new SampleCatalogScript(statements);
    }

    private static Statement toStatement(String line, int lineNo) {
        Matcher matcher = INSERT.matcher(line);
        if (!matcher.matches()) {
            throw invalid(lineNo, "not a single-line insert");
        }
        String table = matcher.group(1).toLowerCase(Locale.ROOT);
        List<String> keys = NATURAL_KEYS.get(table);
        if (keys == null) {
            throw invalid(lineNo, "table " + table + " is not a catalog table");
        }
        List<String> columns = List.of(matcher.group(2).toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
        List<String> values = splitValues(matcher.group(3), lineNo);
        if (columns.size() != values.size()) {
            throw invalid(lineNo, columns.size() + " columns but " + values.size() + " values");
        }
        StringBuilder where = new StringBuilder();
        for (String key : keys) {
            int index = columns.indexOf(key);
            if (index < 0) {
                throw invalid(lineNo, "natural key column " + key + " missing");
            }
            where.append(where.isEmpty() ? "" : " and ").append("existing.").append(key).append(" = ")
                .append(values.get(index));
        }
        String insertSql = "insert into " + table + " (" + String.join(", ", columns) + ") select "
            + String.join(", ", values) + " where not exists (select 1 from " + table + " existing where " + where
            + ")";
        return new Statement(table, insertSql, restoreSql(table, columns, values));
    }

    private static String restoreSql(String table, List<String> columns, List<String> values) {
        int published = columns.indexOf("is_published");
        if (!"product".equals(table) || published < 0 || !"true".equalsIgnoreCase(values.get(published))) {
            return null;
        }
        // a republish is a new version of the product (last_modified_on moves on), so search and recommendation
        // see it as newer than the removal they recorded (decisions.md 5-70)
        return "update product set is_published = true, last_modified_on = now() where slug = "
            + values.get(columns.indexOf("slug"))
            + " and is_published is distinct from true";
    }

    /** Splits on top-level commas, outside quotes and parentheses; rejects unbalanced input and a second statement. */
    private static List<String> splitValues(String text, int lineNo) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                current.append(c);
                if (c == '\'') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                        current.append(text.charAt(++i));
                    } else {
                        quoted = false;
                    }
                }
                continue;
            }
            switch (c) {
                case '\'' -> quoted = true;
                case '(' -> depth++;
                case ')' -> depth--;
                case ';' -> throw invalid(lineNo, "more than one statement");
                default -> {
                    // part of the value
                }
            }
            if (depth < 0) {
                throw invalid(lineNo, "unbalanced parentheses");
            }
            if (c == ',' && depth == 0) {
                values.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (quoted || depth != 0) {
            throw invalid(lineNo, "unbalanced quotes or parentheses");
        }
        values.add(current.toString().strip());
        return values;
    }

    private static IllegalStateException invalid(int lineNo, String reason) {
        return new IllegalStateException("Sample data line " + lineNo + ": " + reason);
    }
}
