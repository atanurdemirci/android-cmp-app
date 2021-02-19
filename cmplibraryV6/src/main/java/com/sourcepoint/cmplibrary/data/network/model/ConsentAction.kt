package com.sourcepoint.cmplibrary.data.network.model

import com.sourcepoint.cmplibrary.model.ActionType
import org.json.JSONObject

data class ConsentAction(
    val choiceId: String? = null,
    val privacyManagerId: String? = null,
    val pmTab: String? = null,
    val requestFromPm: Boolean,
    val saveAndExitVariables: JSONObject = JSONObject(),
    val pubData: JSONObject = JSONObject(),
    val consentLanguage: String? = null,
    val actionType: ActionType
)
