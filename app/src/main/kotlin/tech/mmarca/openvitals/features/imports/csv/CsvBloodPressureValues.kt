package tech.mmarca.openvitals.features.imports.csv

import androidx.health.connect.client.records.BloodPressureRecord

/** The body positions a blood pressure CSV can name, by their canonical wording. */
enum class CsvBodyPosition(val canonical: String, val healthConnectValue: Int) {
    SEATED("seated", BloodPressureRecord.BODY_POSITION_SITTING_DOWN),
    STANDING("standing", BloodPressureRecord.BODY_POSITION_STANDING_UP),
    LYING_DOWN("lying down", BloodPressureRecord.BODY_POSITION_LYING_DOWN),
    RECLINED("reclined", BloodPressureRecord.BODY_POSITION_RECLINING),
}

/** The cuff positions a blood pressure CSV can name, by their canonical wording. */
enum class CsvCuffPosition(val canonical: String, val healthConnectValue: Int) {
    LEFT_WRIST("left wrist", BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST),
    RIGHT_WRIST("right wrist", BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_WRIST),
    LEFT_ARM("left arm", BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM),
    RIGHT_ARM("right arm", BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_UPPER_ARM),
}

/** Case, whitespace and punctuation are ignored: "Lying-Down" and " lying  down " both become "lyingdown". */
private fun compactCsvText(text: String): String =
    text.lowercase().filter { it.isLetter() }

private val BodyPositionSynonyms: Map<String, CsvBodyPosition> = mapOf(
    "seated" to CsvBodyPosition.SEATED,
    "sitting" to CsvBodyPosition.SEATED,
    "sittingdown" to CsvBodyPosition.SEATED,
    "sit" to CsvBodyPosition.SEATED,
    "sat" to CsvBodyPosition.SEATED,
    "standing" to CsvBodyPosition.STANDING,
    "standingup" to CsvBodyPosition.STANDING,
    "stand" to CsvBodyPosition.STANDING,
    "upright" to CsvBodyPosition.STANDING,
    "lyingdown" to CsvBodyPosition.LYING_DOWN,
    "lying" to CsvBodyPosition.LYING_DOWN,
    "laying" to CsvBodyPosition.LYING_DOWN,
    "laydown" to CsvBodyPosition.LYING_DOWN,
    "supine" to CsvBodyPosition.LYING_DOWN,
    "recumbent" to CsvBodyPosition.LYING_DOWN,
    "reclined" to CsvBodyPosition.RECLINED,
    "reclining" to CsvBodyPosition.RECLINED,
    "recline" to CsvBodyPosition.RECLINED,
    "semireclined" to CsvBodyPosition.RECLINED,
)

/**
 * The canonical body position [text] names, or null. Matching is case- and
 * whitespace-insensitive and accepts common synonyms ("sitting" is "seated").
 */
fun matchCsvBodyPosition(text: String): CsvBodyPosition? {
    val key = compactCsvText(text)
    if (key.isEmpty()) return null
    BodyPositionSynonyms[key]?.let { return it }
    return when {
        key.contains("recli") -> CsvBodyPosition.RECLINED
        key.contains("sit") || key.contains("seat") -> CsvBodyPosition.SEATED
        key.contains("stand") || key.contains("upright") -> CsvBodyPosition.STANDING
        key.contains("lying") || key.contains("laying") || key.contains("supine") -> CsvBodyPosition.LYING_DOWN
        else -> null
    }
}

/** "LW", "RArm": a side initial straight before the site. */
private val CuffInitialRegex = Regex("^([lr])(?:wrist|arm|upperarm|bicep)")

/**
 * The canonical cuff position [text] names, or null. Accepts either word order,
 * "upper arm" for "arm", and the initials "L"/"R".
 */
fun matchCsvCuffPosition(text: String): CsvCuffPosition? {
    val key = compactCsvText(text)
    if (key.isEmpty()) return null

    val initial = CuffInitialRegex.find(key)?.groupValues?.get(1)
    val left = key.contains("left") || initial == "l"
    val right = key.contains("right") || initial == "r"
    val wrist = key.contains("wrist")
    val arm = key.contains("arm") || key.contains("bicep")

    if (left == right || wrist == arm) return null
    return when {
        left && wrist -> CsvCuffPosition.LEFT_WRIST
        right && wrist -> CsvCuffPosition.RIGHT_WRIST
        left -> CsvCuffPosition.LEFT_ARM
        else -> CsvCuffPosition.RIGHT_ARM
    }
}
