package com.sourcepoint.cmplibrary.model

import com.sourcepoint.cmplibrary.data.network.util.CampaignsEnv
import com.sourcepoint.cmplibrary.exception.CampaignType
import com.sourcepoint.cmplibrary.model.exposed.TargetingParam

internal data class Campaigns(val list: List<CampaignReq> = emptyList())

internal interface CampaignReq {
    val targetingParams: List<TargetingParam>
    val campaignsEnv: CampaignsEnv
    val campaignType: CampaignType
    val groupPmId: String?
}

internal data class CampaignReqImpl(
    override val targetingParams: List<TargetingParam>,
    override val campaignsEnv: CampaignsEnv,
    override val campaignType: CampaignType,
    override val groupPmId: String? = null
) : CampaignReq

data class DataType(val type: String)
data class IncludeData(
    val localState: DataType = DataType("RecordString"),
    val tCData: DataType = DataType("RecordString"),
    val campaigns: DataType = DataType("RecordString"),
    val customVendorsResponse: DataType = DataType("RecordString"),
    val messageMetaData: DataType = DataType("RecordString")
)
