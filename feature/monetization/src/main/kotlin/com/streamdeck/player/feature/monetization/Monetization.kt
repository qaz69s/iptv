package com.streamdeck.player.feature.monetization

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monetization boundary (spec §4.10). Business code depends only on these interfaces; the shipped build binds the
 * no-op implementations below, i.e. **ads and in-app purchases are off by default**. A store build can provide
 * AdMob/UMP and Play Billing v7 implementations in this module without touching any feature.
 */
interface AdsProvider {
    val enabled: Boolean

    /** Small banner for browsing screens. Must never be placed on the player screen. */
    @Composable
    fun Banner(modifier: Modifier)

    /** GDPR/UMP consent; must be resolved before any ad request. */
    suspend fun requestConsent(activity: Activity): ConsentStatus
}

enum class ConsentStatus { NOT_REQUIRED, GRANTED, DENIED, UNKNOWN }

sealed interface PurchaseResult {
    data object Purchased : PurchaseResult
    data object Cancelled : PurchaseResult
    data object Unavailable : PurchaseResult
    data class Failed(val reason: String) : PurchaseResult
}

interface BillingProvider {
    /** True when ads are removed / premium features are unlocked. */
    val isPremium: StateFlow<Boolean>
    val available: Boolean
    suspend fun purchaseRemoveAds(activity: Activity): PurchaseResult
    suspend fun restorePurchases(): Boolean
}

@Singleton
class NoAdsProvider @Inject constructor() : AdsProvider {
    override val enabled = false

    @Composable
    override fun Banner(modifier: Modifier) = Unit

    override suspend fun requestConsent(activity: Activity) = ConsentStatus.NOT_REQUIRED
}

@Singleton
class NoBillingProvider @Inject constructor() : BillingProvider {
    private val premium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = premium.asStateFlow()
    override val available = false
    override suspend fun purchaseRemoveAds(activity: Activity): PurchaseResult = PurchaseResult.Unavailable
    override suspend fun restorePurchases() = false
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MonetizationModule {
    @Binds abstract fun ads(impl: NoAdsProvider): AdsProvider
    @Binds abstract fun billing(impl: NoBillingProvider): BillingProvider
}
