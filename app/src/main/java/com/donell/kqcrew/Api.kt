package com.donell.kqcrew

import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.net.CookieManager
import java.net.CookiePolicy

// 10.0.2.2 is how the Android emulator reaches the API running on your computer.
const val BASE_URL = "http://10.0.2.2:8000/"

// Paste the WEB client ID from Google Cloud here (it is not a secret).
const val WEB_CLIENT_ID = "739392813441-gisf0vs4bhkhl15gmrufq561fkg93dov.apps.googleusercontent.com"

data class LoginBody(val email: String, val password: String)
data class LoginResult(val ok: Boolean, val role: String?)
data class GoogleBody(val credential: String)
data class GoogleResult(val ok: Boolean, val role: String?, val created: Boolean)
data class Me(val user_id: Int, val full_name: String, val role: String, val email: String)

data class Transit(
    val transit_id: Int,
    val corridor: String?,
    val pickup_location: String?,
    val dropoff_location: String?,
    val scheduled_pickup_time: String?,
    val scheduled_arrival_time: String?,
    val vehicle_type: String?,
    val flight_number: String?,
    val destination: String?,
    val risk_level: String?,
    val open_alert: Boolean
)

data class TransitList(val total: Int, val items: List<Transit>)

interface KqApi {
    @POST("login")
    suspend fun login(@Body body: LoginBody): Response<LoginResult>

    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleBody): Response<GoogleResult>

    @GET("api/me")
    suspend fun me(): Response<Me>

    @GET("api/my-transits")
    suspend fun myTransits(): Response<TransitList>

    @POST("logout")
    suspend fun logout(): Response<Unit>
}

object KqClient {
    // The API signs you in with a cookie, so the app keeps the cookie between calls.
    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    private val http = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .build()

    val api: KqApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(KqApi::class.java)
}