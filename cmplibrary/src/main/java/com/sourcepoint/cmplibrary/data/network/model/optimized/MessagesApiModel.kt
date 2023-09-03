package com.sourcepoint.cmplibrary.data.network.model.optimized

import com.sourcepoint.cmplibrary.core.getOrNull
import com.sourcepoint.cmplibrary.data.network.converter.* // ktlint-disable
import com.sourcepoint.cmplibrary.data.network.model.optimized.messages.MessagesBodyReq
import com.sourcepoint.cmplibrary.data.network.model.optimized.messages.MessagesMetaData
import com.sourcepoint.cmplibrary.data.network.util.Env
import com.sourcepoint.cmplibrary.exception.CampaignType
import com.sourcepoint.cmplibrary.model.exposed.CcpaStatus
import com.sourcepoint.cmplibrary.model.exposed.GDPRPurposeGrants
import com.sourcepoint.cmplibrary.model.exposed.MessageCategory
import com.sourcepoint.cmplibrary.model.exposed.MessageSubCategory
import com.sourcepoint.cmplibrary.util.check
import com.sourcepoint.cmplibrary.util.generateCcpaUspString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class MessagesParamReq(
    @SerialName("env") val env: Env,
    @SerialName("metadata") val metadata: MessagesMetaData,
    @SerialName("body") val body: MessagesBodyReq,
    @SerialName("nonKeyedLocalState") val nonKeyedLocalState: JsonObject? = JsonObject(mapOf()),
)

@Serializable
data class MessagesResp(
    @SerialName("campaigns") val campaigns: Campaigns?,
    @SerialName("localState") val localState: JsonElement?,
    @SerialName("nonKeyedLocalState") val nonKeyedLocalState: JsonElement?,
    @SerialName("priority") val priority: List<Int>,
    @SerialName("propertyId") val propertyId: Int?
) {
    val campaignList: List<CampaignMessage>
        get() {
            val list = mutableListOf<CampaignMessage>().apply {
                campaigns?.gdpr?.let { add(it) }
                campaigns?.ccpa?.let { add(it) }
            }.associateBy { it.type.toCategoryId() }
            return priority.mapNotNull { list[it] }.toSet().toList()
        }

    override fun toString(): String {
        return check { JsonConverter.converter.encodeToString(this) }.getOrNull()
            ?: super.toString()
    }

    @Serializable
    data class Campaigns(
        @SerialName("CCPA") val ccpa: CCPA?,
        @SerialName("GDPR") val gdpr: GDPR?
    ) {
        @Serializable
        data class CCPA(
            @SerialName("applies")
            val applies: Boolean?,
            @SerialName("consentedAll")
            val consentedAll: Boolean?,
            @SerialName("dateCreated")
            override val dateCreated: String?,
            @SerialName("message")
            override val message: JsonElement?,
            @SerialName("messageMetaData")
            override val messageMetaData: MessageMetaData?,
            @SerialName("newUser")
            val newUser: Boolean?,
            @SerialName("rejectedAll")
            val rejectedAll: Boolean?,
            @SerialName("rejectedCategories")
            val rejectedCategories: List<String>?,
            @SerialName("rejectedVendors")
            val rejectedVendors: List<String>?,
            @SerialName("signedLspa")
            val signedLspa: Boolean?,
            @SerialName("GPPData")
            @Serializable(with = JsonMapSerializer::class)
            val gppData: Map<String, JsonElement>? = null,
            @Serializable(with = CcpaStatusSerializer::class)
            val status: CcpaStatus?,
            @Serializable(with = CampaignTypeSerializer::class)
            override val type: CampaignType,
            @SerialName("url")
            override val url: String?,
            @SerialName("webConsentPayload")
            val webConsentPayload: JsonObject?,
        ) : CampaignMessage {

            val uspstring: String
                get() = generateCcpaUspString(
                    applies = applies,
                    ccpaStatus = status,
                    signedLspa = signedLspa,
                )

            internal fun toCcpaCS() = CcpaCS(
                applies = applies,
                consentedAll = consentedAll,
                dateCreated = dateCreated,
                newUser = newUser,
                rejectedAll = rejectedAll,
                rejectedCategories = rejectedCategories,
                rejectedVendors = rejectedVendors,
                signedLspa = signedLspa,
                status = status ?: CcpaStatus.rejectedNone,
                gppData = gppData,
                ccpaApplies = null,
                uuid = null,
                gpcEnabled = null,
                webConsentPayload = webConsentPayload,
            )
        }

        @Serializable
        data class GDPR(
            @SerialName("addtlConsent")
            val addtlConsent: String?,
            @SerialName("childPmId")
            val childPmId: String?,
            @SerialName("consentStatus")
            val consentStatus: ConsentStatus?,
            @SerialName("customVendorsResponse")
            val customVendorsResponse: GdprCS.CustomVendorsResponse?,
            @SerialName("dateCreated")
            override val dateCreated: String?,
            @SerialName("euconsent")
            val euconsent: String?,
            @Serializable(with = GrantsSerializer::class)
            val grants: Map<String, GDPRPurposeGrants>?,
            @SerialName("hasLocalData")
            val hasLocalData: Boolean?,
            @SerialName("message")
            override val message: JsonElement?,
            @SerialName("messageMetaData")
            override val messageMetaData: MessageMetaData?,
            @Serializable(with = JsonMapSerializer::class)
            val TCData: Map<String, JsonElement>?,
            @Serializable(with = CampaignTypeSerializer::class)
            override val type: CampaignType,
            @SerialName("url")
            override val url: String?,
            @SerialName("webConsentPayload")
            val webConsentPayload: JsonObject?,
        ) : CampaignMessage {
            fun toGdprCS() = GdprCS(
                applies = null,
                gdprApplies = null,
                categories = null,
                consentAllRef = null,
                consentedToAll = null,
                legIntCategories = null,
                legIntVendors = null,
                postPayload = null,
                rejectedAny = null,
                specialFeatures = null,
                vendors = null,
                addtlConsent = addtlConsent,
                consentStatus = consentStatus ?: ConsentStatus(),
                cookieExpirationDays = null,
                customVendorsResponse = customVendorsResponse,
                dateCreated = dateCreated,
                euconsent = euconsent,
                grants = grants,
                TCData = TCData,
                localDataCurrent = null,
                uuid = null,
                vendorListId = null,
                webConsentPayload = webConsentPayload,
            )
        }
    }
}

interface CampaignMessage {
    val type: CampaignType
    val messageMetaData: MessageMetaData?
    val message: JsonElement?
    val url: String?
    val dateCreated: String?
}

internal fun CampaignType.toCategoryId() = when (this) {
    CampaignType.GDPR -> 1
    CampaignType.CCPA -> 2
}

@Serializable
data class MessageMetaData(
    @SerialName("bucket") val bucket: Int?,
    @Serializable(with = MessageCategorySerializer::class)
    @SerialName("categoryId") val categoryId: MessageCategory,
    @SerialName("messageId") val messageId: Int?,
    @SerialName("msgDescription") val msgDescription: String?,
    @SerialName("prtnUUID") val prtnUUID: String?,
    @Serializable(with = MessageSubCategorySerializer::class) val subCategoryId: MessageSubCategory
) {
    override fun toString(): String {
        return check { JsonConverter.converter.encodeToString(this) }.getOrNull()
            ?: "{}"
    }
}

@Serializable
data class ConsentStatus(
    @SerialName("consentedAll") var consentedAll: Boolean? = null,
    @SerialName("consentedToAny") val consentedToAny: Boolean? = null,
    @SerialName("granularStatus") val granularStatus: GranularStatus? = null,
    @SerialName("hasConsentData") val hasConsentData: Boolean? = null,
    @SerialName("rejectedAny") val rejectedAny: Boolean? = null,
    @SerialName("rejectedLI") val rejectedLI: Boolean? = null,
    @SerialName("legalBasisChanges") var legalBasisChanges: Boolean? = null,
    @SerialName("vendorListAdditions") var vendorListAdditions: Boolean? = null
) {
    @Serializable
    data class GranularStatus(
        @SerialName("defaultConsent") val defaultConsent: Boolean?,
        @SerialName("previousOptInAll") var previousOptInAll: Boolean?,
        @Serializable(with = GranularStateSerializer::class) val purposeConsent: GranularState?,
        @Serializable(with = GranularStateSerializer::class) val purposeLegInt: GranularState?,
        @Serializable(with = GranularStateSerializer::class) val vendorConsent: GranularState?,
        @Serializable(with = GranularStateSerializer::class) val vendorLegInt: GranularState?
    )
}
