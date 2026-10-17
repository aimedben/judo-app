package com.example.ui.screens

/**
 * Determines what the camera / douchette scanner should do with a decoded code.
 *
 * POINTAGE            : the code is a member QR code and should be turned into a check-in.
 * EXTRACTION_MEMBRE   : the code is a sports card and its data should be extracted to prefill
 *                       a brand new member registration form.
 */
enum class ScannerTargetMode(val label: String) {
    POINTAGE("Pointage des présences"),
    EXTRACTION_MEMBRE("Extraction carte sportive")
}
