package com.sourcepoint.app.v6

import android.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.uitestutil.* // ktlint-disable
import com.sourcepoint.app.v6.TestUseCase.Companion.checkSwitchOnWebView
import com.sourcepoint.app.v6.TestUseCase.Companion.clickOnClearConsent
import com.sourcepoint.app.v6.TestUseCase.Companion.clickOnGdprReviewConsent
import com.sourcepoint.app.v6.TestUseCase.Companion.clickOnOttCcpaReviewConsent
import com.sourcepoint.app.v6.TestUseCase.Companion.mockModule
import com.sourcepoint.app.v6.TestUseCase.Companion.tapAcceptAllOnWebView
import com.sourcepoint.app.v6.TestUseCase.Companion.tapSaveAndExitUPWebView
import com.sourcepoint.app.v6.TestUseCase.Companion.turnOnSwitchOnWebView
import com.sourcepoint.cmplibrary.SpClient
import com.sourcepoint.cmplibrary.creation.config
import com.sourcepoint.cmplibrary.data.network.util.CampaignsEnv
import com.sourcepoint.cmplibrary.exception.CampaignType
import com.sourcepoint.cmplibrary.model.MessageLanguage
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules

@RunWith(AndroidJUnit4ClassRunner::class)
class MainActivityKotlinOttTest {

    lateinit var scenario: ActivityScenario<MainActivityKotlin>

    private val device by lazy { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()) }

    @After
    fun cleanup() {
        if (this::scenario.isLateinit) scenario.close()
    }

    private val spConfOtt = config {
        accountId = 22
        propertyName = "ott.test.suite"
        campaignsEnv = CampaignsEnv.PUBLIC
        messLanguage = MessageLanguage.ENGLISH
        messageTimeout = 3000
        +(CampaignType.GDPR)
    }

    private val spConfOttCcpa = config {
        accountId = 22
        propertyName = "ott-ccpa-22"
        campaignsEnv = CampaignsEnv.PUBLIC
        messLanguage = MessageLanguage.ENGLISH
        messageTimeout = 3000
        +(CampaignType.CCPA)
    }

    @Test
    fun GIVEN_an_OTT_campaign_SHOW_message_and_ACCEPT_ALL() = runBlocking<Unit> {

        val spClient = mockk<SpClient>(relaxed = true)

        loadKoinModules(
            mockModule(
                spConfig = spConfOtt,
                gdprPmId = "579231",
                ccpaPmId = "1",
                spClientObserver = listOf(spClient)
            )
        )

        scenario = launchActivity()

        periodicWr(period = 3000, backup = { scenario.recreateAndResume() }) {
            tapAcceptAllOnWebView()
            device.pressEnter()
        }

        verify(exactly = 0) { spClient.onError(any()) }
        wr { verify(exactly = 1) { spClient.onConsentReady(any()) } }
        verify { spClient.onAction(any(), withArg { it.pubData["pb_key"].assertEquals("pb_value") }) }

        wr {
            verify {
                spClient.run {
                    onUIReady(any())
                    onUIFinished(any())
                    onAction(any(), any())
                    onConsentReady(any())
                }
            }
        }


        scenario.onActivity { activity ->
            val IABTCF_TCString = PreferenceManager.getDefaultSharedPreferences(activity)
                .getString("IABTCF_TCString", null)
            IABTCF_TCString.assertNotNull()
        }

    }

    @Test
    fun GIVEN_an_OTT_campaign_SHOW_message_and_ACCEPT_ALL_from_PM() = runBlocking<Unit> {

        val spClient = mockk<SpClient>(relaxed = true)

        loadKoinModules(
            mockModule(
                spConfig = spConfOtt,
                gdprPmId = "579231",
                ccpaPmId = "1",
                spClientObserver = listOf(spClient)
            )
        )

        scenario = launchActivity()

        periodicWr(period = 3000, backup = { scenario.recreateAndResume() }) {
            tapAcceptAllOnWebView()
            device.pressEnter()
        }

        wr { clickOnGdprReviewConsent() }
        wr(backup = { clickOnGdprReviewConsent() }) {
            tapAcceptAllOnWebView()
            device.pressEnter()
        }

        verify(exactly = 0) { spClient.onError(any()) }
        wr { verify(atLeast = 2) { spClient.onConsentReady(any()) } }
        verify { spClient.onAction(any(), withArg { it.pubData["pb_key"].assertEquals("pb_value") }) }

        wr {
            verify {
                spClient.run {
                    onUIReady(any())
                    onUIFinished(any())
                    onAction(any(), any())
                    onConsentReady(any())
                }
            }
        }


        scenario.onActivity { activity ->
            val IABTCF_TCString = PreferenceManager.getDefaultSharedPreferences(activity)
                .getString("IABTCF_TCString", null)
            IABTCF_TCString.assertNotNull()
        }

    }

    @Test
    fun GIVEN_an_OTT_campaign_ACCEPT_ALL_from_PM() = runBlocking<Unit> {

        val spClient = mockk<SpClient>(relaxed = true)

        loadKoinModules(
            mockModule(
                spConfig = spConfOttCcpa,
                gdprPmId = "1",
                ccpaPmId = "756686",
                spClientObserver = listOf(spClient)
            )
        )

        scenario = launchActivity()

        wr { verify(atLeast = 1) { spClient.onConsentReady(any()) } }
        // test execution with the loadMessageCall (in the onResume)
        ccpaOttTextSteps(spClient)
        // clear data
        clickOnClearConsent()
        // test execution without the loadMessageCall
        ccpaOttTextSteps(spClient)
    }

    private suspend fun ccpaOttTextSteps(spClient : SpClient){
        wr { clickOnOttCcpaReviewConsent() }
        wr(backup = { clickOnOttCcpaReviewConsent() }) {
            turnOnSwitchOnWebView()
            device.pressEnter()
            tapSaveAndExitUPWebView()
            device.pressEnter()
        }

        verify(exactly = 0) { spClient.onError(any()) }

        wr {
            verify {
                spClient.run {
                    onUIReady(any())
                    onUIFinished(any())
                    onAction(any(), any())
                    onConsentReady(any())
                }
            }
        }

        scenario.onActivity { activity ->
            PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getString("IABUSPrivacy_String", null)
                .assertEquals("1YYN")
        }

        clickOnOttCcpaReviewConsent()
        wr(backup = { clickOnOttCcpaReviewConsent() }) {
            checkSwitchOnWebView(true)
            device.pressEnter()
        }
    }

}