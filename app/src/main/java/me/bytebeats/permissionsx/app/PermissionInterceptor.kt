package me.bytebeats.permissionsx.app

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import me.bytebeats.permissionsx.*

/**
 * @Author bytebeats
 * @Email <happychinapc@gmail.com>
 * @Github https://github.com/bytebeats
 * @Created at 2021/11/6 17:19
 * @Version 1.0
 * @Description 权限申请拦截器
 */

internal const val TAG = "PermissionInterceptor"

class PermissionInterceptor : OnPermissionInterceptor {

    override fun requestPermissions(
        activity: FragmentActivity,
        permissions: ArrayList<String>,
        callback: OnPermissionRequestCallback?
    ) {
        var dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.common_permission_hint)
            .setMessage(R.string.common_permission_message)
            .setPositiveButton(
                R.string.common_permission_granted
            ) { _, _ ->
                PermissionFragment.beginRequest(
                    activity,
                    permissions,
                    this@PermissionInterceptor,
                    callback
                )
            }
            .setNegativeButton(R.string.common_permission_denied, null)
            .show()
        activity.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP) {
                    dialog.dismiss()
                } else if (event == Lifecycle.Event.ON_DESTROY) {
                    dialog = null
                }
            }
        })
    }

    override fun denyPermissions(
        activity: ComponentActivity,
        permissions: List<String>,
        callback: OnPermissionRequestCallback?,
        neverAsk: Boolean
    ) {
        if (neverAsk) {
            showPermissionDialog(activity, permissions)
            if (callback == null) {
                return
            }
            callback.onDenied(permissions, neverAsk)
            return
        }

        if (permissions.size == 1 && Permission.ACCESS_BACKGROUND_LOCATION == permissions[0]) {
            Log.i(TAG, activity.getString(R.string.common_permission_fail_4))
            return
        }

        Log.i(TAG, activity.getString(R.string.common_permission_fail_1))

        if (callback == null) {
            return
        }
        callback.onDenied(permissions, neverAsk)
    }


    /**
     * 显示授权对话框
     */
    protected fun showPermissionDialog(activity: ComponentActivity, permissions: List<String>?) {
        // 这里的 Dialog 只是示例，没有用 DialogFragment 来处理 Dialog 生命周期
        var dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.common_permission_alert)
            .setCancelable(false)
            .setMessage(permissionHint(activity, permissions))
            .setPositiveButton(
                R.string.common_permission_goto
            ) { dialog, _ ->
                dialog.dismiss()
                PermissionsX.startPermissionSettingPage(activity, permissions)
            }
            .show()

        activity.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP) {
                    dialog.dismiss()
                } else if (event == Lifecycle.Event.ON_DESTROY) {
                    dialog = null
                }
            }
        })
    }

    /**
     * 根据权限获取提示
     */
    private fun permissionHint(context: Context, permissions: List<String?>?): String {
        if (permissions == null || permissions.isEmpty()) {
            return context.getString(R.string.common_permission_fail_2)
        }
        val hints: MutableList<String> = ArrayList()
        for (permission in permissions) {
            when (permission) {
                Permission.READ_EXTERNAL_STORAGE, Permission.WRITE_EXTERNAL_STORAGE, Permission.MANAGE_EXTERNAL_STORAGE -> {
                    val hint: String = context.getString(R.string.common_permission_storage)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.CAMERA -> {
                    val hint: String = context.getString(R.string.common_permission_camera)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.RECORD_AUDIO -> {
                    val hint: String = context.getString(R.string.common_permission_microphone)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.ACCESS_FINE_LOCATION, Permission.ACCESS_COARSE_LOCATION, Permission.ACCESS_BACKGROUND_LOCATION -> {
                    val hint: String = if (!permissions.contains(Permission.ACCESS_FINE_LOCATION) &&
                        !permissions.contains(Permission.ACCESS_COARSE_LOCATION)) {
                        context.getString(R.string.common_permission_location_background)
                    } else {
                        context.getString(R.string.common_permission_location)
                    }
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.BLUETOOTH_SCAN, Permission.BLUETOOTH_CONNECT, Permission.BLUETOOTH_ADVERTISE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R + 1) {
                        val hint: String = context.getString(R.string.common_permission_bluetooth)
                        if (!hints.contains(hint)) {
                            hints.add(hint)
                        }
                    }
                }
                Permission.READ_PHONE_STATE, Permission.CALL_PHONE, Permission.ADD_VOICEMAIL, Permission.USE_SIP, Permission.READ_PHONE_NUMBERS, Permission.ANSWER_PHONE_CALLS -> {
                    val hint: String = context.getString(R.string.common_permission_phone)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.GET_ACCOUNTS, Permission.READ_CONTACTS, Permission.WRITE_CONTACTS -> {
                    val hint: String = context.getString(R.string.common_permission_contacts)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.READ_CALENDAR, Permission.WRITE_CALENDAR -> {
                    val hint: String = context.getString(R.string.common_permission_calendar)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.READ_CALL_LOG, Permission.WRITE_CALL_LOG, Permission.PROCESS_OUTGOING_CALLS -> {
                    val hint: String =
                        context.getString(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) R.string.common_permission_call_log else R.string.common_permission_phone)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.BODY_SENSORS -> {
                    val hint: String = context.getString(R.string.common_permission_sensors)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.ACTIVITY_RECOGNITION -> {
                    val hint: String = context.getString(R.string.common_permission_activity_recognition)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.SEND_SMS, Permission.RECEIVE_SMS, Permission.READ_SMS, Permission.RECEIVE_WAP_PUSH, Permission.RECEIVE_MMS -> {
                    val hint: String = context.getString(R.string.common_permission_sms)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.REQUEST_INSTALL_PACKAGES -> {
                    val hint: String = context.getString(R.string.common_permission_install)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.NOTIFICATION_SERVICE -> {
                    val hint: String = context.getString(R.string.common_permission_notification)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.SYSTEM_ALERT_WINDOW -> {
                    val hint: String = context.getString(R.string.common_permission_window)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                Permission.WRITE_SETTINGS -> {
                    val hint: String = context.getString(R.string.common_permission_setting)
                    if (!hints.contains(hint)) {
                        hints.add(hint)
                    }
                }
                else -> {
                }
            }
        }
        if (hints.isNotEmpty()) {
            val builder = StringBuilder()
            for (text in hints) {
                if (builder.isEmpty()) {
                    builder.append(text)
                } else {
                    builder.append("、")
                        .append(text)
                }
            }
            builder.append(" ")
            return context.getString(R.string.common_permission_fail_3, builder.toString())
        }
        return context.getString(R.string.common_permission_fail_2)
    }
}