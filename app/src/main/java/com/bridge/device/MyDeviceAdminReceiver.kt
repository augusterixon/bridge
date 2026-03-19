package com.bridge.device

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        enforceDeviceOwnerPolicies(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        enforceDeviceOwnerPolicies(context)
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    companion object {
        private const val TAG = "BridgeAdmin"

        fun adminComponent(context: Context): ComponentName =
            ComponentName(context, MyDeviceAdminReceiver::class.java)

        fun enforceDeviceOwnerPolicies(context: Context) {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
            val admin = adminComponent(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            try {
                dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            } catch (e: Exception) {
                Log.e(TAG, "setLockTaskPackages failed", e)
            }

            try {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            } catch (e: Exception) {
                Log.e(TAG, "setLockTaskFeatures failed", e)
            }

            if (!BuildConfig.DEBUG) {
                try {
                    val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addCategory(Intent.CATEGORY_DEFAULT)
                    }
                    dpm.addPersistentPreferredActivity(
                        admin, filter,
                        ComponentName(context, MainActivity::class.java)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "addPersistentPreferredActivity failed", e)
                }
            }

            try {
                dpm.setStatusBarDisabled(admin, true)
            } catch (e: Exception) {
                Log.e(TAG, "setStatusBarDisabled failed", e)
            }

            try {
                dpm.setKeyguardDisabled(admin, true)
            } catch (e: Exception) {
                Log.e(TAG, "setKeyguardDisabled failed", e)
            }
        }
    }
}
