package tech.mmarca.openvitals.features.imports.csv

import androidx.health.connect.client.records.BloodPressureRecord
import tech.mmarca.openvitals.domain.model.BpRecordValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun bloodPressureMapping(): CsvImportMapping = CsvImportMapping(
    columns = listOf(
        CsvColumnMapping(columnIndex = 0, role = CsvColumnRole.TIMESTAMP),
        CsvColumnMapping(
            columnIndex = 1,
            role = CsvColumnRole.METRIC,
            metric = CsvImportMetric.BLOOD_PRESSURE_SYSTOLIC,
            interpretation = CsvDirectValue(CsvUnit.MILLIMETERS_OF_MERCURY)
        ),
        CsvColumnMapping(
            columnIndex = 2,
            role = CsvColumnRole.METRIC,
            metric = CsvImportMetric.BLOOD_PRESSURE_DIASTOLIC,
            interpretation = CsvDirectValue(CsvUnit.MILLIMETERS_OF_MERCURY)
        ),
        CsvColumnMapping(
            columnIndex = 3,
            role = CsvColumnRole.METRIC,
            metric = CsvImportMetric.BLOOD_PRESSURE_BODY_POSITION,
            interpretation = CsvCodeValue
        ),
        CsvColumnMapping(
            columnIndex = 4,
            role = CsvColumnRole.METRIC,
            metric = CsvImportMetric.BLOOD_PRESSURE_CUFF_LOCATION,
            interpretation = CsvCodeValue
        ),
    ),
    dateTime = CsvDateTimeSettings(
        format = CsvDateTimeFormat.YEAR_FIRST,
        zone = CsvTimeZoneMode.UTC
    ),
)

private fun row(fields: List<String>, rowNumber: Int = 2): CsvRow =
    CsvRow(rowNumber = rowNumber, fields = fields)

class CsvBloodPressureImportTest {

    // Blood pressure.

    @Test
    fun `a row becomes one normalised blood pressure record`() {
        val conversion = convertCsvRow(
            row = row(listOf("2026-07-01 08:12:00", "120", "80", "2", "3")),
            mapping = bloodPressureMapping(),
        )

        assertTrue(conversion.diagnostics.isEmpty())

        val record = conversion.records.single().record as BloodPressureRecord

        assertEquals(120.0, record.systolic.inMillimetersOfMercury, 0.0)
        assertEquals(80.0, record.diastolic.inMillimetersOfMercury, 0.0)
        assertEquals(BpRecordValues.BODY_POSITION_SITTING_DOWN, record.bodyPosition)
        assertEquals(BpRecordValues.MEASUREMENT_LOCATION_LEFT_UPPER_ARM, record.measurementLocation)
    }

    @Test
    fun `blank position cells are unknown`() {
        val conversion = convertCsvRow(
            row = row(listOf("2026-07-01 08:12:00", "118", "76", "", "")),
            mapping = bloodPressureMapping(),
        )

        val record = conversion.records.single().record as BloodPressureRecord

        assertEquals(BloodPressureRecord.BODY_POSITION_UNKNOWN, record.bodyPosition)
        assertEquals(BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN, record.measurementLocation)
    }

    @Test
    fun `an unrecognised position rejects the reading`() {
        val conversion = convertCsvRow(
            row = row(listOf("2026-07-01 08:12:00", "120", "80", "seated", "3")),
            mapping = bloodPressureMapping(),
        )

        assertTrue(conversion.records.isEmpty())
        assertEquals(CsvImportDiagnosticReason.UNRECOGNIZED_VALUE, conversion.diagnostics.single().reason)
    }

    @Test
    fun `an out of range pressure rejects the reading`() {
        val conversion = convertCsvRow(
            row = row(listOf("2026-07-01 08:12:00", "1200", "80", "2", "3")),
            mapping = bloodPressureMapping(),
        )

        assertTrue(conversion.records.isEmpty())
        assertEquals(CsvImportDiagnosticReason.OUT_OF_RANGE, conversion.diagnostics.single().reason)
    }

    @Test
    fun `position codes outside the known set reject the reading`() {
        for (fields in listOf(
            listOf("2026-07-01 08:12:00", "120", "80", "5", "3"),
            listOf("2026-07-01 08:12:00", "120", "80", "2", "9"),
            listOf("2026-07-01 08:12:00", "120", "80", "2", "left arm"),
        )) {
            val conversion = convertCsvRow(
                row = row(fields),
                mapping = bloodPressureMapping(),
            )

            assertTrue(conversion.records.isEmpty())
            assertEquals(CsvImportDiagnosticReason.UNRECOGNIZED_VALUE, conversion.diagnostics.single().reason)
        }
    }

    @Test
    fun `both pressures blank skips silently and one blank rejects`() {
        val skipped = convertCsvRow(
            row = row(listOf("2026-07-01 08:12:00", "", "", "2", "3")),
            mapping = bloodPressureMapping(),
        )

        assertTrue(skipped.records.isEmpty())
        assertTrue(skipped.diagnostics.isEmpty())

        for (fields in listOf(
            listOf("2026-07-01 08:12:00", "120", "", "2", "3"),
            listOf("2026-07-01 08:12:00", "", "80", "2", "3"),
        )) {
            val conversion = convertCsvRow(
                row = row(fields),
                mapping = bloodPressureMapping(),
            )

            assertTrue(conversion.records.isEmpty())
            assertEquals(
                CsvImportDiagnosticReason.MISSING_BLOOD_PRESSURE_VALUE,
                conversion.diagnostics.single().reason,
            )
        }
    }

    @Test
    fun `systolic not above diastolic rejects the reading`() {
        for ((systolic, diastolic) in listOf("80" to "80", "70" to "90")) {
            val conversion = convertCsvRow(
                row = row(listOf("2026-07-01 08:12:00", systolic, diastolic, "2", "3")),
                mapping = bloodPressureMapping(),
            )

            assertTrue(conversion.records.isEmpty())
            assertEquals(
                CsvImportDiagnosticReason.SYSTOLIC_NOT_ABOVE_DIASTOLIC,
                conversion.diagnostics.single().reason,
            )
        }
    }

    @Test
    fun `blood pressure needs both systolic and diastolic columns`() {
        val mapping = CsvImportMapping(
            columns = listOf(
                CsvColumnMapping(columnIndex = 0, role = CsvColumnRole.TIMESTAMP),
                CsvColumnMapping(
                    columnIndex = 1,
                    role = CsvColumnRole.METRIC,
                    metric = CsvImportMetric.BLOOD_PRESSURE_SYSTOLIC,
                    interpretation = CsvDirectValue(CsvUnit.MILLIMETERS_OF_MERCURY)
                ),
            ),
            dateTime = CsvDateTimeSettings(
                format = CsvDateTimeFormat.YEAR_FIRST,
                zone = CsvTimeZoneMode.UTC
            ),
        )

        val issues = validateCsvMapping(mapping, listOf(listOf("2026-07-01 08:12:00", "120")))

        assertTrue(CsvMappingIssue.BLOOD_PRESSURE_NEEDS_SYSTOLIC_AND_DIASTOLIC in issues)
        assertTrue(validateCsvMapping(bloodPressureMapping(), emptyList()).isEmpty())
    }
}
