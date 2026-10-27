package com.nlab.androidboot.utils

import java.util.UUID

/**
 * 호출할 때마다 새로운 식별자를 만든다. 값 자체에 의미는 없고 "이전과 다른 값"임을 보장하는 것이 목적이다.
 *
 * 쓰는 곳: 이전 값이 같아도 StateFlow가 새 값으로 인식해야 하는 입력(재시도 요청 등), 같은 내용이 연속해서 발생해도
 * 구분해야 하는 이벤트(UserMessage 등).
 *
 * UUID의 상위 64비트만 쓰는 이유: Compose key, Bundle 등에서 다루기 쉬운 Long으로 충분하며, 충돌 확률은 무시할 수 있다.
 *
 * Compose 샘플 Jetsnack의 SnackbarManager가 메시지 id를 만드는 방식을 따른다.
 * @see <a href="https://github.com/android/compose-samples/blob/73b3a51e06a6520efb5b4931e71b771d257bf1dd/Jetsnack/app/src/main/java/com/example/jetsnack/model/SnackbarManager.kt#L39">Jetsnack SnackbarManager</a>
 */
fun generateUniqueId(): Long = UUID.randomUUID().mostSignificantBits
