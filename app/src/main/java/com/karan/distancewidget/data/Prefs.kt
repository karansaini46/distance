package com.karan.distancewidget.data

import android.content.Context

object Prefs {
    private const val PREF_NAME      = "app_prefs"
    const val USER_KARAN             = "karan"
    const val USER_PARTNER           = "partner"
    private const val KEY_USER_ID    = "user_id"
    private const val KEY_MY_INITIAL = "my_initial"
    private const val KEY_PT_INITIAL = "partner_initial"
    
    private const val KEY_WIDGET_OPACITY = "widget_opacity"
    private const val KEY_WIDGET_ANIM    = "widget_anim"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isSetup(ctx: Context): Boolean = getUserId(ctx) != null

    fun saveUser(ctx: Context, userId: String, myInitial: String, partnerInitial: String) {
        prefs(ctx).edit()
            .putString(KEY_USER_ID,    userId)
            .putString(KEY_MY_INITIAL, myInitial.uppercase().take(1))
            .putString(KEY_PT_INITIAL, partnerInitial.uppercase().take(1))
            .apply()
    }

    fun getUserId(ctx: Context): String? =
        prefs(ctx).getString(KEY_USER_ID, null)

    fun getMyInitial(ctx: Context): String =
        prefs(ctx).getString(KEY_MY_INITIAL, "K") ?: "K"

    fun getPartnerInitial(ctx: Context): String =
        prefs(ctx).getString(KEY_PT_INITIAL, "P") ?: "P"

    fun getPartnerId(ctx: Context): String =
        if (getUserId(ctx) == USER_KARAN) USER_PARTNER else USER_KARAN

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    // ── Widget Customization ────────────────────────────────────────────

    fun getWidgetOpacity(ctx: Context): Int =
        prefs(ctx).getInt(KEY_WIDGET_OPACITY, 100) // 100% opacity by default

    fun setWidgetOpacity(ctx: Context, opacity: Int) {
        prefs(ctx).edit().putInt(KEY_WIDGET_OPACITY, opacity.coerceIn(0, 100)).apply()
    }

    fun isWidgetAnimationEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_WIDGET_ANIM, true)

    fun setWidgetAnimationEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_WIDGET_ANIM, enabled).apply()
    }
}
