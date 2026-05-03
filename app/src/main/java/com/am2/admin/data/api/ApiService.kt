package com.am2.admin.data.api

import com.am2.admin.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @FormUrlEncoded
    @POST("api_login.php")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    @GET("api_dashboard_stats.php")
    suspend fun getDashboardStats(
        @Query("admin_id") adminId: Int,
        @Query("role") role: String
    ): Response<DashboardStats>

    @GET("api_dashboard_chart.php")
    suspend fun getChartData(
        @Query("admin_id") adminId: Int,
        @Query("role") role: String
    ): Response<ChartDataResponse>

    // --- Users ---
    @GET("api_users.php")
    suspend fun getUsers(
        @Query("admin_id") adminId: Int,
        @Query("role") role: String,
        @Query("search") search: String? = null
    ): Response<List<User>>

    @FormUrlEncoded
    @POST("api_users.php")
    suspend fun addUser(
        @Field("action") action: String = "add",
        @Field("admin_id") adminId: Int,
        @Field("id") id: String,
        @Field("name") name: String,
        @Field("password") password: String
    ): Response<GenericResponse>

    @FormUrlEncoded
    @POST("api_users.php")
    suspend fun updateFeature(
        @Field("action") action: String = "update_feature",
        @Field("u_id") userId: String,
        @Field("feature") feature: String,
        @Field("val") value: Any
    ): Response<GenericResponse>

    @FormUrlEncoded
    @POST("api_users.php")
    suspend fun deleteUser(
        @Field("action") action: String = "delete",
        @Field("id") id: String
    ): Response<GenericResponse>

    @GET("api_users.php")
    suspend fun getUserChannels(
        @Query("action") action: String = "get_user_channels",
        @Query("u_id") userId: String
    ): Response<List<Int>>

    @FormUrlEncoded
    @POST("api_users.php")
    suspend fun saveUserChannels(
        @Field("action") action: String = "save_user_channels",
        @Field("u_id") userId: String,
        @Field("channels") channelsJson: String
    ): Response<GenericResponse>

    // --- Channels ---
    @GET("api_channels.php")
    suspend fun getChannels(
        @Query("admin_id") adminId: Int,
        @Query("role") role: String
    ): Response<List<Channel>>

    @FormUrlEncoded
    @POST("api_channels.php")
    suspend fun addChannel(
        @Field("action") action: String = "add",
        @Field("admin_id") adminId: Int,
        @Field("display_name") displayName: String,
        @Field("category") category: String
    ): Response<GenericResponse>

    @FormUrlEncoded
    @POST("api_channels.php")
    suspend fun updateChannel(
        @Field("action") action: String = "edit",
        @Field("admin_id") adminId: Int,
        @Field("role") role: String,
        @Field("id") id: Int,
        @Field("display_name") displayName: String
    ): Response<GenericResponse>

    @FormUrlEncoded
    @POST("api_channels.php")
    suspend fun deleteChannel(
        @Field("action") action: String = "delete",
        @Field("admin_id") adminId: Int,
        @Field("role") role: String,
        @Field("id") id: Int
    ): Response<GenericResponse>

    // --- Channel Access Management ---
    @GET("api_channels.php")
    suspend fun getChannelUsersAccess(
        @Query("action") action: String = "get_users_access",
        @Query("channel_id") channelId: Int
    ): Response<List<String>> // Returns list of user_ids

    @FormUrlEncoded
    @POST("api_channels.php")
    suspend fun saveChannelAccess(
        @Field("action") action: String = "save_access",
        @Field("admin_id") adminId: Int,
        @Field("role") role: String,
        @Field("channel_id") channelId: Int,
        @Field("users") userIdsJson: String // Stringified JSON array of user IDs
    ): Response<GenericResponse>

    // --- Access Control ---
    @GET("api_user_access.php")
    suspend fun getUserAccessList(
        @Query("admin_id") adminId: Int,
        @Query("role") role: String,
        @Query("search") search: String? = null
    ): Response<List<UserAccess>>

    @FormUrlEncoded
    @POST("api_user_access.php")
    suspend fun forceLogout(
        @Field("action") action: String = "force_logout",
        @Field("user_id") userId: String,
        @Field("admin_id") adminId: Int
    ): Response<GenericResponse>

    @FormUrlEncoded
    @POST("api_user_access.php")
    suspend fun updateUserAccess(
        @Field("action") action: String = "update_access",
        @Field("admin_id") adminId: Int,
        @Field("user_id") userId: String,
        @Field("channels[]") channelIds: List<Int>?,
        @Field("default_channel") defaultChannelId: Int?,
        @Field("permissions") permissionsJson: String
    ): Response<GenericResponse>

    // --- Tracking ---
    @GET("api_get_users.php")
    suspend fun getTrackUnits(
        @Query("admin_id") adminId: Int,
        @Query("role") role: String
    ): Response<List<TrackUnit>>

    // --- Logs ---
    @GET("api_logs.php")
    suspend fun getLogs(
        @Query("admin_id") adminId: Int,
        @Query("role") role: String,
        @Query("category") category: String
    ): Response<List<LogEntry>>

    // --- Admin Panel ---
    @GET("api_admin_panel.php")
    suspend fun getAdminList(): Response<List<Admin>>

    @FormUrlEncoded
    @POST("api_admin_panel.php")
    suspend fun saveAdmin(
        @Field("action") action: String = "save",
        @Field("admin_id") adminId: Int?,
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("role") role: String,
        @Field("user_quota") userQuota: Int,
        @Field("channel_quota") channelQuota: Int,
        @Field("expired_at") expiredAt: String?,
        @Field("can_manage_maps") canManageMaps: Boolean,
        @Field("can_manage_p2p") canManageP2P: Boolean,
        @Field("can_manage_video") canManageVideo: Boolean
    ): Response<GenericResponse>

    @FormUrlEncoded
    @POST("api_admin_panel.php")
    suspend fun deleteAdmin(
        @Field("action") action: String = "delete",
        @Field("id") id: Int
    ): Response<GenericResponse>

    @FormUrlEncoded
    @POST("api_admin_panel.php")
    suspend fun delegateChannels(
        @Field("action") action: String = "delegate",
        @Field("target_admin_id") adminId: Int,
        @Field("channels[]") channelIds: List<Int>
    ): Response<GenericResponse>

    // --- Settings ---
    @GET("api_settings.php")
    suspend fun getAdminProfile(
        @Query("admin_id") adminId: Int,
        @Query("role") role: String
    ): Response<AdminProfile>

    @GET("api_settings.php")
    suspend fun checkAppUpdate(
        @Query("action") action: String = "check_update"
    ): Response<UpdateInfo>

    @FormUrlEncoded
    @POST("api_settings.php")
    suspend fun updateAdminPassword(
        @Field("action") action: String = "update_password",
        @Field("admin_id") adminId: Int,
        @Field("new_password") newPass: String
    ): Response<GenericResponse>

    @Multipart
    @POST("api_settings.php")
    suspend fun importDatabase(
        @Part("action") action: okhttp3.RequestBody,
        @Part("admin_id") adminId: okhttp3.RequestBody,
        @Part sql_file: MultipartBody.Part
    ): Response<GenericResponse>
}
