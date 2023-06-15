
package com.sigmanote.notes.model

import javax.crypto.SecretKey

interface JsonManager {

    suspend fun exportJsonData(): String

    suspend fun importJsonData(data: String, importKey: SecretKey? = null): ImportResult

    enum class ImportResult {
        SUCCESS,
        BAD_FORMAT,
        BAD_DATA,
        FUTURE_VERSION,
        KEY_MISSING_OR_INCORRECT,
    }
}
