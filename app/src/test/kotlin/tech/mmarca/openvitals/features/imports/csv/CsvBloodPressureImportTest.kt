package tech.mmarca.openvitals.features.imports.csv

import androidx.health.connect.client.records.BloodPressureRecord
import tech.mmarca.openvitals.domain.model.BpRecordValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun bloodPressureMapping(): CsvImportMapping = CsvImportMapping(
    columns = listOf(
        CsvColumnMapping(columnIndex = 0, role = CsvColumnRole.TIMESTAMP),
        CsvColumnMapping(1, CsvColumnRole.METRIC, CsvImportMetric.BLOOD_PRESSURE_SYSTOLIC, CsvDirectValue(CsvUnit.MILLIMETERS_OF_MERCURY)),
        CsvColumnMapping(2, CsvColumnRole.METRIC, CsvImportMetric.BLOOD_PRESSURE_DIASTOLIC, CsvDirectValue(CsvUnit.MILLIMETERS_OF_MERCURY)),
        CsvColumnMapping(3, CsvColumnRole.METRIC, CsvImportMetric.BLOOD_PRESSURE_BODY_POSITION, CsvTextValue),
        CsvColumnMapping(4, CsvColumnRole.METRIC, CsvImportMetric.BLOOD_PRESSURE_CUFF_POSITION, CsvTextValue),
    ),
    dateTime = CsvDateTimeSettings(format = CsvDateTimeFormat.YEAR_FIRST, zone = CsvTimeZoneMode.UTC),
)

private fun bpRow(vararg fields: String) = CsvRow(rowNumber = 2, fields = fields.toList())

class CsvBloodPressureImportTest {

    @Test
    fun `a row becomes one normalised blood pressure record`() {
        val conversion = convertCsvRow(
            bpRow("2026-07-01 08:12:00", "120", "80", "2", " 3 "),
            bloodPressureMapping(),
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
            bpRow("2026-07-01 08:12:00", "118", "76", "", ""),
            bloodPressureMapping(),
        )

        val record = conversion.records.single().record as BloodPressureRecord
        assertEquals(BloodPressureRecord.BODY_POSITION_UNKNOWN, record.bodyPosition)
        assertEquals(BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN, record.measurementLocation)
    }

    @Test
    fun `an unrecognised position rejects the reading`() {
        val conversion = convertCsvRow(
            bpRow("2026-07-01 08:12:00", "120", "80", "seated", "3"),
            bloodPressureMapping(),
        )

        assertTrue(conversion.records.isEmpty())
        assertEquals(CsvImportDiagnosticReason.UNRECOGNIZED_VALUE, conversion.diagnostics.single().reason)
    }

    @Test
    fun `an out of range pressure rejects the reading`() {
        val conversion = convertCsvRow(
            bpRow("2026-07-01 08:12:00", "1200", "80", "2", "3"),
            bloodPressureMapping(),
        )

        assertTrue(conversion.records.isEmpty())
        assertEquals(CsvImportDiagnosticReason.OUT_OF_RANGE, conversion.diagnostics.single().reason)
    }

    @Test
    fun `position codes outside the known set reject the reading`() {
        for (fields in listOf(
            arrayOf("2026-07-01 08:12:00", "120", "80", "5", "3"),
            arrayOf("2026-07-01 08:12:00", "120", "80", "2", "9"),
            arrayOf("2026-07-01 08:12:00", "120", "80", "2", "left arm"),
        )) {
            val conversion = convertCsvRow(bpRow(*fields), bloodPressureMapping())
            assertTrue(conversion.records.isEmpty())
            assertEquals(CsvImportDiagnosticReason.UNRECOGNIZED_VALUE, conversion.diagnostics.single().reason)
        }
    }

    @Test
    fun `both pressures blank skips silently and one blank rejects`() {
        val skipped = convertCsvRow(bpRow("2026-07-01 08:12:00", "", "", "2", "3"), bloodPressureMapping())
        assertTrue(skipped.records.isEmpty())
        assertTrue(skipped.diagnostics.isEmpty())

        for (fields in listOf(
            arrayOf("2026-07-01 08:12:00", "120", "", "2", "3"),
            arrayOf("2026-07-01 08:12:00", "", "80", "2", "3"),
        )) {
            val conversion = convertCsvRow(bpRow(*fields), bloodPressureMapping())
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
                bpRow("2026-07-01 08:12:00", systolic, diastolic, "2", "3"),
                bloodPressureMapping(),
            )

            assertTrue(conversion.records.isEmpty())
            assertEquals(
                CsvImportDiagnosticReason.SYSTOLIC_NOT_ABOVE_DIASTOLIC,
                conversion.diagnostics.single().reason,
            )
        }
    }

    @Test
    fun `a pulse column alongside blood pressure imports as heart rate`() {
        val mapping = bloodPressureMapping().let {
            it.copy(
                columns = it.columns + CsvColumnMapping(
                    5,
                    CsvColumnRole.METRIC,
                    CsvImportMetric.HEART_RATE,
                    CsvDirectValue(CsvUnit.BEATS_PER_MINUTE),
                ),
            )
        }

        val withPulse = convertCsvRow(
            bpRow("2026-07-01 08:12:00", "120", "80", "2", "3", "64"),
            mapping,
        )
        assertTrue(withPulse.diagnostics.isEmpty())
        assertEquals(
            setOf("BloodPressureRecord", "HeartRateRecord"),
            withPulse.records.map { it.targetType }.toSet(),
        )

        val blankPulse = convertCsvRow(
            bpRow("2026-07-01 08:12:00", "120", "80", "2", "3", ""),
            mapping,
        )
        assertEquals(listOf("BloodPressureRecord"), blankPulse.records.map { it.targetType })

        val badReading = convertCsvRow(
            bpRow("2026-07-01 08:12:00", "70", "90", "2", "3", "64"),
            mapping,
        )
        assertEquals(listOf("HeartRateRecord"), badReading.records.map { it.targetType })
    }

    @Test
    fun `preview reads systolic and diastolic separately`() {
        val rows = listOf(listOf("2026-07-01 08:12:00", "120", "80", "2", "3"))
        val mapping = bloodPressureMapping()

        assertEquals(listOf(120.0), previewCanonicalValues(rows, mapping, CsvImportMetric.BLOOD_PRESSURE_SYSTOLIC))
        assertEquals(listOf(80.0), previewCanonicalValues(rows, mapping, CsvImportMetric.BLOOD_PRESSURE_DIASTOLIC))
        assertTrue(previewCanonicalValues(rows, mapping, CsvImportMetric.BLOOD_PRESSURE_CUFF_POSITION).isEmpty())
    }

    @Test
    fun `blood pressure needs both systolic and diastolic columns`() {
        val mapping = CsvImportMapping(
            columns = listOf(
                CsvColumnMapping(columnIndex = 0, role = CsvColumnRole.TIMESTAMP),
                CsvColumnMapping(1, CsvColumnRole.METRIC, CsvImportMetric.BLOOD_PRESSURE_SYSTOLIC, CsvDirectValue(CsvUnit.MILLIMETERS_OF_MERCURY)),
            ),
            dateTime = CsvDateTimeSettings(format = CsvDateTimeFormat.YEAR_FIRST, zone = CsvTimeZoneMode.UTC),
        )

        val issues = validateCsvMapping(mapping, listOf(listOf("2026-07-01 08:12:00", "120")))

        assertTrue(CsvMappingIssue.BLOOD_PRESSURE_NEEDS_SYSTOLIC_AND_DIASTOLIC in issues)
        assertTrue(validateCsvMapping(bloodPressureMapping(), emptyList()).isEmpty())
    }

    @Test
    fun `mmHg header unit is detected`() {
        assertEquals(CsvUnit.MILLIMETERS_OF_MERCURY, detectCsvUnitInHeader("Systolic (mmHg)"))
    }
}
