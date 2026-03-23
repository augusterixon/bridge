package com.bridge.device

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        writeLog(context, "onEnabled fired")
        enforceDeviceOwnerPolicies(context)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onProvisioningFailed(context: Context, intent: Intent) {
        super.onProvisioningFailed(context, intent)
        val error = intent.getStringExtra("android.app.extra.PROVISIONING_ERROR_CODE")
        writeLog(context, "PROVISIONING FAILED: $error — ${intent.extras?.keySet()?.map { "$it=${intent.extras?.get(it)}" }}")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        writeLog(context, "onProfileProvisioningComplete fired")

        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: run {
            writeLog(context, "ERROR: dpm is null")
            return
        }
        val admin = adminComponent(context)
        writeLog(context, "isDeviceOwnerApp: ${dpm.isDeviceOwnerApp(context.packageName)}")

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
            writeLog(context, "HOME activity set successfully")
        } catch (e: Exception) {
            writeLog(context, "ERROR setting HOME activity: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BridgeAdmin"

        fun adminComponent(context: Context): ComponentName =
            ComponentName(context, MyDeviceAdminReceiver::class.java)

        private val LAUNCHED_PACKAGES = arrayOf(
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "org.fossify.messages",
            "com.google.android.apps.maps",
            "com.android.settings",
            "com.bankid.bus",
            "com.azure.authenticator",
            "com.google.android.apps.authenticator2",
            "com.okta.android.auth",
            "com.duosecurity.duomobile",
            "com.authy.authy",
            "com.whatsapp",
            "com.spotify.music",
            "com.ubercab",
            "com.grabtaxi.passenger",
            "ee.mtakso.client",
        )

        fun enforceDeviceOwnerPolicies(context: Context) {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: run {
                writeLog(context, "ERROR: dpm is null in enforceDeviceOwnerPolicies")
                return
            }
            val admin = adminComponent(context)

            if (!dpm.isDeviceOwnerApp(context.packageName)) {
                writeLog(context, "ERROR: not Device Owner, aborting policy enforcement")
                return
            }

            writeLog(context, "enforceDeviceOwnerPolicies started")

            try {
                val packages = mutableListOf(context.packageName)
                for (pkg in LAUNCHED_PACKAGES) {
                    if (isPackageInstalled(context, pkg)) packages.add(pkg)
                }
                dpm.setLockTaskPackages(admin, packages.toTypedArray())
                writeLog(context, "setLockTaskPackages OK: $packages")
            } catch (e: Exception) {
                writeLog(context, "ERROR setLockTaskPackages: ${e.message}")
            }

            try {
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                        or DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                )
                writeLog(context, "setLockTaskFeatures OK")
            } catch (e: Exception) {
                writeLog(context, "ERROR setLockTaskFeatures: ${e.message}")
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
                    writeLog(context, "addPersistentPreferredActivity OK")
                } catch (e: Exception) {
                    writeLog(context, "ERROR addPersistentPreferredActivity: ${e.message}")
                }
            }

            try {
                dpm.setStatusBarDisabled(admin, true)
                writeLog(context, "setStatusBarDisabled OK")
            } catch (e: Exception) {
                writeLog(context, "ERROR setStatusBarDisabled: ${e.message}")
            }

            try {
                dpm.setKeyguardDisabled(admin, true)
                writeLog(context, "setKeyguardDisabled OK")
            } catch (e: Exception) {
                writeLog(context, "ERROR setKeyguardDisabled: ${e.message}")
            }

            try {
                dpm.setCameraDisabled(admin, false)
                writeLog(context, "setCameraDisabled OK")
            } catch (e: Exception) {
                writeLog(context, "ERROR setCameraDisabled: ${e.message}")
            }

            try {
                dpm.setPermissionGrantState(
                    admin, context.packageName,
                    android.Manifest.permission.CAMERA,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
                writeLog(context, "CAMERA permission grant OK")
            } catch (e: Exception) {
                writeLog(context, "ERROR CAMERA permission grant: ${e.message}")
            }

            writeLog(context, "enforceDeviceOwnerPolicies complete")
        }

        fun writeLog(context: Context, message: String) {
            try {
                val file = java.io.File(context.getExternalFilesDir(null), "bridge_log.txt")
                file.appendText("${System.currentTimeMillis()}: $message\n")
                Log.d(TAG, message)
            } catch (e: Exception) {
                Log.e(TAG, "writeLog failed: ${e.message}")
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