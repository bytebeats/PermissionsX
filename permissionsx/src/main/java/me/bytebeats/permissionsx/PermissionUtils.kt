package me.bytebeats.permissionsx

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.lang.reflect.InvocationTargetException

/**
 * @Author bytebeats
 * @Email <happychinapc@gmail.com>
 * @Github https://github.com/bytebeats
 * @Created at 2021/11/3 16:11
 * @Version 1.0
 * @Description 权限相关工具
 */

/** Android 命名空间  */
private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

private fun isAtLeastAndroid(versionCode: Int): Boolean = Build.VERSION.SDK_INT >= versionCode

/**
 * 是否是 Android 12 及以上版本
 */
internal fun isAtLeastAndroid12(): Boolean = isAtLeastAndroid(Build.VERSION_CODES.R + 1)

/**
 * 是否是 Android 11 及以上版本
 */
internal fun isAtLeastAndroid11(): Boolean = isAtLeastAndroid(Build.VERSION_CODES.R)

/**
 * 是否是 Android 10 及以上版本
 */
internal fun isAtLeastAndroid10(): Boolean = isAtLeastAndroid(Build.VERSION_CODES.Q)

/**
 * 是否是 Android 9 及以上版本
 */
internal fun isAtLeastAndroid9(): Boolean = isAtLeastAndroid(Build.VERSION_CODES.P)

/**
 * 是否是 Android 8 及以上版本
 */
internal fun isAtLeastAndroid8(): Boolean = isAtLeastAndroid(Build.VERSION_CODES.O)

/**
 * 是否是 Android 7 及以上版本
 */
internal fun isAtLeastAndroid7(): Boolean = isAtLeastAndroid(Build.VERSION_CODES.N)

/**
 * 是否是 Android 6 及以上版本
 */
internal fun isAtLeastAndroid6(): Boolean = isAtLeastAndroid(Build.VERSION_CODES.M)

/**
 * 解析清单文件
 */
internal fun parseAndroidManifest(context: Context): XmlResourceParser? {
    val cookie = findInstalledApkPathCookie(context) ?: return null
    return try {
        context.assets.openXmlResourceParser(cookie, "AndroidManifest.xml")
    } catch (ignore: IOException) {
        null
    }
}

/**
 * 获取当前应用 Apk 在 AssetManager 中的 Cookie
 */
internal fun findInstalledApkPathCookie(context: Context): Int? {
    val assetManager = context.assets
    val installedPath = context.applicationInfo.sourceDir
    return try {
        // 为什么不直接通过反射 AssetManager.findCookieForPath 方法来判断？因为这个 API 属于反射黑名单，反射执行不了
        // 为什么不直接通过反射 AssetManager.addAssetPathInternal 这个非隐藏的方法来判断？因为这个也反射不了
        val addOverlayPath = assetManager.javaClass.getDeclaredMethod("addOverlayPath", String::class.java)
        addOverlayPath.isAccessible = true
        /* Android 9.0 以下获取到的结果会为零; Android 9.0 及以上获取到的结果会大于零 */
        addOverlayPath.invoke(assetManager, installedPath) as Int?
    } catch (ignore: Exception) {
        null
    }
}

/**
 * 判断是否适配了分区存储
 */
internal fun isScopedStorage(context: Context): Boolean {
    return try {
        val metaKey = "ScopedStorage"
        val metadata =
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        if (metadata.containsKey(metaKey))
            metadata.get(metaKey).toString().toBoolean()
        else
            false
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

/**
 * 判断 Activity 是否反方向旋转了
 */
internal fun isActivityReverse(activity: ComponentActivity): Boolean {
    // 获取 Activity 旋转的角度
    val rotation = if (isAtLeastAndroid11()) activity.display?.rotation
    else activity.windowManager.defaultDisplay.rotation
    return when (rotation) {
        Surface.ROTATION_180, Surface.ROTATION_270 -> true
        else -> false
    }
}

/**
 * 寻找上下文中的 Activity 对象
 */
internal fun findActivity(context: Context?): ComponentActivity? {
    var ctx: Context? = context
    while (ctx != null) {
        if (ctx is ComponentActivity) {
            return ctx
        } else if (ctx is ContextWrapper) {
            ctx = ctx.baseContext
        } else {
            break
        }
    }
    return null
}

/**
 * 获取已授予的权限
 *
 * @param permissions   需要请求的权限组
 * @param grantResults  允许结果组
 */
internal fun grantedPermissions(permissions: Array<String>, grantResults: IntArray): List<String> {
    return permissions.filterIndexed { index, _ -> grantResults[index] == PackageManager.PERMISSION_GRANTED }.toList()
}

/**
 * 获取没有授予的权限
 *
 * @param permissions   需要请求的权限组
 * @param grantResults  允许结果组
 */
internal fun deniedPermissions(permissions: Array<String>, grantResults: IntArray): List<String> {
    return permissions.filterIndexed { index, _ -> grantResults[index] == PackageManager.PERMISSION_DENIED }.toList()
}

/**
 * 获取没有授予的权限
 *
 * @param context  Context
 * @param permissions   需要请求的权限组
 */
internal fun deniedPermissions(context: Context, permissions: Array<String>): List<String> {
    /*  如果是安卓 6.0 以下版本就默认授予  */
    return if (!isAtLeastAndroid6()) permissions.toList() else permissions.filter { !isPermissionGranted(context, it) }
}

/**
 * 判断某个权限是否是特殊权限
 */
internal fun isSpecialPermission(permission: String): Boolean = permission in Permission.SPECIAL_PERMISSIONS

/**
 * 判断某个权限集合是否包含特殊权限
 */
internal fun hasSpecialPermissions(permissions: List<String>): Boolean =
    permissions.intersect(Permission.SPECIAL_PERMISSIONS).isNotEmpty()

/**
 * 返回应用程序在清单文件中注册的权限
 */
internal fun manifestPermissions(context: Context): Map<String, Int> {
    val permissions = mutableMapOf<String, Int>()
    parseAndroidManifest(context)?.let {
        try {
            do {
                /*  当前节点必须为标签头部  */
                if (it.eventType != XmlResourceParser.START_TAG) {
                    continue
                }
                /*  当前标签必须为 uses-permission  */
                if (it.name != "uses-permission") {
                    continue
                }
                permissions[it.getAttributeValue(ANDROID_NAMESPACE, "name")] =
                    it.getAttributeIntValue(ANDROID_NAMESPACE, "maxSdkVersion", Int.MAX_VALUE)
            } while (it.next() != XmlResourceParser.END_DOCUMENT)
        } catch (ignore: IOException) {
            ignore.printStackTrace()
        } catch (ignore: XmlPullParserException) {
            ignore.printStackTrace()
        } finally {
            it.close()
        }
    }

    if (permissions.isEmpty()) {
        try {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .forEach {
                    permissions[it] = Int.MAX_VALUE
                }
        } catch (ignore: PackageManager.NameNotFoundException) {
            ignore.printStackTrace()
        }
    }
    return permissions
}

/**
 * 是否有存储权限
 */
internal fun isStoragePermissionsGranted(context: Context): Boolean {
    return if (isAtLeastAndroid11()) Environment.isExternalStorageManager()
    else isPermissionsAllGranted(context, Permission.Group.STORAGE.toList())
}

/**
 * 是否有安装权限
 */
internal fun isInstallPermissionGranted(context: Context): Boolean = if (isAtLeastAndroid8())
    context.packageManager.canRequestPackageInstalls() else true

/**
 * 是否有悬浮窗权限
 */
internal fun isWindowPermissionGranted(context: Context): Boolean = if (isAtLeastAndroid6())
    Settings.canDrawOverlays(context) else true

/**
 * 是否有通知栏权限
 */
internal fun isNotificationPermissionGranted(context: Context): Boolean {
    return if (isAtLeastAndroid7()) context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    else if (isAtLeastAndroid6()) {
        try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val checkOpNoThrow = appOpsManager.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.java,
                Int::class.java,
                String::class.java
            )
            val OP_POST_NOTIFICATION = appOpsManager.javaClass.getDeclaredField("OP_POST_NOTIFICATION")
            val value = OP_POST_NOTIFICATION.get(Int::class.java)
            checkOpNoThrow.invoke(
                appOpsManager,
                value,
                context.applicationInfo.uid,
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (ignore: NoSuchMethodException) {
            true
        } catch (ignore: NoSuchFieldException) {
            true
        } catch (ignore: InvocationTargetException) {
            true
        } catch (ignore: IllegalAccessException) {
            true
        } catch (ignore: RuntimeException) {
            true
        }
    } else true
}

/**
 * 是否有系统设置权限
 */
internal fun isSettingPermissionGranted(context: Context): Boolean =
    if (isAtLeastAndroid6()) Settings.System.canWrite(context) else true

/**
 * 判断某些权限是否全部被授予
 */
internal fun isPermissionsAllGranted(context: Context, permissions: List<String>?): Boolean {
    // 如果是安卓 6.0 以下版本就直接返回 true
    if (!isAtLeastAndroid6()) {
        return true
    }
    return permissions?.all { isPermissionGranted(context, it) } ?: false
}

/**
 * 判断某个权限是否授予
 */
internal fun isPermissionGranted(context: Context, permission: String): Boolean {
    if (!isAtLeastAndroid6()) {
        return true
    }
    return when {
        /*  检测存储权限  */
        permission == Permission.MANAGE_EXTERNAL_STORAGE -> isStoragePermissionsGranted(context)
        /*  检测安装权限  */
        permission == Permission.REQUEST_INSTALL_PACKAGES -> isInstallPermissionGranted(context)
        /*  检测悬浮窗权限  */
        permission == Permission.SYSTEM_ALERT_WINDOW -> isWindowPermissionGranted(context)
        /*  检测通知栏权限  */
        permission == Permission.NOTIFICATION_SERVICE -> isNotificationPermissionGranted(context)
        /*  检测系统权限  */
        permission == Permission.WRITE_SETTINGS -> isSettingPermissionGranted(context)
        /*  检测 Android 12 的三个新权限  */
        !isAtLeastAndroid12() && permission == Permission.BLUETOOTH_SCAN -> ContextCompat.checkSelfPermission(
            context,
            Permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        !isAtLeastAndroid12() && (permission == Permission.BLUETOOTH_CONNECT || permission == Permission.BLUETOOTH_ADVERTISE) -> true
        /*  检测 Android 10 的三个新权限  */
        !isAtLeastAndroid10() && permission == Permission.ACCESS_BACKGROUND_LOCATION ->
            ContextCompat.checkSelfPermission(
                context,
                Permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        !isAtLeastAndroid10() && permission == Permission.ACTIVITY_RECOGNITION ->
            ContextCompat.checkSelfPermission(
                context,
                Permission.BODY_SENSORS
            ) == PackageManager.PERMISSION_GRANTED
        !isAtLeastAndroid10() && permission == Permission.ACCESS_MEDIA_LOCATION -> true
        /*  检测 Android 9 的一个新权限  */
        !isAtLeastAndroid9() && permission == Permission.ACCEPT_HANDOVER -> true
        /*  检测 Android 8 的二个新权限  */
        !isAtLeastAndroid8() && permission == Permission.READ_PHONE_NUMBERS ->
            ContextCompat.checkSelfPermission(
                context,
                Permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        !isAtLeastAndroid8() && permission == Permission.ANSWER_PHONE_CALLS -> true
        else -> ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * 优化权限回调结果
 */
internal fun internalPermissionResult(activity: ComponentActivity, permissions: Array<String>, grantResults: IntArray) {
    permissions.forEachIndexed { index, permission ->
        var checkPermissionAgain = false
        /*  如果这个权限是特殊权限，那么就重新进行权限检测  */
        if (isSpecialPermission(permission)) {
            checkPermissionAgain = true
        }
        /*  重新检查 Android 12 的三个新权限  */
        if (!isAtLeastAndroid12() && (permission == Permission.BLUETOOTH_SCAN
                    || permission == Permission.BLUETOOTH_CONNECT
                    || permission == Permission.BLUETOOTH_ADVERTISE)) {
            checkPermissionAgain = true
        }
        /*  重新检查 Android 10.0 的三个新权限  */
        if (!isAtLeastAndroid10() && (permission == Permission.ACCESS_BACKGROUND_LOCATION
                    || permission == Permission.ACTIVITY_RECOGNITION
                    || permission == Permission.ACCESS_MEDIA_LOCATION)) {
            checkPermissionAgain = true
        }

        /*  重新检查 Android 9.0 的一个新权限  */
        if (!isAtLeastAndroid9() && permission == Permission.ACCEPT_HANDOVER) {
            checkPermissionAgain = true
        }
        /*   重新检查 Android 8.0 的两个新权限  */
        if (!isAtLeastAndroid8() && (permission == Permission.ANSWER_PHONE_CALLS
                    || permission == Permission.READ_PHONE_NUMBERS)) {
            checkPermissionAgain = true
        }
        if (checkPermissionAgain) {
            grantResults[index] = if (isPermissionGranted(
                    activity,
                    permission
                )) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
    }
}

/**
 * 判断某个权限是否被永久拒绝
 *
 * @param activity              Activity对象
 * @param permission            请求的权限
 */
internal fun isPermissionPermanentlyDenied(activity: ComponentActivity, permission: String): Boolean {
    return when {
        !isAtLeastAndroid6() -> false
        /*  特殊权限不算，本身申请方式和危险权限申请方式不同，因为没有永久拒绝的选项，所以这里返回 false  */
        isSpecialPermission(permission) -> false
        !isAtLeastAndroid12() && permission == Permission.BLUETOOTH_SCAN ->
            !isPermissionGranted(activity, Permission.ACCESS_COARSE_LOCATION)
                    && !activity.shouldShowRequestPermissionRationale(Permission.ACCESS_COARSE_LOCATION)
        !isAtLeastAndroid12() && (permission == Permission.BLUETOOTH_CONNECT || permission == Permission.BLUETOOTH_ADVERTISE) -> false
        /*  重新检测后台定位权限是否永久拒绝  */
        isAtLeastAndroid10() && permission == Permission.ACCESS_BACKGROUND_LOCATION
                && !isPermissionGranted(activity, Permission.ACCESS_BACKGROUND_LOCATION)
                && !isPermissionGranted(
            activity,
            Permission.ACCESS_FINE_LOCATION
        ) -> !activity.shouldShowRequestPermissionRationale(Permission.ACCESS_FINE_LOCATION)
        /*  检测 Android 10 的三个新权限  */
        !isAtLeastAndroid10() && permission == Permission.ACCESS_BACKGROUND_LOCATION -> !isPermissionGranted(
            activity,
            Permission.ACCESS_FINE_LOCATION
        ) && !activity.shouldShowRequestPermissionRationale(Permission.ACCESS_FINE_LOCATION)
        !isAtLeastAndroid10() && permission == Permission.ACTIVITY_RECOGNITION -> !isPermissionGranted(
            activity,
            Permission.BODY_SENSORS
        ) && !activity.shouldShowRequestPermissionRationale(Permission.BODY_SENSORS)
        !isAtLeastAndroid10() && permission == Permission.ACCESS_MEDIA_LOCATION -> false
        /*  检测 Android 9 的一个新权限  */
        !isAtLeastAndroid9() && permission == Permission.ACCEPT_HANDOVER -> false
        /*  检测 Android 8 的二个新权限  */
        !isAtLeastAndroid8() && permission == Permission.ANSWER_PHONE_CALLS -> false
        !isAtLeastAndroid8() && permission == Permission.READ_PHONE_NUMBERS -> !isPermissionGranted(
            activity,
            Permission.READ_PHONE_STATE
        ) && !activity.shouldShowRequestPermissionRationale(Permission.READ_PHONE_STATE)
        else -> !isPermissionGranted(activity, permission) && !activity.shouldShowRequestPermissionRationale(permission)
    }
}

/**
 * 在权限组中检查是否有某个权限是否被永久拒绝
 *
 * @param activity              Activity对象
 * @param permissions            请求的权限
 */
internal fun hasPermissionPermanentlyDenied(activity: ComponentActivity, permissions: List<String>): Boolean {
    return permissions.any { isPermissionPermanentlyDenied(activity, it) }
}