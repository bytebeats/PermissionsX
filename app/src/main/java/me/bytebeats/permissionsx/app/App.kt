package me.bytebeats.permissionsx.app

import android.app.Application
import android.content.Context
import me.bytebeats.permissionsx.PermissionsX

/**
 * @Author bytebeats
 * @Email <happychinapc@gmail.com>
 * @Github https://github.com/bytebeats
 * @Created at 2021/11/6 17:09
 * @Version 1.0
 * @Description TO-DO
 */

class App : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        PermissionsX.interceptor(PermissionInterceptor())
    }
}