package com.sourcepointmeta.metaapp

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.example.uitestutil.wr
import com.sourcepointmeta.metaapp.TestUseCaseMeta.Companion.addTestProperty
import com.sourcepointmeta.metaapp.TestUseCaseMeta.Companion.checkDeepLinkDisplayed
import com.sourcepointmeta.metaapp.TestUseCaseMeta.Companion.checkNumberOfNullMessage
import com.sourcepointmeta.metaapp.TestUseCaseMeta.Companion.checkOnConsentReady
import com.sourcepointmeta.metaapp.TestUseCaseMeta.Companion.clickOnGdprReviewConsent
import com.sourcepointmeta.metaapp.TestUseCaseMeta.Companion.runDemo
import com.sourcepointmeta.metaapp.TestUseCaseMeta.Companion.saveProperty
import com.sourcepointmeta.metaapp.TestUseCaseMeta.Companion.swipeLeftPager
import com.sourcepointmeta.metaapp.TestUseCaseMeta.Companion.tapFab
import com.sourcepointmeta.metaapp.TestUseCaseMeta.Companion.tapMetaDeepLinkOnWebView
import com.sourcepointmeta.metaapp.data.localdatasource.createDb
import com.sourcepointmeta.metaapp.db.MetaAppDB
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.qualifier.named
import org.koin.dsl.module

@RunWith(AndroidJUnit4ClassRunner::class)
class MainActivityTest {

    lateinit var scenario: ActivityScenario<MainActivity>
    private val appContext by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
    private val db by lazy<MetaAppDB> { createDb(appContext) }

    @After
    fun cleanup() {
        if (this::scenario.isLateinit) scenario.close()
    }

    @Before
    fun setup() {
        db.campaignQueries.run {
            deleteAllProperties()
            deleteStatusCampaign()
        }
    }

    @Test
    fun INSERT_a_property_and_VERIFY_the_data() = runBlocking<Unit> {
        scenario = launchActivity()

        tapFab()
        addTestProperty()
        saveProperty()
    }

    @Test
    fun GIVEN_an_authId_VERIFY_no_first_layer_mess_gets_called() = runBlocking<Unit> {
        loadKoinModules(
            module(override = true) {
                single(qualifier = named("ui_test_running")) { true }
            }
        )
        scenario = launchActivity()

        db.addTestProperty(autId = "test")

        runDemo()
        wr { checkNumberOfNullMessage() }
        wr { checkOnConsentReady() }
    }

    @Test
    fun GIVEN_an_deepLink_SHOW_the_deep_link_activity() = runBlocking<Unit> {
        loadKoinModules(
            module(override = true) {
                single(qualifier = named("ui_test_running")) { true }
            }
        )
        scenario = launchActivity()

        db.addTestProperty(autId = "test")

        runDemo()
        wr { checkOnConsentReady() }
        wr(delay = 200) { swipeLeftPager() }
        wr { clickOnGdprReviewConsent() }
        wr(backup = { clickOnGdprReviewConsent() }) { tapMetaDeepLinkOnWebView() }
        wr { checkDeepLinkDisplayed() }
    }
}
