package com.sorush.banktracker.util

import android.content.Context

object XiaomiHelper {
    fun isXiaomiDevice(): Boolean = DeviceCompatibilityHelper.isXiaomiDevice()
    fun openAutostartSettings(context: Context) = DeviceCompatibilityHelper.openAutostartSettings(context)
    fun openPopupInBackgroundSettings(context: Context) = DeviceCompatibilityHelper.openPopupInBackgroundSettings(context)
    fun openOverlaySettings(context: Context) = DeviceCompatibilityHelper.openOverlaySettings(context)
    fun openBatterySettings(context: Context) = DeviceCompatibilityHelper.openBatterySettings(context)
    fun openAppDetailsSettings(context: Context) = DeviceCompatibilityHelper.openAppDetailsSettings(context)
}
