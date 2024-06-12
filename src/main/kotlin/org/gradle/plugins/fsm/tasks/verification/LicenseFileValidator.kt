package org.gradle.plugins.fsm.tasks.verification

import de.espirit.common.util.CsvReader
import org.gradle.api.GradleException
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Verifies a `licenses.csv` file to be packaged up in a FSM archive. The [validateLicenseCsv] method
 * performs the steps that FirstSpirit uses when validating a license file.
 *
 * See [validateLicenseCsv] for an explanation of validation criteria.
 */
object LicenseFileValidator {

    /**
     * Performs validation of the `licenses.csv` file.
     *
     * The following criteria must be met:
     * - A header of the form `"artifact","moduleUrl","moduleLicense","moduleLicenseUrl" must be present
     * - Each license record may optionally have a trailing comma. If one record has a trailing comma, all records must have one.
     * - The first entry of each record (the artifact name) must be non-empty.
     *
     * If the file is invalid, a [GradleException] is thrown with an appropriate error message.
     * Otherwise, the method completes without effect.
     *
     * @param licenseFilename The filename of the `licenses.csv` name in the FSM archive, usually `META-INF/licenses.csv`. Only used for logging.
     * @param inputStream     An [InputStream] providing the contents of the `licenses.csv` file.
     */
    fun validateLicenseCsv(licenseFilename: String, inputStream: InputStream) {
        val csvEntries: List<List<String>>
        InputStreamReader(inputStream).use {
            try {
                csvEntries = CsvReader.parseCsvToList(it, ',')
            } catch (e: IllegalStateException) {
                throw GradleException("License file '$licenseFilename' cannot be parsed: ${e.message}")
            }
        }

        // Header Line is required. An optional trailing comma is allowed.
        if (csvEntries.isEmpty() || csvEntries[0].isEmpty()) {
            throw GradleException("License file '$licenseFilename' is empty.")
        }
        val header = csvEntries[0]
        val expectedHeader = mutableListOf("artifact", "moduleUrl", "moduleLicense", "moduleLicenseUrl")
        if (header != expectedHeader && header != expectedHeader + "") {
            throw GradleException("License file '$licenseFilename': Invalid Header: ${header.joinToString(", ")}")
        }
        // If one line has a trailing comma, all lines must have one
        val expectedRecordSize = header.size
        for ((i, record) in csvEntries.withIndex().drop(1)) {
            if (record.size != expectedRecordSize) {
                throw GradleException("License file '$licenseFilename', line $i: Invalid Entry: ${record.joinToString(", ")}")
            }
            // First entry (artifact name) must be non-empty
            if (record[0].isEmpty()) {
                throw GradleException("License file '$licenseFilename', line $i: Invalid Entry: ${record.joinToString(", ")}")
            }
            // If there is a trailing comma, the last entry must be empty
            if (expectedRecordSize == 5 && record[4].isNotEmpty()) {
                throw GradleException("License file '$licenseFilename', line $i: Invalid Entry: ${record.joinToString(", ")}")
            }
        }

    }
}