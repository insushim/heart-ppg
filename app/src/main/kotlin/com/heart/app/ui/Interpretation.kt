package com.heart.app.ui

import com.heart.core.model.AutonomicBalance
import com.heart.core.model.MeasurementResult
import com.heart.core.model.SignalQuality

/**
 * Plain-language, non-diagnostic interpretation of a measurement.
 * Ranges are general adult references for context only — not medical advice.
 */
object Interpretation {

    fun heartRate(bpm: Double): String = when {
        bpm < 50 -> "안정 시 매우 낮은 편(50 미만)입니다. 운동을 많이 하는 분은 정상일 수 있으나, 어지럼·피로가 같이 있으면 전문의 상담을 권합니다."
        bpm < 60 -> "안정 시 다소 낮은 편(서맥 경계)입니다. 컨디션이 좋은 상태이거나 휴식 중일 수 있습니다."
        bpm <= 100 -> "성인 안정 시 정상 범위(60~100)입니다."
        bpm <= 120 -> "약간 높습니다. 카페인·긴장·활동 직후일 수 있어요. 5분 쉬고 다시 재보세요."
        else -> "높은 편(120 초과)입니다. 안정 상태에서 측정한 게 맞는지 확인하고, 반복되면 전문의 상담을 권합니다."
    }

    fun rmssd(ms: Double): String = when {
        ms <= 0 -> "변이도를 계산할 만큼 비트가 충분치 않았습니다."
        ms < 20 -> "심박변이도(HRV)가 낮은 편입니다. 피로·스트레스·수면부족일 때 흔히 낮아집니다. (개인차가 크니 추세로 보세요.)"
        ms < 45 -> "심박변이도가 보통 범위입니다."
        else -> "심박변이도가 높은 편입니다. 보통 회복·이완이 잘 된 상태와 연관됩니다."
    }

    fun autonomic(b: AutonomicBalance): String = when (b) {
        AutonomicBalance.BALANCED -> "부교감(이완) 활동이 우세 — 안정·회복 상태로 해석됩니다."
        AutonomicBalance.MODERATE -> "교감/부교감이 중간 정도입니다."
        AutonomicBalance.ELEVATED -> "교감(긴장) 쪽으로 기운 편 — 스트레스·각성·카페인 등의 영향일 수 있습니다."
        AutonomicBalance.UNKNOWN -> "판정에 필요한 데이터가 부족했습니다."
    }

    fun perfusion(pi: Double): String = when {
        pi < 0.5 -> "관류(혈류)·접촉 신호가 약합니다. 손가락을 더 밀착하고 따뜻하게 한 뒤 재측정하면 정확도가 올라갑니다."
        pi < 4 -> "관류 신호 양호 — 측정 접촉이 잘 된 편입니다."
        else -> "관류 신호가 강합니다(접촉 양호)."
    }

    fun respiration(rpm: Double?): String = when {
        rpm == null -> "호흡수는 안정·정지·침묵 조건이 충족될 때만 추정됩니다. 이번엔 추정하지 않았습니다."
        rpm < 12 -> "호흡이 느린 편입니다(정상 12~20). 깊게 쉬는 중일 수 있어요."
        rpm <= 20 -> "정상 호흡 범위(12~20)입니다."
        else -> "호흡이 빠른 편입니다(정상 12~20)."
    }

    /** One-line overall takeaway, factoring measurement quality. */
    fun overall(r: MeasurementResult): String {
        if (r.quality == SignalQuality.POOR) {
            return "신호 품질이 낮아 수치를 신뢰하기 어렵습니다. 손가락을 렌즈에 단단히 밀착하고 움직이지 않은 채 다시 측정해 주세요."
        }
        if (r.irregularRhythm) {
            return "맥박 간격이 불규칙하게 감지됐습니다. 진단이 아니며, 반복되면 심전도(ECG) 확인과 전문의 상담을 권합니다."
        }
        val hrOk = r.bpm in 60.0..100.0
        return if (hrOk) {
            "전반적으로 안정 범위로 보입니다. 같은 시간대에 꾸준히 측정해 ‘내 평소값’ 대비 추세를 보는 것이 가장 유용합니다."
        } else {
            "참고용 수치입니다. 한 번의 측정보다 여러 날의 추세가 더 의미 있습니다."
        }
    }

    fun qualityLabel(q: SignalQuality): String = when (q) {
        SignalQuality.GOOD -> "신호 양호"
        SignalQuality.FAIR -> "신호 보통"
        SignalQuality.POOR -> "신호 미흡 — 재측정 권장"
    }
}
