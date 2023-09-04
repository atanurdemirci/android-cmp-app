package com.sourcepoint.cmplibrary.data

import com.sourcepoint.cmplibrary.campaign.CampaignManager
import com.sourcepoint.cmplibrary.consent.ConsentManager
import com.sourcepoint.cmplibrary.consent.ConsentManagerUtils
import com.sourcepoint.cmplibrary.core.Either
import com.sourcepoint.cmplibrary.core.ExecutorManager
import com.sourcepoint.cmplibrary.core.executeOnLeft
import com.sourcepoint.cmplibrary.core.executeOnRight
import com.sourcepoint.cmplibrary.core.getOrNull
import com.sourcepoint.cmplibrary.core.map
import com.sourcepoint.cmplibrary.data.local.DataStorage
import com.sourcepoint.cmplibrary.data.network.NetworkClient
import com.sourcepoint.cmplibrary.data.network.converter.JsonConverter
import com.sourcepoint.cmplibrary.data.network.converter.converter
import com.sourcepoint.cmplibrary.data.network.converter.genericFail
import com.sourcepoint.cmplibrary.data.network.model.optimized.CcpaCS
import com.sourcepoint.cmplibrary.data.network.model.optimized.ConsentStatus
import com.sourcepoint.cmplibrary.data.network.model.optimized.ConsentStatusParamReq
import com.sourcepoint.cmplibrary.data.network.model.optimized.GdprCS
import com.sourcepoint.cmplibrary.data.network.model.optimized.MessagesParamReq
import com.sourcepoint.cmplibrary.data.network.model.optimized.MessagesResp
import com.sourcepoint.cmplibrary.data.network.model.optimized.MetaDataParamReq
import com.sourcepoint.cmplibrary.data.network.model.optimized.PostChoiceParamReq
import com.sourcepoint.cmplibrary.data.network.model.optimized.PvDataParamReq
import com.sourcepoint.cmplibrary.data.network.model.optimized.choice.ChoiceResp
import com.sourcepoint.cmplibrary.data.network.model.optimized.choice.GetChoiceParamReq
import com.sourcepoint.cmplibrary.data.network.model.optimized.consentStatus.ConsentStatusMetaData
import com.sourcepoint.cmplibrary.data.network.model.optimized.includeData.IncludeData
import com.sourcepoint.cmplibrary.data.network.model.optimized.messages.MessagesBodyReq
import com.sourcepoint.cmplibrary.data.network.model.optimized.messages.OperatingSystemInfoParam
import com.sourcepoint.cmplibrary.data.network.model.optimized.metaData.toChoiceMetaData
import com.sourcepoint.cmplibrary.data.network.model.optimized.metaData.toConsentStatusMetaData
import com.sourcepoint.cmplibrary.data.network.model.optimized.metaData.toMessagesMetaData
import com.sourcepoint.cmplibrary.data.network.model.optimized.postChoiceCcpaBody
import com.sourcepoint.cmplibrary.data.network.model.optimized.postChoiceGdprBody
import com.sourcepoint.cmplibrary.data.network.model.optimized.toMetadataBody
import com.sourcepoint.cmplibrary.data.network.util.Env
import com.sourcepoint.cmplibrary.exception.CampaignType.CCPA
import com.sourcepoint.cmplibrary.exception.CampaignType.GDPR
import com.sourcepoint.cmplibrary.exception.ConsentLibExceptionK
import com.sourcepoint.cmplibrary.exception.InvalidConsentResponse
import com.sourcepoint.cmplibrary.exception.Logger
import com.sourcepoint.cmplibrary.model.ConsentActionImpl
import com.sourcepoint.cmplibrary.model.CustomConsentReq
import com.sourcepoint.cmplibrary.model.exposed.ActionType
import com.sourcepoint.cmplibrary.model.exposed.GDPRPurposeGrants
import com.sourcepoint.cmplibrary.model.exposed.SPConsents
import com.sourcepoint.cmplibrary.model.toChoiceTypeParam
import com.sourcepoint.cmplibrary.util.check
import com.sourcepoint.cmplibrary.util.extensions.extractIncludeGppDataParamIfEligible
import com.sourcepoint.cmplibrary.util.extensions.toJsonObject
import com.sourcepoint.cmplibrary.util.extensions.toMapOfAny
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import org.json.JSONObject

/**
 * Factory method to create an instance of a [Service] using its implementation
 * @param nc is an instance of [NetworkClient]
 * @param dataStorage is an instance of [DataStorage]
 * @param campaignManager is an instance of [CampaignManager]
 * @param consentManagerUtils is an instance of [ConsentManagerUtils]
 * @return an instance of the [ServiceImpl] implementation
 */
internal fun Service.Companion.create(
    nc: NetworkClient,
    campaignManager: CampaignManager,
    consentManagerUtils: ConsentManagerUtils,
    dataStorage: DataStorage,
    logger: Logger,
    execManager: ExecutorManager
): Service = ServiceImpl(nc, campaignManager, consentManagerUtils, dataStorage, logger, execManager)

/**
 * Implementation os the [Service] interface
 */
private class ServiceImpl(
    private val networkClient: NetworkClient,
    private val campaignManager: CampaignManager,
    private val consentManagerUtils: ConsentManagerUtils,
    private val dataStorage: DataStorage,
    private val logger: Logger,
    private val execManager: ExecutorManager
) : Service, NetworkClient by networkClient, CampaignManager by campaignManager {

    private fun JSONArray.toArrayList(): ArrayList<String> {
        val list = arrayListOf<String>()
        for (i in 0 until this.length()) {
            list.add(this.getString(i))
        }
        return list
    }

    override fun sendCustomConsentServ(customConsentReq: CustomConsentReq, env: Env): Either<GdprCS> = check {
        networkClient.sendCustomConsent(customConsentReq, env)
            .map {
                if (campaignManager.gdprConsentStatus == null) {
                    genericFail("CustomConsent cannot be executed. Consent is missing!!!")
                }

                val categories: List<String> = (it.content.get("categories") as JSONArray).toArrayList()
                val vendors: List<String> = (it.content.get("vendors") as JSONArray).toArrayList()
                val legIntCategories: List<String> = (it.content.get("legIntCategories") as JSONArray).toArrayList()
                val specialFeatures: List<String> = (it.content.get("specialFeatures") as JSONArray).toArrayList()

                val grantsString: String = (it.content.get("grants") as JSONObject).toString()
                val grants = JsonConverter.converter.decodeFromString<Map<String, GDPRPurposeGrants>>(grantsString)
                val updatedGrants = campaignManager.gdprConsentStatus?.copy(
                    grants = grants,
                    categories = categories,
                    vendors = vendors,
                    legIntCategories = legIntCategories,
                    specialFeatures = specialFeatures
                )
                campaignManager.gdprConsentStatus = updatedGrants
            }
        campaignManager.gdprConsentStatus!!
    }

    override fun deleteCustomConsentToServ(customConsentReq: CustomConsentReq, env: Env): Either<GdprCS> = check {
        networkClient.deleteCustomConsentTo(customConsentReq, env)
            .map {
                if (campaignManager.gdprConsentStatus == null) {
                    genericFail("CustomConsent cannot be executed. Consent is missing!!!")
                }

                val categories: List<String> = (it.content.get("categories") as JSONArray).toArrayList()
                val vendors: List<String> = (it.content.get("vendors") as JSONArray).toArrayList()
                val legIntCategories: List<String> = (it.content.get("legIntCategories") as JSONArray).toArrayList()
                val specialFeatures: List<String> = (it.content.get("specialFeatures") as JSONArray).toArrayList()

                val grantsString: String = (it.content.get("grants") as JSONObject).toString()
                val grants = JsonConverter.converter.decodeFromString<Map<String, GDPRPurposeGrants>>(grantsString)
                campaignManager.gdprConsentStatus = campaignManager.gdprConsentStatus?.copy(
                    grants = grants,
                    categories = categories,
                    vendors = vendors,
                    legIntCategories = legIntCategories,
                    specialFeatures = specialFeatures
                )
            }
        campaignManager.gdprConsentStatus!!
    }

    override fun getMessages(
        authId: String?,
        pubData: JsonObject?,
        env: Env,
        onSuccess: (MessagesResp) -> Unit,
        showConsent: () -> Unit,
        onFailure: (Throwable, Boolean) -> Unit,
    ) {
        execManager.executeOnWorkerThread {
            campaignManager.authId = authId

            val metadataResponse = this.getMetaData(
                MetaDataParamReq(
                    env = env,
                    propertyId = spConfig.propertyId,
                    accountId = spConfig.accountId,
                    metadata = MetaDataParamReq.MetaDataMetaDataParam(
                        ccpa = spConfig.campaigns.find { it.campaignType == CCPA }?.let {
                            MetaDataParamReq.MetaDataMetaDataParam.MetaDataCampaign(
                                groupPmId = getGroupId(CCPA)
                            )
                        },
                        gdpr = spConfig.campaigns.find { it.campaignType == GDPR }?.let {
                            MetaDataParamReq.MetaDataMetaDataParam.MetaDataCampaign(
                                groupPmId = getGroupId(GDPR)
                            )
                        }
                    )
                )
            )
                .executeOnLeft { metaDataError ->
                    onFailure(metaDataError, true)
                    return@executeOnWorkerThread
                }
                .executeOnRight { metaDataResponse -> handleMetaDataResponse(metaDataResponse) }
                .getOrNull()

            if (authId != null || campaignManager.shouldCallConsentStatus || campaignManager.requiresNewConsentData) {
                val consentStatusParamReq = ConsentStatusParamReq(
                    env = env,
                    metadata = metadataResponse?.toConsentStatusMetaData(campaignManager)
                        ?: ConsentStatusMetaData(gdpr = null, ccpa = null),
                    propertyId = spConfig.propertyId,
                    accountId = spConfig.accountId,
                    authId = authId,
                    localState = campaignManager.messagesOptimizedLocalState,
                    includeData = IncludeData.generateIncludeDataForConsentStatus(
                        gppData = spConfig.extractIncludeGppDataParamIfEligible(),
                    ),
                )

                networkClient.getConsentStatus(consentStatusParamReq)
                    .executeOnRight { consentStatusResponse ->
                        dataStorage.updateLocalDataVersion()
                        campaignManager.apply {
                            handleOldLocalData()
                            messagesOptimizedLocalState = consentStatusResponse.localState
                            consentStatusResponse.consentStatusData?.let { consentStatusData ->
                                gdprConsentStatus = consentStatusData.gdpr
                                ccpaConsentStatus = consentStatusData.ccpa
                            }
                        }
                    }
                    .executeOnLeft { consentStatusError ->
                        onFailure(consentStatusError, true)
                        return@executeOnWorkerThread
                    }
            }

            val gdprConsentStatus = campaignManager.gdprConsentStatus?.consentStatus
            val additionsChangeDate = metadataResponse?.gdpr?.additionsChangeDate
            val legalBasisChangeDate = metadataResponse?.gdpr?.legalBasisChangeDate
            val dataRecordedConsent = campaignManager.gdprConsentStatus?.dateCreated

            if (dataRecordedConsent != null &&
                gdprConsentStatus != null &&
                additionsChangeDate != null &&
                legalBasisChangeDate != null
            ) {
                val consentStatus = consentManagerUtils.updateGdprConsent(
                    dataRecordedConsent = dataRecordedConsent,
                    gdprConsentStatus = gdprConsentStatus,
                    additionsChangeDate = additionsChangeDate,
                    legalBasisChangeDate = legalBasisChangeDate
                )
                campaignManager.gdprConsentStatus = campaignManager.gdprConsentStatus?.copy(consentStatus = consentStatus)
            }

            if (campaignManager.shouldCallMessages) {
                getMessages(
                    MessagesParamReq(
                        env = env,
                        body = MessagesBodyReq(
                            accountId = spConfig.accountId,
                            propertyHref = "https://${spConfig.propertyName}",
                            campaigns = campaignManager.campaigns4Config.toMetadataBody(
                                gdprConsentStatus = campaignManager.gdprConsentStatus?.consentStatus,
                                ccpaConsentStatus = campaignManager.ccpaConsentStatus?.status?.name,
                            ),
                            campaignEnv = campaignManager.spConfig.campaignsEnv.env,
                            consentLanguage = spConfig.messageLanguage.value,
                            hasCSP = false,
                            includeData = IncludeData.generateIncludeDataForMessages(
                                gppData = spConfig.extractIncludeGppDataParamIfEligible(),
                            ),
                            localState = campaignManager.messagesOptimizedLocalState?.jsonObject
                                ?: JsonObject(mapOf()),
                            operatingSystem = OperatingSystemInfoParam(),
                        ),
                        metadata = metadataResponse!!.toMessagesMetaData(),
                        nonKeyedLocalState = campaignManager.nonKeyedLocalState?.jsonObject,
                    )
                )
                    .executeOnLeft { messagesError ->
                        onFailure(messagesError, true)
                        return@executeOnWorkerThread
                    }
                    .executeOnRight { messagesResponse ->
                        campaignManager.also { _ ->
                            messagesOptimizedLocalState = messagesResponse.localState
                            nonKeyedLocalState = messagesResponse.nonKeyedLocalState
                            gdprMessageMetaData = messagesResponse.campaigns?.gdpr?.messageMetaData
                            ccpaMessageMetaData = messagesResponse.campaigns?.ccpa?.messageMetaData
                        }

                        if (campaignManager.gdprConsentStatus == null) {
                            messagesResponse.campaigns?.gdpr?.let { gdpr ->
                                dataStorage.tcData = gdpr.TCData?.toMapOfAny()
                                campaignManager.gdprConsentStatus = gdpr.toGdprCS()
                            }
                        }

                        if (campaignManager.ccpaConsentStatus == null) {
                            messagesResponse.campaigns?.ccpa?.let { ccpa ->
                                dataStorage.gppData = ccpa.gppData?.toMapOfAny()
                                campaignManager.ccpaConsentStatus = ccpa.toCcpaCS()
                            }
                        }

                        execManager.executeOnMain { onSuccess(messagesResponse) }
                    }
            } else {
                execManager.executeOnMain { showConsent() }
            }

            val isGdprInConfig = spConfig.campaigns.find { it.campaignType == GDPR } != null

            logger.computation(
                tag = "PvData condition GdprSample",
                msg = """
                    isGdprInConfig[$isGdprInConfig]
                    shouldTriggerByGdprSample[${consentManagerUtils.shouldTriggerByGdprSample}]
                    res[${consentManagerUtils.shouldTriggerByGdprSample && isGdprInConfig}]
                """.trimIndent()
            )

            if (consentManagerUtils.shouldTriggerByGdprSample && isGdprInConfig) {
                metadataResponse?.gdpr?.let { gdpr ->
                    postPvData(
                        PvDataParamReq(
                            env = env,
                            body = PvDataParamReq.Body(
                                gdpr = PvDataParamReq.Body.GDPR(
                                    uuid = campaignManager.gdprConsentStatus?.uuid,
                                    euconsent = campaignManager.gdprConsentStatus?.euconsent,
                                    accountId = spConfig.accountId,
                                    pubData = pubData,
                                    applies = gdpr.applies,
                                    siteId = spConfig.propertyId,
                                    consentStatus = campaignManager.gdprConsentStatus?.consentStatus
                                        ?: ConsentStatus(),
                                    msgId = gdprMessageMetaData?.messageId,
                                    categoryId = gdprMessageMetaData?.categoryId?.code,
                                    subCategoryId = gdprMessageMetaData?.subCategoryId?.code,
                                    prtnUUID = gdprMessageMetaData?.prtnUUID,
                                    sampleRate = metaDataResp?.gdpr?.sampleRate
                                )
                            ),
                            campaignType = GDPR
                        )
                    )
                        .executeOnLeft { gdprPvDataError ->
                            onFailure(gdprPvDataError, false)
                            return@executeOnWorkerThread
                        }
                        .executeOnRight { pvDataResponse ->
                            campaignManager.gdprConsentStatus = campaignManager.gdprConsentStatus?.copy(
                                uuid = pvDataResponse.gdpr?.uuid
                            )
                        }
                }
            }

            val isCcpaInConfig = spConfig.campaigns.find { it.campaignType == CCPA } != null

            logger.computation(
                tag = "PvData condition CcpaSample",
                msg = """
                    isCcpaInConfig[$isCcpaInConfig]
                    shouldTriggerByCcpaSample[${consentManagerUtils.shouldTriggerByCcpaSample}]
                    res[${consentManagerUtils.shouldTriggerByCcpaSample && isCcpaInConfig}]
                """.trimIndent()
            )

            if (consentManagerUtils.shouldTriggerByCcpaSample && isCcpaInConfig) {
                metaDataResp?.ccpa?.let { ccpa ->
                    postPvData(
                        PvDataParamReq(
                            env = env,
                            body = PvDataParamReq.Body(
                                ccpa = PvDataParamReq.Body.CCPA(
                                    uuid = campaignManager.ccpaConsentStatus?.uuid,
                                    accountId = spConfig.accountId,
                                    pubData = pubData,
                                    applies = ccpa.applies,
                                    siteId = spConfig.propertyId,
                                    consentStatus = PvDataParamReq.Body.CCPA.ConsentStatus(
                                        hasConsentData = campaignManager.ccpaConsentStatus?.uuid != null,
                                        rejectedCategories = campaignManager.ccpaConsentStatus?.rejectedCategories ?: emptyList(),
                                        rejectedVendors = campaignManager.ccpaConsentStatus?.rejectedVendors ?: emptyList()
                                    ),
                                    messageId = ccpaMessageMetaData?.messageId,
                                    sampleRate = ccpa.sampleRate
                                )
                            ),
                            campaignType = CCPA
                        )
                    )
                        .executeOnLeft { ccpaPvDataError ->
                            onFailure(ccpaPvDataError, false)
                            return@executeOnWorkerThread
                        }
                        .executeOnRight { pvDataResponse ->
                            campaignManager.ccpaConsentStatus = campaignManager.ccpaConsentStatus?.copy(
                                uuid = pvDataResponse.ccpa?.uuid
                            )
                        }
                }
            }
        }
    }

    override fun sendConsent(
        consentActionImpl: ConsentActionImpl,
        env: Env,
        sPConsentsSuccess: ((SPConsents) -> Unit)?,
        pmId: String?
    ): Either<ChoiceResp> {
        return when (consentActionImpl.campaignType) {
            GDPR -> {
                sendConsentGdpr(
                    consentActionImpl,
                    env,
                    sPConsentsSuccess
                ).map { ChoiceResp(gdpr = it) }
            }
            CCPA -> {
                sendConsentCcpa(
                    consentActionImpl,
                    env,
                    sPConsentsSuccess
                ).map { ChoiceResp(ccpa = it) }
            }
        }
    }

    fun sendConsentGdpr(
        consentActionImpl: ConsentActionImpl,
        env: Env,
        sPConsentsSuccess: ((SPConsents) -> Unit)?
    ): Either<GdprCS> = check {

        var getResp: ChoiceResp? = null

        val actionType = consentActionImpl.actionType
        if (actionType == ActionType.ACCEPT_ALL || actionType == ActionType.REJECT_ALL) {
            getResp = networkClient.getChoice(
                GetChoiceParamReq(
                    choiceType = consentActionImpl.actionType.toChoiceTypeParam(),
                    accountId = spConfig.accountId.toLong(),
                    propertyId = spConfig.propertyId.toLong(),
                    env = env,
                    metadataArg = campaignManager.metaDataResp?.toChoiceMetaData()?.copy(ccpa = null),
                    includeData = IncludeData.generateIncludeDataForGetChoice(
                        gppData = spConfig.extractIncludeGppDataParamIfEligible(),
                    ),
                    hasCsp = true,
                    includeCustomVendorsRes = false,
                    withSiteActions = false,
                )
            )
                .executeOnRight { response ->
                    response.gdpr?.let { responseGdpr ->
                        campaignManager.gdprConsentStatus = responseGdpr.copy(uuid = campaignManager.gdprUuid)
                        val spConsents = ConsentManager.responseConsentHandler(
                            campaignManager.gdprConsentStatus,
                            consentManagerUtils
                        )
                        sPConsentsSuccess?.invoke(spConsents)
                    }
                }
                .executeOnLeft { error ->
                    (error as? ConsentLibExceptionK)?.let { logger.error(error) }
                }
                .getOrNull()
        }

        networkClient.storeGdprChoice(
            PostChoiceParamReq(
                env = env,
                actionType = consentActionImpl.actionType,
                body = postChoiceGdprBody(
                    sampleRate = dataStorage.gdprSamplingValue,
                    propertyId = spConfig.propertyId.toLong(),
                    messageId = campaignManager.gdprMessageMetaData?.messageId?.toLong(),
                    granularStatus = campaignManager.gdprConsentStatus?.consentStatus?.granularStatus,
                    consentAllRef = getResp?.gdpr?.consentAllRef,
                    vendorListId = getResp?.gdpr?.vendorListId,
                    saveAndExitVariables = consentActionImpl.saveAndExitVariablesOptimized,
                    authid = authId,
                    uuid = campaignManager.gdprUuid,
                    sendPvData = dataStorage.gdprSamplingResult,
                    pubData = consentActionImpl.pubData.toJsonObject(),
                )
            )
        )
            .executeOnRight { postConsentResponse ->
                campaignManager.gdprConsentStatus = campaignManager.gdprConsentStatus?.copy(
                    uuid = postConsentResponse.uuid,
                    dateCreated = postConsentResponse.dateCreated
                )

                // Only overwrite consents if the action is Save & Exit, because the other actions
                // will contain empty values for consent.
                if (actionType == ActionType.SAVE_AND_EXIT) {
                    campaignManager.gdprConsentStatus = campaignManager.gdprConsentStatus?.copy(
                        TCData = postConsentResponse.TCData,
                        euconsent = postConsentResponse.euconsent,
                        grants = postConsentResponse.grants,
                        webConsentPayload = postConsentResponse.webConsentPayload,
                        vendors = postConsentResponse.vendors,
                        categories = postConsentResponse.categories
                    )
                    val spConsents = ConsentManager.responseConsentHandler(campaignManager.gdprConsentStatus, consentManagerUtils)
                    sPConsentsSuccess?.invoke(spConsents)
                }
            }
            .executeOnLeft { error ->
                (error as? ConsentLibExceptionK)?.let { logger.error(error) }
            }

        campaignManager.gdprConsentStatus ?: throw InvalidConsentResponse(
            cause = null,
            "The GDPR consent object cannot be null!!!"
        )
    }

    fun sendConsentCcpa(
        consentActionImpl: ConsentActionImpl,
        env: Env,
        sPConsentsSuccess: ((SPConsents) -> Unit)?
    ): Either<CcpaCS> = check {
        val actionType = consentActionImpl.actionType
        if (actionType == ActionType.ACCEPT_ALL || actionType == ActionType.REJECT_ALL) {
            networkClient.getChoice(
                GetChoiceParamReq(
                    choiceType = actionType.toChoiceTypeParam(),
                    accountId = spConfig.accountId.toLong(),
                    propertyId = spConfig.propertyId.toLong(),
                    env = env,
                    metadataArg = campaignManager.metaDataResp?.toChoiceMetaData()?.copy(gdpr = null),
                    includeData = IncludeData.generateIncludeDataForGetChoice(
                        gppData = spConfig.extractIncludeGppDataParamIfEligible(),
                    ),
                    hasCsp = true,
                    includeCustomVendorsRes = false,
                    withSiteActions = false,
                )
            )
                .executeOnRight { ccpaResponse ->
                    campaignManager.ccpaConsentStatus = ccpaResponse.ccpa?.copy(uuid = campaignManager.ccpaConsentStatus?.uuid)
                    val spConsents = ConsentManager.responseConsentHandler(
                        campaignManager.ccpaConsentStatus,
                        consentManagerUtils
                    )
                    sPConsentsSuccess?.invoke(spConsents)
                }
                .executeOnLeft { error ->
                    (error as? ConsentLibExceptionK)?.let { logger.error(error) }
                }
        }

        networkClient.storeCcpaChoice(
            PostChoiceParamReq(
                env = env,
                actionType = actionType,
                body = postChoiceCcpaBody(
                    sampleRate = dataStorage.ccpaSamplingValue,
                    propertyId = spConfig.propertyId.toLong(),
                    messageId = campaignManager.ccpaMessageMetaData?.messageId?.toLong(),
                    saveAndExitVariables = consentActionImpl.saveAndExitVariablesOptimized,
                    authid = authId,
                    uuid = campaignManager.ccpaConsentStatus?.uuid,
                    sendPvData = dataStorage.ccpaSamplingResult,
                    pubData = consentActionImpl.pubData.toJsonObject(),
                    includeData = IncludeData.generateIncludeDataForGetChoice(
                        gppData = spConfig.extractIncludeGppDataParamIfEligible(),
                    ),
                )
            )
        )
            .executeOnRight { postConsentResponse ->
                campaignManager.ccpaConsentStatus = campaignManager.ccpaConsentStatus?.copy(
                    uuid = postConsentResponse.uuid,
                    dateCreated = postConsentResponse.dateCreated,
                    consentedAll = postConsentResponse.consentedAll,
                    rejectedAll = postConsentResponse.rejectedAll,
                    status = postConsentResponse.status,
                    rejectedCategories = postConsentResponse.rejectedCategories,
                    rejectedVendors = postConsentResponse.rejectedVendors,
                    gppData = postConsentResponse.GPPData
                )

                if (actionType == ActionType.SAVE_AND_EXIT) {
                    campaignManager.ccpaConsentStatus = campaignManager.ccpaConsentStatus?.copy(
                        webConsentPayload = postConsentResponse.webConsentPayload
                    )
                    val spConsents = ConsentManager.responseConsentHandler(campaignManager.ccpaConsentStatus, consentManagerUtils)
                    sPConsentsSuccess?.invoke(spConsents)
                }
            }
            .executeOnLeft { error ->
                (error as? ConsentLibExceptionK)?.let { logger.error(error) }
            }

        campaignManager.ccpaConsentStatus ?: throw InvalidConsentResponse(
            cause = null,
            "The CCPA consent object cannot be null!!!"
        )
    }
}
