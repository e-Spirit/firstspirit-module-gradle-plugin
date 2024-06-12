package org.gradle.plugins.fsm.tasks.verification

import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

/**
 * Test cases for [LicenseFileValidator]. In each instance, we prepare a CSV string containing licenses
 * and verify the result, which should either be a [org.gradle.api.GradleException] in case of failure, or
 * the [LicenseFileValidator.validateLicenseCsv] method completing without effect if the licenses.csv file is valid.
 *
 * See [LicenseFileValidator] for more information in the correct structure for a `licenses.csv` file.
 */
class LicenseFileValidatorTest {

    @Test
    fun `empty licenses file`() {
        assertThatValidation("").hasMessage("License file '$LICENSE_FILE_NAME' is empty.")
    }

    @Test
    fun `header only`() {
        assertThatValidation(HEADER).doesNotThrowAnyException()
        assertThatValidation("$HEADER,").doesNotThrowAnyException()
    }

    @Test
    fun `simple license file`() {
        // Load licenses file from class resources
        val licenses = javaClass.getResourceAsStream("/licenses/fsmdependency_licenses.csv")!!.use {
            it.reader().readText()
        }
        assertThatValidation(licenses).doesNotThrowAnyException()
    }

    @Test
    fun `valid quote escaping`() {
        val licenses = """
            $HEADER
            "tablelayout:TableLayout:20050920","https://tablelayout.dev.java.net","dev.java.net ""Other"" License","https://tablelayout.dev.java.net/servlets/LicenseDetails?licenseID=18"
        """.trimIndent()
        assertThatValidation(licenses).doesNotThrowAnyException()
    }

    @Test
    fun `invalid quote escaping`() {
        val licenses = """
            $HEADER
            "tablelayout:TableLayout:20050920","https://tablelayout.dev.java.net","dev.java.net \"Other\" License","https://tablelayout.dev.java.net/servlets/LicenseDetails?licenseID=18"
        """.trimIndent()
        assertThatValidation(licenses).hasMessage("License file '$LICENSE_FILE_NAME' cannot be parsed: Found non-whitespace characters after string literal!")
    }

    @Test
    fun `invalid record`() {
        val licenses = """
            $HEADER
            ",,,,"
        """.trimIndent()
        assertThatValidation(licenses).hasMessage("License file '$LICENSE_FILE_NAME', line 1: Invalid Entry: ,,,,")
    }

    /**
     * Convenience method providing an assert for the validation method. You can check if an exception is present
     * with the [AbstractThrowableAssert.isNull] and [AbstractThrowableAssert.isNotNull] methods.
     */
    private fun assertThatValidation(licensesCsv: String): AbstractThrowableAssert<*, out Throwable> {
        return assertThatCode { LicenseFileValidator.validateLicenseCsv(LICENSE_FILE_NAME, licensesCsv.byteInputStream()) }
    }

    companion object {
        private const val LICENSE_FILE_NAME = "META-INF/licenses.csv"
        private const val HEADER = """"artifact","moduleUrl","moduleLicense","moduleLicenseUrl""""
    }
}