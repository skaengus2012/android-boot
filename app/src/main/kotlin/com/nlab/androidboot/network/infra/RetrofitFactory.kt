package com.nlab.androidboot.network.infra

import dagger.Lazy
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject

/**
 * @author Thalys
 */
class RetrofitFactory @Inject constructor(
    private val okHttpClientProvider: Lazy<OkHttpClient>,
) {
    private val json: Json = Json {
        ignoreUnknownKeys = true    // DTO 에서 추가 안한 값 무시
        coerceInputValues = true    // 파싱 후, 잘못되었으면 기본 값 사용
        encodeDefaults = true       // JSON 변환 시, 기본값 추가
    }

    fun create(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .callFactory { okHttpClientProvider.get().newCall(it) }
        .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()
}