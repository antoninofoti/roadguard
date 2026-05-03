package com.example.roadguard.ml

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.roadguard.data.local.RoadGuardDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PersonalizationViewModel(
    private val fusionManager: FederatedFusionManager,
    private val database: RoadGuardDatabase
) : ViewModel() {

    private val _weights = MutableStateFlow(fusionManager.getPersonalizedWeights())
    val weights: StateFlow<Triple<Float, Float, Float>> = _weights.asStateFlow()

    private val _localF1 = MutableStateFlow(fusionManager.getLocalF1())
    val localF1: StateFlow<Float> = _localF1.asStateFlow()

    private val _isPersonalizationEnabled = MutableStateFlow(fusionManager.isPersonalizationEnabled())
    val isPersonalizationEnabled: StateFlow<Boolean> = _isPersonalizationEnabled.asStateFlow()

    fun refreshData() {
        _weights.value = fusionManager.getPersonalizedWeights()
        _localF1.value = fusionManager.getLocalF1()
        _isPersonalizationEnabled.value = fusionManager.isPersonalizationEnabled()
    }

    class Factory(
        private val fusionManager: FederatedFusionManager,
        private val database: RoadGuardDatabase
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonalizationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PersonalizationViewModel(fusionManager, database) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
