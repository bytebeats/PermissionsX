package me.bytebeats.permissionsx

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/**
 * @Author bytebeats
 * @Email <happychinapc@gmail.com>
 * @Github https://github.com/bytebeats
 * @Created at 2021/11/3 21:21
 * @Version 1.0
 * @Description Intent for multiple permissions to specific pages
 */
/**
 * 获取包名 Uri 对象
 */
private fun uriForPackageName(context: Context): Uri = Uri.parse("package:${context.packageName}")

/**
 * 判断这个意图的 Activity 是否存在
 */
@SuppressLint("QueryPermissionsNeeded")
private fun isActivityIntent(context: Context, intent: Intent): Boolean =
    context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()

/**
 * 获取存储权限设置界面意图
 */
internal fun intentOfStoragePermission(context: Context): Intent {
    var intent: Intent? = null
    if (isAtLeastAndroid11()) {
        intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = uriForPackageName(context)
    }
    if (intent == null || !isActivityIntent(context, intent)) {
        intent = intentOfApplicationDetails(context)
    }
    return intent!!
}

/**
 * 获取应用详情界面意图
 */
internal fun intentOfApplicationDetails(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = uriForPackageName(context)
    }

/**
 * 获取系统设置权限界面意图
 */
internal fun intentOfSettingPermission(context: Context): Intent {
    var intent: Intent? = null
    if (isAtLeastAndroid6()) {
        intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = uriForPackageName(context)
    }
    if (intent == null || !isActivityIntent(context, intent)) {
        intent = intentOfApplicationDetails(context)
    }
    return intent!!
}

/**
 * 获取通知栏权限设置界面意图
 */
internal fun intentOfNotificationPermission(context: Context): Intent {
    var intent: Intent? = null
    if (isAtLeastAndroid8()) {
        intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
//        intent.putExtra(Settings.EXTRA_CHANNEL_ID, context.applicationInfo.uid)
    }
    if (intent == null || !isActivityIntent(context, intent)) {
        intent = intentOfApplicationDetails(context)
    }
    return intent!!
}

/**
 * 获取悬浮窗权限设置界面意图
 */
internal fun intentOfWindowPermission(context: Context): Intent {
    var intent: Intent? = null
    if (isAtLeastAndroid6()) {
        intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        // 在 Android 11 加包名跳转也是没有效果的，官方文档链接：
        // https://developer.android.google.cn/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION
        intent.data = uriForPackageName(context)
    }
    if (intent == null || !isActivityIntent(context, intent)) {
        intent = intentOfApplicationDetails(context)
    }
    return intent!!
}

/**
 * 获取安装权限设置界面意图
 */
internal fun intentOfInstallPermission(context: Context): Intent {
    var intent: Intent? = null
    if (isAtLeastAndroid8()) {
        intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        intent.data = uriForPackageName(context)
    }
    if (intent == null || !isActivityIntent(context, intent)) {
        intent = intentOfApplicationDetails(context)
    }
    return intent!!
}

/**
 * 根据传入的权限自动选择最合适的权限设置页
 */

internal fun smartMatchPermissionIntent(context: Context, permissions: List<String>?): Intent {
    // 如果失败的权限里面不包含特殊权限
    if (permissions.isNullOrEmpty() || !hasSpecialPermissions(permissions)) {
        return intentOfApplicationDetails(context)
    }
    if (isAtLeastAndroid11() && permissions.size == 3 && (Permission.MANAGE_EXTERNAL_STORAGE in permissions || Permission.READ_EXTERNAL_STORAGE in permissions || Permission.WRITE_EXTERNAL_STORAGE in permissions)) {
        return intentOfStoragePermission(context)
    }
    if (permissions.size == 1) {
        val permission = permissions[0]
        if (permission == Permission.MANAGE_EXTERNAL_STORAGE) {
            return intentOfStoragePermission(context)
        }
        if (permission == Permission.REQUEST_INSTALL_PACKAGES) {
            return intentOfInstallPermission(context)
        }
        if (permission == Permission.SYSTEM_ALERT_WINDOW) {
            return intentOfWindowPermission(context)
        }
        if (permission == Permission.NOTIFICATION_SERVICE) {
            return intentOfNotificationPermission(context)
        }
        if (permission == Permission.WRITE_SETTINGS) {
            return intentOfSettingPermission(context)
        }
    }
    return intentOfApplicationDetails(context)
}