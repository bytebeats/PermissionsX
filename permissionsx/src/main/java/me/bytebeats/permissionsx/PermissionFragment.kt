package me.bytebeats.permissionsx

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlin.collections.ArrayList
import kotlin.math.pow
import kotlin.random.Random

/**
 * @Author bytebeats
 * @Email <happychinapc@gmail.com>
 * @Github https://github.com/bytebeats
 * @Created at 2021/11/3 17:06
 * @Version 1.0
 * @Description 权限请求 Fragment
 */
// TODO: 2021/11/4 replace `requestPermissions` with Activity Results APIs
class PermissionFragment : Fragment(), Runnable {
    /** 是否申请了特殊权限 */
    private var isSpecialRequest = false

    /** 是否申请了危险权限 */
    private var isDangerousRequest = false

    /** 权限申请标记 */
    private var isRequestFlag = false

    /** 权限回调对象 */
    private var mCallback: OnPermissionRequestCallback? = null

    /** 权限请求拦截器 */
    private var mInterceptor: OnPermissionInterceptor? = null

    /** Activity 屏幕方向 */
    private var mScreenOrientation: Int = 0

    /**
     * 绑定 Activity
     */
    fun attachTo(activity: FragmentActivity) {
        activity.supportFragmentManager.beginTransaction().add(this, this.toString()).commitAllowingStateLoss()
    }

    /**
     * 解绑 Activity
     */
    fun detachFrom(activity: FragmentActivity) {
        activity.supportFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    /**
     * 设置权限监听回调监听
     */
    fun setCallback(callback: OnPermissionRequestCallback?): PermissionFragment = this.apply { mCallback = callback }

    /**
     * 权限申请标记（防止系统杀死应用后重新触发请求的问题）
     */
    fun setRequestFlag(flag: Boolean): PermissionFragment = this.apply { isRequestFlag = flag }

    /**
     * 设置权限请求拦截器
     */
    fun setInterceptor(interceptor: OnPermissionInterceptor?): PermissionFragment =
        this.apply { mInterceptor = interceptor }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (activity == null) return
        // 如果当前没有锁定屏幕方向就获取当前屏幕方向并进行锁定
        mScreenOrientation = requireActivity().requestedOrientation
        if (mScreenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            return
        }
        try {
            // 兼容问题：在 Android 8.0 的手机上可以固定 Activity 的方向，但是这个 Activity 不能是透明的，否则就会抛出异常
            // 复现场景：只需要给 Activity 主题设置 <item name="android:windowIsTranslucent">true</item> 属性即可
            when (requireActivity().resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> requireActivity().requestedOrientation =
                    if (isActivityReverse(requireActivity())) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> requireActivity().requestedOrientation =
                    if (isActivityReverse(requireActivity())) ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } catch (ignore: IllegalStateException) {
            // java.lang.IllegalStateException: Only fullscreen activities can request orientation
            ignore.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()

        // 如果当前 Fragment 是通过系统重启应用触发的，则不进行权限申请
        if (!isRequestFlag) {
            detachFrom(requireActivity())
            return
        }
        // 如果在 Activity 不可见的状态下添加 Fragment 并且去申请权限会导致授权对话框显示不出来
        // 所以必须要在 Fragment 的 onResume 来申请权限，这样就可以保证应用回到前台的时候才去申请权限
        if (isSpecialRequest) {
            return
        }
        isSpecialRequest = true
        requestSpecialPermission()
    }

    /**
     * 申请特殊权限
     */
    private fun requestSpecialPermission() {
        arguments ?: return
        activity ?: return
        // 是否需要申请特殊权限
        var requestSpecialPermission = false
        // 判断当前是否包含特殊权限
        requireArguments().getStringArrayList(REQUEST_PERMISSIONS)?.let {
            for (permission in it) {
                if (isPermissionGranted(requireContext(), permission)) {// 已经授予过了，可以跳过
                    continue
                }
                if (permission == Permission.MANAGE_EXTERNAL_STORAGE && !isAtLeastAndroid11()) {//当前必须是 Android 11 及以上版本，因为在旧版本上是拿旧权限做的判断
                    continue
                }
                // 跳转到特殊权限授权页面
                startActivityForResult(
                    smartMatchPermissionIntent(requireActivity(), listOf(permission)), requireArguments().getInt(
                        REQUEST_CODE
                    )
                )
                requestSpecialPermission = true
            }
        }
        if (requestSpecialPermission) {
            return
        }
        // 如果没有跳转到特殊权限授权页面，就直接申请危险权限
        requestDangerousPermission()
    }

    /**
     * 申请危险权限
     */
    private fun requestDangerousPermission() {
        arguments ?: return
        activity ?: return
        val requestCode = requireArguments().getInt(REQUEST_CODE)

        val permissions = requireArguments().getStringArrayList(REQUEST_PERMISSIONS) ?: return

        val locationPermissions = ArrayList<String>()
        // Android 10 定位策略发生改变，申请后台定位权限的前提是要有前台定位权限（授予了精确或者模糊任一权限）
        if (isAtLeastAndroid10() && permissions.contains(Permission.ACCESS_BACKGROUND_LOCATION)) {
            locationPermissions.addAll(permissions.filter { it == Permission.ACCESS_COARSE_LOCATION || it == Permission.ACCESS_FINE_LOCATION })
        }
        if (!isAtLeastAndroid10() || locationPermissions.isEmpty()) {
            requestPermissions(permissions.subList(0, permissions.size - 1).toTypedArray(), requestCode)
            return
        }

        // 在 Android 10 的机型上，需要先申请前台定位权限，再申请后台定位权限
        beginRequest(requireActivity(), locationPermissions, null, object : OnPermissionRequestCallback {
            override fun onGranted(ps1: List<String>, allGranted: Boolean) {
                if (!allGranted || !isAdded) {
                    return
                }
                // 前台定位权限授予了，现在申请后台定位权限
                beginRequest(
                    requireActivity(),
                    arrayListOf(Permission.ACCESS_BACKGROUND_LOCATION),
                    null,
                    object : OnPermissionRequestCallback {
                        override fun onGranted(ps2: List<String>, allGranted: Boolean) {
                            if (!allGranted || !isAdded) {
                                return
                            }
                            val grantResults = IntArray(permissions.size) { PackageManager.PERMISSION_DENIED }
                            onRequestPermissionsResult(
                                requestCode,
                                permissions.subList(0, permissions.size - 1).toTypedArray(),
                                grantResults
                            )
                        }

                        override fun onDenied(ps3: List<String>, neverAsk: Boolean) {
                            if (!isAdded) {
                                return
                            }
                            // 后台定位授权失败，但是前台定位权限已经授予了
                            val grantResults = IntArray(permissions.size)
                            for (i in permissions.indices) {
                                grantResults[i] =
                                    if (permissions[i] == Permission.ACCESS_BACKGROUND_LOCATION) PackageManager.PERMISSION_DENIED else PackageManager.PERMISSION_GRANTED
                            }
                            onRequestPermissionsResult(
                                requestCode,
                                permissions.subList(0, permissions.size - 1).toTypedArray(),
                                grantResults
                            )
                        }
                    })
            }

            override fun onDenied(ps4: List<String>, neverAsk: Boolean) {
                if (!isAdded) {
                    return
                }
                // 前台定位授权失败，并且无法申请后台定位权限
                val grantResults = IntArray(permissions.size) { PackageManager.PERMISSION_DENIED }
                onRequestPermissionsResult(
                    requestCode,
                    permissions.subList(0, permissions.size - 1).toTypedArray(),
                    grantResults
                )
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        arguments ?: return
        activity ?: return
        mCallback ?: return
        if (requestCode != requireArguments().getInt(REQUEST_CODE)) {
            return
        }
        val callback = mCallback
        mCallback = null
        val interceptor = mInterceptor
        mInterceptor = null

        // 优化权限回调结果
        optimizePermissionResults(requireActivity(), permissions, grantResults)
        // 释放对这个请求码的占用
        REQUEST_CODE_ARRAY.remove(requestCode)
// 将 Fragment 从 Activity 移除
        detachFrom(requireActivity())
        // 获取已授予的权限
        val grantedPermissions = grantedPermissions(permissions, grantResults)
        // 如果请求成功的权限集合大小和请求的数组一样大时证明权限已经全部授予
        if (grantedPermissions.size == permissions.size) {
            if (interceptor != null) {
                // 代表申请的所有的权限都授予了
                interceptor.grantPermissions(requireActivity(), grantedPermissions, callback, true)
            } else {
                callback?.onGranted(grantedPermissions, true)
            }
            return
        }

        val deniedPermissions = deniedPermissions(permissions, grantResults)
        if (interceptor != null) {
            // 代表申请的权限中有不同意授予的，如果有某个权限被永久拒绝就返回 true 给开发人员，让开发者引导用户去设置界面开启权限
            interceptor.denyPermissions(
                requireActivity(),
                deniedPermissions,
                callback,
                hasPermissionPermanentlyDenied(requireActivity(), deniedPermissions)
            )
        } else {
            callback?.onDenied(deniedPermissions, hasPermissionPermanentlyDenied(requireActivity(), deniedPermissions))
        }
        // 证明还有一部分权限被成功授予，回调成功接口
        if (grantedPermissions.isNotEmpty()) {
            if (interceptor != null) {
                interceptor.grantPermissions(requireActivity(), grantedPermissions, callback, false)
            } else {
                callback?.onGranted(grantedPermissions, false)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        arguments ?: return
        activity ?: return
        if (isDangerousRequest || requestCode != requireArguments().getInt(REQUEST_CODE)) {
            return
        }
        isDangerousRequest = true
        // 需要延迟执行，不然有些华为机型授权了但是获取不到权限
        requireActivity().window.decorView.postDelayed(this, 300)
    }

    override fun run() {
        // 如果用户离开太久，会导致 Activity 被回收掉
        // 所以这里要判断当前 Fragment 是否有被添加到 Activity
        // 可在开发者模式中开启不保留活动来复现这个 Bug
        if (!isAdded) {
            return
        } else {
            // 请求其他危险权限
            requestDangerousPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消引用监听器，避免内存泄漏
        mCallback = null
    }

    override fun onDetach() {
        super.onDetach()
        if (activity == null || mScreenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            return
        }
        // 为什么这里不用跟上面一样 try catch ？因为这里是把 Activity 方向取消固定，只有设置横屏或竖屏的时候才可能触发 cras
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    companion object {
        /** 请求的权限组  */
        private const val REQUEST_PERMISSIONS = "request_permissions"

        /** 请求码（自动生成） */
        private const val REQUEST_CODE = "request_code"

        /** 权限请求码存放集合  */
        private val REQUEST_CODE_ARRAY = mutableSetOf<Int>()

        /**
         * 开启权限申请
         */
        @JvmOverloads
        fun beginRequest(
            activity: FragmentActivity,
            permissions: ArrayList<String>,
            interceptor: OnPermissionInterceptor? = null,
            callback: OnPermissionRequestCallback? = null
        ) {
            val permissionFragment = PermissionFragment()
            val bundle = Bundle()
            var requestCode = 0
            // 请求码随机生成，避免随机产生之前的请求码，必须进行循环判断
            do {
                // 新版本的 Support 库限制请求码必须小于 65536
                // 旧版本的 Support 库限制请求码必须小于 256
                requestCode = Random.Default.nextInt(20.0.pow(8).toInt())
            } while (!REQUEST_CODE_ARRAY.contains(requestCode))
            // 标记这个请求码已经被占用
            REQUEST_CODE_ARRAY.add(requestCode)
            bundle.putInt(REQUEST_CODE, requestCode)
            bundle.putStringArrayList(REQUEST_PERMISSIONS, permissions)
            permissionFragment.arguments = bundle
            // 设置保留实例，不会因为屏幕方向或配置变化而重新创建
            permissionFragment.retainInstance = true
            permissionFragment.setRequestFlag(true)
                .setCallback(callback)
                .setInterceptor(interceptor)
                .attachTo(activity)
        }
    }
}