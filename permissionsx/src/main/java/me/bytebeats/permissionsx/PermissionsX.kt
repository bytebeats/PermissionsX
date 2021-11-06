package me.bytebeats.permissionsx

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * @Author bytebeats
 * @Email <happychinapc@gmail.com>
 * @Github https://github.com/bytebeats
 * @Created at 2021/11/4 18:05
 * @Version 1.0
 * @Description TO-DO
 */

class PermissionsX private constructor(private val context: Context) {

    /** 权限列表 */
    private var mPermissions: MutableList<String>? = null

    /** 权限请求拦截器 */
    private var mInterceptor: OnPermissionInterceptor? = null

    /**
     * 设置权限请求拦截器
     */
    fun interceptor(interceptor: OnPermissionInterceptor?): PermissionsX = this.apply { mInterceptor = interceptor }

    /**
     * 添加权限组
     */
    fun permissions(permissions: List<String>?): PermissionsX = this.apply {
        if (!permissions.isNullOrEmpty()) {
            if (mPermissions == null) {
                mPermissions = mutableListOf()
            }
            permissions.forEach {
                if (!mPermissions!!.contains(it)) {
                    mPermissions?.add(it)
                }
            }
        }
    }

    fun permissions(vararg permissions: String): PermissionsX = permissions(permissions.toList())

    fun permissions(vararg permissionGroups: Array<String>): PermissionsX =
        permissions(permissionGroups.flatMap { it.toList() })

    /**
     * 请求权限
     */
    fun request(callback: OnPermissionRequestCallback?) {
        if (mInterceptor == null) {
            mInterceptor = interceptor()
        }
        val permissions = arrayListOf<String>()
        mPermissions?.let { permissions.addAll(it) }
        // 权限请求列表（为什么直接不用字段？因为框架要兼容新旧权限，在低版本下会自动添加旧权限申请）
        val debugMode = debugMode(context)
        // 检查当前 Activity 状态是否是正常的，如果不是则不请求权限
        val activity = findActivity(context)
        if (!verifyActivity(activity, debugMode)) {
            return
        }
        // 必须要传入正常的权限或者权限组才能申请权限
        if (!verifyPermissionGroup(permissions, debugMode)) {
            return
        }
        if (debugMode) {
            // 检查申请的存储权限是否符合规范
            verifyStoragePermission(context, permissions)
            // 检查申请的定位权限是否符合规范
            verifyLocationPermission(context, permissions)
            // 检查申请的权限和 targetSdk 版本是否能吻合
            verifyTargetSdkVersion(context, permissions)
            // 检测权限有没有在清单文件中注册
            verifyManifestPermissions(context, permissions)
        }
        // 优化所申请的权限列表
        optimizeDeprecatedPermission(permissions)

        if (isPermissionsAllGranted(context, permissions)) {
            // 证明这些权限已经全部授予过，直接回调成功
            callback?.let {
                mInterceptor?.grantPermissions(activity!!, permissions, callback, true)
            }
            return
        }
        mInterceptor?.requestPermissions(activity!!, permissions, callback)
    }

    companion object {
        /** 权限设置页跳转请求码  */
        const val REQUEST_CODE = 1024 + 1

        /** 权限请求拦截器  */
        private var sInterceptor: OnPermissionInterceptor? = null

        /** 当前是否为调试模式  */
        private var sDebugMode: Boolean? = null

        fun with(context: Context): PermissionsX = PermissionsX(context)

        fun with(fragment: Fragment): PermissionsX = with(fragment.requireActivity())

        fun debugMode(debugMode: Boolean) {
            sDebugMode = debugMode
        }

        private fun debugMode(context: Context): Boolean {
            if (sDebugMode == null) {
                sDebugMode = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            }
            return sDebugMode!!
        }

        fun interceptor(interceptor: OnPermissionInterceptor?) {
            sInterceptor = interceptor
        }

        fun interceptor(): OnPermissionInterceptor = sInterceptor ?: object : OnPermissionInterceptor {}

        /**
         * 判断一个或多个权限是否全部授予了
         */
        fun isGranted(context: Context, permissions: List<String>): Boolean =
            isPermissionsAllGranted(context, permissions)

        fun isGranted(context: Context, vararg permissions: String): Boolean = isGranted(context, permissions.toList())

        fun isGranted(context: Context, vararg permissionGroups: Array<String>): Boolean =
            isGranted(context, permissionGroups.flatMap { it.toList() })

        /**
         * 获取没有授予的权限
         */
        fun denied(context: Context, permissions: List<String>): List<String> = deniedPermissions(context, permissions)

        fun denied(context: Context, vararg permissions: String): List<String> = denied(context, permissions.toList())

        fun denied(context: Context, vararg permissionGroups: Array<String>): List<String> =
            denied(context, permissionGroups.flatMap { it.toList() })

        /**
         * 判断某个权限是否为特殊权限
         */
        fun isSpecial(permission: String): Boolean = isSpecialPermission(permission)

        /**
         * 判断一个或多个权限是否被永久拒绝了（注意不能在请求权限之前调用，应该在 {@link OnPermissionCallback#onDenied(List, boolean)} 方法中调用）
         */
        fun isPermanentlyDenied(activity: FragmentActivity, permissions: List<String>): Boolean =
            hasPermissionPermanentlyDenied(activity, permissions)

        fun isPermanentlyDenied(activity: FragmentActivity, vararg permissions: String): Boolean =
            isPermanentlyDenied(activity, permissions.toList())

        fun isPermanentlyDenied(activity: FragmentActivity, vararg permissionGroups: Array<String>): Boolean =
            isPermanentlyDenied(activity, permissionGroups.flatMap { it.toList() })

        /**
         * 跳转到应用权限设置页
         *
         * @param permissions           没有授予或者被拒绝的权限组
         * @param requestCode           Activity 跳转请求码
         */
        @JvmOverloads
        fun startPermissionSettingPage(
            context: Context,
            permissions: List<String>? = null,
            requestCode: Int = REQUEST_CODE
        ) {
            val activity = findActivity(context)
            if (activity != null) {
                startPermissionSettingPage(activity, permissions, requestCode)
            } else {
                val intent = smartMatchPermissionIntent(context, permissions)
                if (context !is ComponentActivity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }

        fun startPermissionSettingPage(context: Context, vararg permissions: String) {
            startPermissionSettingPage(context, permissions.toList())
        }

        fun startPermissionSettingPage(context: Context, vararg permissionGroups: Array<String>) {
            startPermissionSettingPage(context, permissionGroups.flatMap { it.toList() })
        }

        /**
         * 跳转到应用权限设置页
         *
         * @param permissions           没有授予或者被拒绝的权限组
         * @param requestCode           Activity 跳转请求码
         */
        @JvmOverloads
        fun startPermissionSettingPage(
            activity: FragmentActivity,
            permissions: List<String>? = null,
            requestCode: Int = REQUEST_CODE
        ) {
            activity.startActivityForResult(
                smartMatchPermissionIntent(activity, permissions),
                requestCode
            )
        }

        fun startPermissionSettingPage(activity: FragmentActivity, vararg permissions: String) {
            startPermissionSettingPage(activity, permissions.toList())
        }

        fun startPermissionSettingPage(activity: FragmentActivity, vararg permissionGroups: Array<String>) {
            startPermissionSettingPage(activity, permissionGroups.flatMap { it.toList() })
        }

        /**
         * 跳转到应用权限设置页
         *
         * @param permissions           没有授予或者被拒绝的权限组
         * @param requestCode           Activity 跳转请求码
         */
        @JvmOverloads
        fun startPermissionSettingPage(
            fragment: Fragment,
            permissions: List<String>? = null,
            requestCode: Int = REQUEST_CODE
        ) {
            fragment.activity?.let {
                it.startActivityForResult(
                    smartMatchPermissionIntent(it, permissions),
                    requestCode
                )
            }
        }

        fun startPermissionSettingPage(
            fragment: Fragment, vararg permissions: String
        ) {
            startPermissionSettingPage(fragment, permissions.toList())
        }

        fun startPermissionSettingPage(
            fragment: Fragment, vararg permissionGroups: Array<String>
        ) {
            startPermissionSettingPage(fragment, permissionGroups.flatMap { it.toList() })
        }
    }

}