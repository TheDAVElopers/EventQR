package com.thedavelopers.eventqr.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Pins the repeatable migration {@code R__align_fresh_schema_with_production.sql} against accidental edits.
 *
 * <p>Flyway re-runs a repeatable migration whenever its checksum changes, so editing the file - even cosmetically -
 * re-executes it against every database on the next deploy. Keeping the file frozen is deliberate (see its FROZEN
 * header), and this test fails CI on any change so the re-run can never happen unnoticed. Both statements in the file
 * are {@code IF EXISTS} no-ops in production, so the one-off re-run after this file's documentation-only edit is
 * harmless.
 *
 * <p>The checksum is computed the way flyway-core does ({@code ChecksumCalculatorHelper}): read the file line by line
 * with {@link BufferedReader#readLine()} so line terminators are excluded and CRLF vs LF does not matter, strip a
 * UTF-8 BOM from the first line, and CRC32 the UTF-8 bytes of every line. The result is a signed 32-bit int.
 */
class FrozenRepeatableMigrationTest {

    private static final String MIGRATION_RESOURCE = "db/migration/R__align_fresh_schema_with_production.sql";

    /**
     * The checksum of the file as of this change. Production still holds the pre-freeze value, so it re-applies the
     * file once on the first deploy (a no-op). Update this only together with a deliberate change to the migration.
     */
    private static final int FROZEN_CHECKSUM = 826445448;

    @Test
    void checksumMatchesTheFrozenValueSoAnyEditFailsCi() throws IOException {
        assertThat(flywayChecksum()).isEqualTo(FROZEN_CHECKSUM);
    }

    @Test
    void headerMarksTheFileFrozen() throws IOException {
        assertThat(readMigration()).contains("FROZEN: do not edit or delete")
                .contains("Any change re-runs this file on production");
    }

    @Test
    void retiredObjectNamesAreDocumentedAsNeverReusable() throws IOException {
        assertThat(readMigration())
                .contains("uq_txn_logs_approved_reg_purpose")
                .contains("fk_point_transactions_source")
                .contains("never reused");
    }

    private static int flywayChecksum() throws IOException {
        try (InputStream in = new ClassPathResource(MIGRATION_RESOURCE).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 4096)) {
            CRC32 crc32 = new CRC32();
            String line = reader.readLine();
            if (line != null) {
                line = stripUtf8Bom(line);
                do {
                    crc32.update(line.getBytes(StandardCharsets.UTF_8));
                } while ((line = reader.readLine()) != null);
            }
            return (int) crc32.getValue();
        }
    }

    private static String readMigration() throws IOException {
        try (InputStream in = new ClassPathResource(MIGRATION_RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String stripUtf8Bom(String line) {
        return !line.isEmpty() && line.charAt(0) == '\uFEFF' ? line.substring(1) : line;
    }
}
