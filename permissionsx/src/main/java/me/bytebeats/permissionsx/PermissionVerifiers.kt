package me.bytebeats.permissionsx

import android.Manifest
import android.content.Context
import android.content.res.XmlResourceParser
import android.os.Build
import androidx.activity.ComponentActivity
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import kotlin.jvm.Throws

/**
 * @Author bytebeats
 * @Email <happychinapc@gmail.com>
 * @Github https://github.com/bytebeats
 * @Created at 2021/11/4 11:40
 * @Version 1.0
 * @Description 权限错误检测类
 */

/**
 * 检查 Activity 的状态是否正常
 *
 * @param debugMode         是否是调试模式
 * @return                  是否检查通过
 */
@Throws(IllegalArgumentException::class, IllegalStateException::class)
internal fun verifyActivity(activity: ComponentActivity?, debugMode: Boolean): Boolean {
    /*  检查当前 Activity 状态是否是正常的，如果不是则不请求权限  */
    if (activity == null) {
        if (debugMode) {
            // Context 的实例必须是 Activity 对象
            throw  IllegalArgumentException("The instance of the context must be an activity object")
        }
        return false
    }
    if (activity.isFinishing) {
        if (debugMode) {
            // 这个 Activity 对象当前不能是关闭状态，这种情况常出现在执行异步请求后申请权限
            // 请自行在外层判断 Activity 状态是否正常之后再进入权限申请
            throw  IllegalStateException("The activity has been finishing, please manually determine the status of the activity")
        }
        return false
    }
    if (isAtLeastAndroid(Build.VERSION_CODES.JELLY_BEAN_MR1) && activity.isDestroyed) {
        if (debugMode) {
            // 这个 Activity 对象当前不能是销毁状态，这种情况常出现在执行异步请求后申请权限
            // 请自行在外层判断 Activity 状态是否正常之后再进入权限申请
            throw  IllegalStateException("The activity has been destroyed, please manually determine the status of the activity")
        }
        return false
    }
    return true
}

/**
 * 检查传入的权限是否符合要求
 *
 * @param permissions        请求的权限组
 * @param debugMode          是否是调试模式
 */
@Throws(IllegalArgumentException::class)
internal fun verifyPermissionGroup(permissions: List<String>?, debugMode: Boolean): Boolean {
    if (permissions.isNullOrEmpty()) {
        if (debugMode) {
            // 不传权限，就想申请权限？
            throw  IllegalArgumentException("The requested permission cannot be empty")
        }
        return false
    }
    if (debugMode) {
        val allPermissions = mutableListOf<String>()
        val fields = Permission::class.java.declaredFields
        if (fields.isNullOrEmpty()) {
            return true
        }
        for (field in fields) {
            if (field.type != String::class.java) {
                continue
            }
            try {
                allPermissions.add(field.get(null) as String)
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }
        permissions.forEach { permission ->
            if (!allPermissions.contains(permission)) {
                // 请不要申请危险权限和特殊权限之外的权限
                throw  IllegalArgumentException("The $permission is not a dangerous permission or special permission, please do not apply dynamically")
            }
        }
    }
    return true
}

/**
 * 检查存储权限
 *
 * @param permissions        请求的权限组
 */
internal fun verifyStoragePermission(context: Context, permissions: List<String>) {
    // 如果请求的权限中没有包含外部存储相关的权限，那么就直接返回
    if (!permissions.contains(Permission.MANAGE_EXTERNAL_STORAGE) &&
        !permissions.contains(Permission.READ_EXTERNAL_STORAGE) &&
        !permissions.contains(Permission.WRITE_EXTERNAL_STORAGE)) {
        return
    }

    // 是否适配了分区存储
    val scopedStorage: Boolean = isScopedStorage(context)
    val parser: XmlResourceParser = parseAndroidManifest(context) ?: return
    try {
        do {
            // 当前节点必须为标签头部
            if (parser.eventType != XmlResourceParser.START_TAG) {
                continue
            }

            // 当前标签必须为 application
            if ("application" != parser.name) {
                continue
            }
            val targetSdkVersion: Int = context.applicationInfo.targetSdkVersion
            val requestLegacyExternalStorage: Boolean = parser.getAttributeBooleanValue(
                ANDROID_NAMESPACE,
                "requestLegacyExternalStorage", false
            )
            // 如果在已经适配 Android 10 的情况下
            if (targetSdkVersion >= Build.VERSION_CODES.Q && !requestLegacyExternalStorage &&
                (permissions.contains(Permission.MANAGE_EXTERNAL_STORAGE) || !scopedStorage)) {
                // 请在清单文件 Application 节点中注册 android:requestLegacyExternalStorage="true" 属性
                // 否则就算申请了权限，也无法在 Android 10 的设备上正常读写外部存储上的文件
                // 如果你的项目已经全面适配了分区存储，请在清单文件中注册一个 meta-data 属性
                // <meta-data android:name="ScopedStorage" android:value="true" /> 来跳过该检查
                throw IllegalStateException(
                    "Please register the android:requestLegacyExternalStorage=\"true\" " +
                            "attribute in the AndroidManifest.xml file"
                )
            }

            // 如果在已经适配 Android 11 的情况下
            if (targetSdkVersion >= Build.VERSION_CODES.R &&
                !permissions.contains(Permission.MANAGE_EXTERNAL_STORAGE) && !scopedStorage) {
                // 1. 适配分区存储的特性，并在清单文件中注册一个 meta-data 属性
                // <meta-data android:name="ScopedStorage" android:value="true" />
                // 2. 如果不想适配分区存储，则需要使用 Permission.MANAGE_EXTERNAL_STORAGE 来申请权限
                // 上面两种方式需要二选一，否则无法在 Android 11 的设备上正常读写外部存储上的文件
                // 如果不知道该怎么选择，可以看文档：https://github.com/getActivity/XXPermissions/blob/master/HelpDoc
                throw IllegalArgumentException(
                    "Please adapt the scoped storage, or use the MANAGE_EXTERNAL_STORAGE permission"
                )
            }

            // 终止循环
            break
        } while (parser.next() != XmlResourceParser.END_DOCUMENT)
    } catch (e: IOException) {
        e.printStackTrace()
    } catch (e: XmlPullParserException) {
        e.printStackTrace()
    } finally {
        parser.close()
    }
}

/**
 * 检查定位权限
 *
 * @param permissions        请求的权限组
 */
@Throws(IllegalArgumentException::class)
internal fun verifyLocationPermission(context: Context, permissions: List<String>) {
    if (context.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.R + 1) {
        if (permissions.contains(Permission.ACCESS_FINE_LOCATION) &&
            !permissions.contains(Permission.ACCESS_COARSE_LOCATION)) {
            // 如果您的应用以 Android 12 为目标平台并且您请求 ACCESS_FINE_LOCATION 权限，则还必须请求 ACCESS_COARSE_LOCATION 权限
            // 官方适配文档：https://developer.android.google.cn/about/versions/12/approximate-location
            throw IllegalArgumentException(
                "If your app targets Android 12 or higher " +
                        "and requests the ACCESS_FINE_LOCATION runtime permission, " +
                        "you must also request the ACCESS_COARSE_LOCATION permission. " +
                        "You must include both permissions in a single runtime request."
            )
        }
    }

    // 判断是否包含后台定位权限
    if (!permissions.contains(Permission.ACCESS_BACKGROUND_LOCATION)) {
        return
    }
    if (permissions.contains(Permission.ACCESS_COARSE_LOCATION) &&
        !permissions.contains(Permission.ACCESS_FINE_LOCATION)) {
        // 申请后台定位权限可以不包含模糊定位权限，但是一定要包含精确定位权限，否则后台定位权限会无法申请，也就是会导致无法弹出授权弹窗
        // 经过实践，在 Android 12 上这个问题已经被解决了，但是为了兼容 Android 12 以下的设备，还是要那么做，否则在 Android 11 及以下设备会出现异常
        throw IllegalArgumentException(
            "The application for background location permissions must include precise location permissions"
        )
    }
    for (permission: String in permissions) {
        if (Permission.ACCESS_FINE_LOCATION == permission || Permission.ACCESS_COARSE_LOCATION == permission || Permission.ACCESS_BACKGROUND_LOCATION == permission) {
            continue
        }
        throw IllegalArgumentException("Because it includes background location permissions, do not apply for permissions unrelated to location")
    }
}

/**
 * 检查targetSdkVersion 是否符合要求
 *
 * @param permissions            请求的权限组
 */
@Throws(RuntimeException::class)
internal fun verifyTargetSdkVersion(context: Context, permissions: List<String>) {
    // targetSdk 最低版本要求
    val targetSdkMinVersion = if (permissions.contains(Permission.BLUETOOTH_SCAN) ||
        permissions.contains(Permission.BLUETOOTH_CONNECT) ||
        permissions.contains(Permission.BLUETOOTH_ADVERTISE)) {
        Build.VERSION_CODES.R + 1
    } else if (permissions.contains(Permission.MANAGE_EXTERNAL_STORAGE)) {
        // 必须设置 targetSdkVersion >= 30 才能正常检测权限，否则请使用 Permission.Group.STORAGE 来申请存储权限
        Build.VERSION_CODES.R
    } else if (permissions.contains(Permission.ACCEPT_HANDOVER)) {
        Build.VERSION_CODES.P
    } else if (permissions.contains(Permission.ACCESS_BACKGROUND_LOCATION) ||
        permissions.contains(Permission.ACTIVITY_RECOGNITION) ||
        permissions.contains(Permission.ACCESS_MEDIA_LOCATION)) {
        Build.VERSION_CODES.Q
    } else if (permissions.contains(Permission.REQUEST_INSTALL_PACKAGES) ||
        permissions.contains(Permission.ANSWER_PHONE_CALLS) ||
        permissions.contains(Permission.READ_PHONE_NUMBERS)) {
        Build.VERSION_CODES.O
    } else {
        Build.VERSION_CODES.M
    }

    // 必须设置正确的 targetSdkVersion 才能正常检测权限
    if (context.applicationInfo.targetSdkVersion < targetSdkMinVersion) {
        throw RuntimeException("The targetSdkVersion SDK must be $targetSdkMinVersion or more")
    }
}

/**
 * 检查清单文件中所注册的权限是否正常
 *
 * @param permissions            请求的权限组
 */
internal fun verifyManifestPermissions(context: Context, permissions: List<String>) {
    val manifestPermissions = manifestPermissions(context)
    if (manifestPermissions.isEmpty()) {
        throw IllegalStateException("No permissions are registered in the AndroidManifest.xml file")
    }
    val minSdkVersion =
        if (isAtLeastAndroid(Build.VERSION_CODES.N)) context.applicationInfo.minSdkVersion else Build.VERSION_CODES.M
    for (permission in permissions) {
        if ((Permission.NOTIFICATION_SERVICE == permission)) {
            // 不检测通知栏权限有没有在清单文件中注册，因为这个权限是框架虚拟出来的，有没有在清单文件中注册都没关系
            continue
        }
        var sdkVersion: Int
        if (((Build.VERSION_CODES.R + 1).also { sdkVersion = it }) >= minSdkVersion) {
            if ((Permission.BLUETOOTH_SCAN == permission)) {
                verifyManifestPermission(manifestPermissions, Manifest.permission.BLUETOOTH_ADMIN, sdkVersion)
                // 这是 Android 12 之前遗留的问题，获取扫描蓝牙的结果需要定位的权限
                verifyManifestPermission(manifestPermissions, Manifest.permission.ACCESS_COARSE_LOCATION, sdkVersion)
            }
            if ((Permission.BLUETOOTH_CONNECT == permission)) {
                verifyManifestPermission(manifestPermissions, Manifest.permission.BLUETOOTH, sdkVersion)
            }
            if ((Permission.BLUETOOTH_ADVERTISE == permission)) {
                verifyManifestPermission(manifestPermissions, Manifest.permission.BLUETOOTH_ADMIN, sdkVersion)
            }
        }
        if ((Build.VERSION_CODES.R.also { sdkVersion = it }) >= minSdkVersion) {
            if ((Permission.MANAGE_EXTERNAL_STORAGE == permission)) {
                verifyManifestPermission(manifestPermissions, Permission.READ_EXTERNAL_STORAGE, sdkVersion)
                verifyManifestPermission(manifestPermissions, Permission.WRITE_EXTERNAL_STORAGE, sdkVersion)
            }
        }
        if ((Build.VERSION_CODES.Q.also { sdkVersion = it }) >= minSdkVersion) {
            if ((Permission.ACTIVITY_RECOGNITION == permission)) {
                verifyManifestPermission(manifestPermissions, Permission.BODY_SENSORS, sdkVersion)
            }
        }
        if ((Build.VERSION_CODES.O.also { sdkVersion = it }) >= minSdkVersion) {
            if ((Permission.READ_PHONE_NUMBERS == permission)) {
                verifyManifestPermission(manifestPermissions, Permission.READ_PHONE_NUMBERS, sdkVersion)
            }
        }
        verifyManifestPermission(manifestPermissions, permission, Int.MAX_VALUE)
    }
}

/**
 * 检查某个权限注册是否正常，如果是则会抛出异常
 *
 * @param manifestPermissions       清单权限组
 * @param permission           被检查的权限
 * @param maxSdkVersion             最低要求的 maxSdkVersion
 */
@Throws(IllegalStateException::class, IllegalArgumentException::class)
internal fun verifyManifestPermission(manifestPermissions: Map<String, Int>, permission: String, maxSdkVersion: Int) {
    if (!manifestPermissions.containsKey(permission)) {
        // 动态申请的权限没有在清单文件中注册
        throw IllegalStateException("Please register permissions in the AndroidManifest.xml file <uses-permission android:name=\"$permission\" />")
    }
    val manifestMaxSdkVersion = manifestPermissions[permission] ?: return
    if (manifestMaxSdkVersion < maxSdkVersion) {
        // 清单文件中所注册的权限 android:maxSdkVersion 大小不符合最低要求
        throw IllegalArgumentException(
            "The AndroidManifest.xml file <uses-permission android:name=\"$permission\" android:maxSdkVersion=\"$manifestMaxSdkVersion\" /> does not meet the requirements, " +
                    (if (maxSdkVersion != Int.MAX_VALUE) "the minimum requirement for maxSdkVersion is $maxSdkVersion" else "please delete the android:maxSdkVersion=\"$manifestMaxSdkVersion\" attribute")
        )
    }
}

/**
 * 处理和优化已经过时的权限
 *
 * @param permissions            请求的权限组
 */
@Throws(IllegalArgumentException::class)
internal fun optimizeDeprecatedPermission(permissions: MutableList<String>) {
    if ((!isAtLeastAndroid12() &&
                permissions.contains(Permission.BLUETOOTH_SCAN) &&
                !permissions.contains(Permission.ACCESS_COARSE_LOCATION))) {
        // 自动添加定位权限，因为在低版本下获取蓝牙扫描的结果需要此权限
        permissions.add(Permission.ACCESS_COARSE_LOCATION)
    }

    // 如果本次申请包含了 Android 12 蓝牙扫描权限
    if (!isAtLeastAndroid12() && permissions.contains(Permission.BLUETOOTH_SCAN)) {
        // 这是 Android 12 之前遗留的问题，扫描蓝牙需要定位的权限
        permissions.add(Permission.ACCESS_COARSE_LOCATION)
    }

    // 如果本次申请包含了 Android 11 存储权限
    if (permissions.contains(Permission.MANAGE_EXTERNAL_STORAGE)) {
        if (permissions.contains(Permission.READ_EXTERNAL_STORAGE) ||
            permissions.contains(Permission.WRITE_EXTERNAL_STORAGE)) {
            // 检测是否有旧版的存储权限，有的话直接抛出异常，请不要自己动态申请这两个权限
            throw IllegalArgumentException(
                "If you have applied for MANAGE_EXTERNAL_STORAGE permissions, " +
                        "do not apply for the READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE permissions"
            )
        }
        if (!isAtLeastAndroid11()) {
            // 自动添加旧版的存储权限，因为旧版的系统不支持申请新版的存储权限
            permissions.add(Permission.READ_EXTERNAL_STORAGE)
            permissions.add(Permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    if (!isAtLeastAndroid8() &&
        permissions.contains(Permission.READ_PHONE_NUMBERS) &&
        !permissions.contains(Permission.READ_PHONE_STATE)) {
        // 自动添加旧版的读取电话号码权限，因为旧版的系统不支持申请新版的权限
        permissions.add(Permission.READ_PHONE_STATE)
    }
    if (!isAtLeastAndroid10() &&
        permissions.contains(Permission.ACTIVITY_RECOGNITION) &&
        !permissions.contains(Permission.BODY_SENSORS)) {
        // 自动添加传感器权限，因为这个权限是从 Android 10 开始才从传感器权限中剥离成独立权限
        permissions.add(Permission.BODY_SENSORS)
    }
}

