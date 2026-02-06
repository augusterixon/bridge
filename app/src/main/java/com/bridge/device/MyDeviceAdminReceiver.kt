package com.bridge.device

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, MyDeviceAdminReceiver::class.java)

        // Allow Bridge to run in lock task (kiosk) mode
        dpm.setLockTaskPackages(admin, arrayOf(context.packageName))

        // Optional: make Bridge the persistent preferred HOME handler
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val activity = ComponentName(context, MainActivity::class.java)
        dpm.addPersistentPreferredActivity(admin, filter, activity)
    }
}