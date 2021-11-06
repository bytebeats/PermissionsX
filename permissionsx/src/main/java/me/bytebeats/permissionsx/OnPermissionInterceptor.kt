package me.bytebeats.permissionsx

import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity

/**
 * @Author bytebeats
 * @Email <happychinapc@gmail.com>
 * @Github https://github.com/bytebeats
 * @Created at 2021/11/3 15:31
 * @Version 1.0
 * @Description 权限请求拦截器
 */

interface OnPermissionInterceptor {
    /**
     * 权限申请拦截，可在此处先弹 Dialog 再申请权限
     */
    fun requestPermissions(
        activity: FragmentActivity,
        permissions: ArrayList<String>,
        callback: OnPermissionRequestCallback? = null
    ) {
        PermissionFragment.beginRequest(activity, permissions, null, callback)
    }

    /**
     * 权限授予回调拦截, 参见 {@link OnPermissionRequestCallback#onGranted(List, boolean)}
     */
    fun grantPermissions(
        activity: ComponentActivity,
        permissions: List<String>,
        callback: OnPermissionRequestCallback? = null,
        allGranted: Boolean = false
    ) {
        callback?.onGranted(permissions, allGranted)
    }

    /**
     * 权限拒绝回调拦截，参见 {@link OnPermissionRequestCallback#onDenied(List, boolean)}
     */
    fun denyPermissions(
        activity: ComponentActivity,
        permissions: List<String>,
        callback: OnPermissionRequestCallback? = null,
        neverAsk: Boolean = false
    ) {
        callback?.onDenied(permissions, neverAsk)
    }
}