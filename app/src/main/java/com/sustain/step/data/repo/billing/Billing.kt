package com.sustain.step.data.repo.billing
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class Billing {

    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _billingFlow = MutableSharedFlow<BillingState>(replay = 0)
    val billingFlow = _billingFlow.asSharedFlow()

    fun startBilling(fragmentActivity: FragmentActivity) {
        emitState(BillingState.Purchasing)
        billingScope.launch {
            delay(1000)
            emitState(BillingState.Purchased)
        }
    }

    fun restorePurchases() {
        // no-op for demo billing flow
    }

    fun close() {
        // no-op for demo billing flow
    }

    private fun emitState(state: BillingState) {
        billingScope.launch {
            _billingFlow.emit(state)
        }
    }
}


sealed class BillingState {
    object Idle : BillingState()
    object Purchasing : BillingState()
    object Purchased : BillingState()
    data class Error(val message: String) : BillingState()
}
