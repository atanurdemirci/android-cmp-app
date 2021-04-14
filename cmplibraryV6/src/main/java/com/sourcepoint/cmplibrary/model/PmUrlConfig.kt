package com.sourcepoint.cmplibrary.model

internal data class PmUrlConfig(
    val pmTab: PMTab = PMTab.PURPOSES,
    val consentLanguage: String?,
    val consentUUID: String?,
    val siteId: String?,
    val messageId: String?
)
