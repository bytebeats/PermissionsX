package me.bytebeats.permissionsx

/**
 * @Author bytebeats
 * @Email <happychinapc@gmail.com>
 * @Github https://github.com/bytebeats
 * @Created at 2021/11/3 15:25
 * @Version 1.0
 * @Description 权限请求结果回调接口
 */

interface OnPermissionRequestCallback {
    /**
     * 有权限被同意授予时回调
     *
     * @param permissions   请求成功的权限组
     * @param allGranted    是否全部授予了
     */
    fun onGranted(permissions: List<String>, allGranted: Boolean)

    /**
     * 有权限被拒绝授予时回调
     *
     * @param permissions   请求失败的权限组
     * @param neverAsk  是否有某个权限被永久拒绝了
     */
    fun onDenied(permissions: List<String>, neverAsk: Boolean) {}
}