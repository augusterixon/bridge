package com.bridge.device

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        enforceDeviceOwnerPolicies(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        enforceDeviceOwnerPolicies(context)
        // Do NOT start MainActivity here. The Setup Wizard is still in the foreground
        // during provisioning. Launching an activity interferes with the setup flow and
        // causes "Couldn't set up your device. Contact your IT admin."
        // After provisioning finishes, Android launches the HOME app automatically
        // because addPersistentPreferredActivity binds HOME → Bridge.
    }

    companion object {
        private const val TAG = "BridgeAdmin"

        fun adminComponent(context: Context): ComponentName =
            ComponentName(context, MyDeviceAdminReceiver::class.java)

        private val LAUNCHED_PACKAGES = arrayOf(
            // System apps Bridge launches via intents
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "org.fossify.messages",
            "com.google.android.apps.maps",
            "com.android.settings",
            // Auth apps
            "com.bankid.bus",
            "com.azure.authenticator",
            "com.google.android.apps.authenticator2",
            "com.okta.android.auth",
            "com.duosecurity.duomobile",
            "com.authy.authy",
            // Utility apps
            "com.whatsapp",
            "com.spotify.music",
            "com.ubercab",
            // Travel apps
            "com.grabtaxi.passenger",
            "ee.mtakso.client",
        )

        fun enforceDeviceOwnerPolicies(context: Context) {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
            val admin = adminComponent(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            // Whitelist Bridge + every package it may launch for LockTask.
            // Non-whitelisted packages in separate tasks are blocked with SecurityException.
            try {
                val packages = mutableListOf(context.packageName)
                for (pkg in LAUNCHED_PACKAGES) {
                    if (isPackageInstalled(context, pkg)) packages.add(pkg)
                }
                dpm.setLockTaskPackages(admin, packages.toTypedArray())
            } catch (e: Exception) {
                Log.e(TAG, "setLockTaskPackages failed", e)
            }

            // LOCK_TASK_FEATURE_HOME: shows Home button so users can return to Bridge
            //   from any launched app (Maps, Settings, etc.)
            // LOCK_TASK_FEATURE_GLOBAL_ACTIONS: enables power menu (restart/power off)
            try {
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                        or DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                )
            } catch (e: Exception) {
                Log.e(TAG, "setLockTaskFeatures failed", e)
            }

            if (!BuildConfig.DEBUG) {
                try {
                    dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
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

            // Camera must be enabled for QR scanning
            try {
                dpm.setCameraDisabled(admin, false)
            } catch (e: Exception) {
                Log.e(TAG, "setCameraDisabled failed", e)
            }

            // Auto-grant CAMERA so the runtime dialog (impossible in kiosk) is bypassed
            try {
                dpm.setPermissionGrantState(
                    admin, context.packageName,
                    android.Manifest.permission.CAMERA,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
            } catch (e: Exception) {
                Log.e(TAG, "Auto-grant CAMERA failed", e)
            }
        }

        private fun isPackageInstalled(context: Context, pkg: String): Boolean {
            return try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
