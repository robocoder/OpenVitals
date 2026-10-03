package tech.mmarca.openvitals.features.imports.csv

import androidx.health.connect.client.records.BloodPressureRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `body position matches synonyms ignoring case and whitespace`() {
        assertEquals(CsvBodyPosition.SEATED, matchCsvBodyPosition("sitting"))
        assertEquals(CsvBodyPosition.SEATED, matchCsvBodyPosition("  SEATED "))
        assertEquals(CsvBodyPosition.STANDING, matchCsvBodyPosition("Stand ing"))
        assertEquals(CsvBodyPosition.LYING_DOWN, matchCsvBodyPosition("Lying-Down"))
        assertEquals(CsvBodyPosition.LYING_DOWN, matchCsvBodyPosition("supine"))
        assertEquals(CsvBodyPosition.RECLINED, matchCsvBodyPosition("reclining"))
        assertNull(matchCsvBodyPosition("floating"))
        assertNull(matchCsvBodyPosition(""))
    }

    @Test
    fun `cuff position matches either word order and initials`() {
        assertEquals(CsvCuffPosition.LEFT_WRIST, matchCsvCuffPosition("Left Wrist"))
        assertEquals(CsvCuffPosition.RIGHT_WRIST, matchCsvCuffPosition("wrist, right"))
        assertEquals(CsvCuffPosition.LEFT_ARM, matchCsvCuffPosition("LEFT  upper arm"))
        assertEquals(CsvCuffPosition.RIGHT_ARM, matchCsvCuffPosition("R arm"))
        assertEquals(CsvCuffPosition.LEFT_WRIST, matchCsvCuffPosition("LWrist"))
        assertNull(matchCsvCuffPosition("arm"))
        assertNull(matchCsvCuffPosition("left"))
        assertNull(matchCsvCuffPosition("left right arm"))
        assertNull(matchCsvCuffPosition("leg"))
    }

    @Test
    fun `a row becomes one normalised blood pressure record`() {
        val conversion = convertCsvRow(
            bpRow("2026-07-01 08:12:00", "120", "80", "Sitting", "left  ARM"),
            bloodPressureMapping(),
        )

        assertTrue(conversion.diagnostics.isEmpty())
        val record = conversion.records.single().record as BloodPressureRecord
        assertEquals(120.0, record.systolic.inMillimetersOfMercury, 0.0)
        assertEquals(80.0, record.diastolic.inMillimetersOfMercury, 0.0)
        assertEquals(BloodPressureRecord.BODY_POSITION_SITTING_DOWN, record.bodyPosition)
        assertEquals(BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM, record.measurementLocation)
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
            bpRow("2026-07-01 08:12:00", "120", "80", "dangling", "left arm"),
            bloodPressureMapping(),
        )

        assertTrue(conversion.records.isEmpty())
        assertEquals(CsvImportDiagnosticReason.UNRECOGNIZED_VALUE, conversion.diagnostics.single().reason)
    }

    @Test
    fun `an out of range pressure rejects the reading`() {
        val conversion = convertCsvRow(
            bpRow("2026-07-01 08:12:00", "1200", "80", "seated", "left arm"),
            bloodPressureMapping(),
        )

        assertTrue(conversion.records.isEmpty())
        assertEquals(CsvImportDiagnosticReason.OUT_OF_RANGE, conversion.diagnostics.single().reason)
    }

    @Test
    fun `preview reads systolic and diastolic separately`() {
        val rows = listOf(listOf("2026-07-01 08:12:00", "120", "80", "seated", "left arm"))
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
