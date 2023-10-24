package com.sourcepoint.cmplibrary.consent

import com.sourcepoint.cmplibrary.core.ExecutorManager
import com.sourcepoint.cmplibrary.core.executeOnLeft
import com.sourcepoint.cmplibrary.core.executeOnRight
import com.sourcepoint.cmplibrary.core.getOrNull
import com.sourcepoint.cmplibrary.data.Service
import com.sourcepoint.cmplibrary.data.local.DataStorage
import com.sourcepoint.cmplibrary.data.network.model.optimized.CcpaCS
import com.sourcepoint.cmplibrary.data.network.model.optimized.GdprCS
import com.sourcepoint.cmplibrary.data.network.model.optimized.toCCPAConsentInternal
import com.sourcepoint.cmplibrary.data.network.model.optimized.toGDPRUserConsent
import com.sourcepoint.cmplibrary.data.network.util.Env
import com.sourcepoint.cmplibrary.exception.Logger
import com.sourcepoint.cmplibrary.model.ConsentActionImpl
import com.sourcepoint.cmplibrary.model.NativeConsentAction
import com.sourcepoint.cmplibrary.model.exposed.SPCCPAConsent
import com.sourcepoint.cmplibrary.model.exposed.SPConsents
import com.sourcepoint.cmplibrary.model.exposed.SPGDPRConsent
import com.sourcepoint.cmplibrary.model.toConsentAction
import com.sourcepoint.cmplibrary.util.check

internal interface ConsentManager {
    fun enqueueConsent(consentActionImpl: ConsentActionImpl)
    fun enqueueConsent(nativeConsentAction: NativeConsentAction)
    fun sendStoredConsentToClient()
    fun sendConsent(
        actionImpl: ConsentActionImpl
    )

    var sPConsentsSuccess: ((SPConsents) -> Unit)?
    var sPConsentsError: ((Throwable) -> Unit)?

    val storedConsent: Boolean

    companion object {

        internal fun responseConsentHandler(
            gdpr: GdprCS?,
            consentManagerUtils: ConsentManagerUtils
        ): SPConsents {
            val ccpaCached = consentManagerUtils.ccpaConsentOptimized.getOrNull()
            return SPConsents(
                gdpr = gdpr?.let { SPGDPRConsent(it.toGDPRUserConsent()) },
                ccpa = ccpaCached?.let { cc -> SPCCPAConsent(consent = cc) }
            )
        }

        internal fun responseConsentHandler(
            ccpa: CcpaCS?,
            consentManagerUtils: ConsentManagerUtils
        ): SPConsents {
            val gdprCached = consentManagerUtils.gdprConsentOptimized.getOrNull()
            return SPConsents(
                gdpr = gdprCached?.let { gc -> SPGDPRConsent(consent = gc) },
                ccpa = ccpa?.let { SPCCPAConsent(it.toCCPAConsentInternal()) },
            )
        }
    }
}

internal fun ConsentManager.Companion.create(
    service: Service,
    consentManagerUtils: ConsentManagerUtils,
    env: Env,
    logger: Logger,
    dataStorage: DataStorage,
    executorManager: ExecutorManager,
    clientEventManager: ClientEventManager
): ConsentManager =
    ConsentManagerImpl(service, consentManagerUtils, logger, env, dataStorage, executorManager, clientEventManager)

private class ConsentManagerImpl(
    private val service: Service,
    private val consentManagerUtils: ConsentManagerUtils,
    private val logger: Logger,
    private val env: Env,
    private val dataStorage: DataStorage,
    private val executorManager: ExecutorManager,
    private val clientEventManager: ClientEventManager
) : ConsentManager {

    override var sPConsentsSuccess: ((SPConsents) -> Unit)? = null
    override var sPConsentsError: ((Throwable) -> Unit)? = null

    override val storedConsent: Boolean
        get() = dataStorage.getCcpaConsentResp() != null ||
            dataStorage.getGdprConsentResp() != null

    override fun enqueueConsent(consentActionImpl: ConsentActionImpl) {
        sendConsent(consentActionImpl)
    }

    override fun enqueueConsent(nativeConsentAction: NativeConsentAction) {
        sendConsent(nativeConsentAction.toConsentAction())
    }

    override fun sendStoredConsentToClient() {
        check {
            val ccpaCached = consentManagerUtils.ccpaConsentOptimized.getOrNull()
            val gdprCached = consentManagerUtils.gdprConsentOptimized.getOrNull()
            SPConsents(
                gdpr = gdprCached?.let { gc -> SPGDPRConsent(consent = gc) },
                ccpa = ccpaCached?.let { cc -> SPCCPAConsent(consent = cc) }
            ).let { sPConsentsSuccess?.invoke(it) }
        }
    }

    override fun sendConsent(actionImpl: ConsentActionImpl) {
        executorManager.executeOnSingleThread {
            service.sendConsent(actionImpl, env, sPConsentsSuccess, actionImpl.privacyManagerId)
                .executeOnLeft { sPConsentsError?.invoke(it) }
                .executeOnRight {
                    clientEventManager.registerConsentResponse()
                }
        }
    }
}
