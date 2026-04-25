package com.coparenting.chronicle.horizon.domain.usecase.analytics

import com.coparenting.chronicle.horizon.domain.model.AnalyticsReport
import com.coparenting.chronicle.horizon.domain.repository.AnalyticsRepository
import javax.inject.Inject

class GenerateAnalyticsReportUseCase @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) {
    suspend operator fun invoke(userId: String): AnalyticsReport {
        return analyticsRepository.getAnalyticsReport(userId)
    }
}
