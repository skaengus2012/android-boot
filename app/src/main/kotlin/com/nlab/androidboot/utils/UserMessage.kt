package com.nlab.androidboot.utils

import java.util.UUID

/**
 * 단발성 UI 이벤트(스낵바, 다이얼로그 등)를 State로 관리하기 위한 모델.
 * Google Architecture 권장 사항('ViewModel: One-off event antipatterns')을 준수합니다.
 *
 * @property message 표시할 텍스트 메시지
 * @property id 메시지 고유 식별자. Recomposition 방지 및 동일 메시지 연속 발생 시 식별용.
 */
data class UserMessage(
    val message: String,
    val id: Long = UUID.randomUUID().mostSignificantBits
)
