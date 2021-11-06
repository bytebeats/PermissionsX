package me.bytebeats.permissionsx.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import me.bytebeats.permissionsx.OnPermissionRequestCallback
import me.bytebeats.permissionsx.Permission
import me.bytebeats.permissionsx.PermissionsX

class MainActivity : AppCompatActivity(), View.OnClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_main_request_single).setOnClickListener(this);
        findViewById<Button>(R.id.btn_main_request_group).setOnClickListener(this);
        findViewById<Button>(R.id.btn_main_request_location).setOnClickListener(this);
        findViewById<Button>(R.id.btn_main_request_bluetooth).setOnClickListener(this);
        findViewById<Button>(R.id.btn_main_request_storage).setOnClickListener(this);
        findViewById<Button>(R.id.btn_main_request_package).setOnClickListener(this);
        findViewById<Button>(R.id.btn_main_request_window).setOnClickListener(this);
        findViewById<Button>(R.id.btn_main_request_notification).setOnClickListener(this);
        findViewById<Button>(R.id.btn_main_request_setting).setOnClickListener(this);
        findViewById<Button>(R.id.btn_main_app_details).setOnClickListener(this);
    }

    override fun onClick(view: View) {
        val viewId = view.id
        if (viewId == R.id.btn_main_request_single) {
            PermissionsX.with(this)
                .permissions(Permission.CAMERA)
                .request(object : OnPermissionRequestCallback {
                    override fun onGranted(permissions: List<String>, allGranted: Boolean) {
                        if (allGranted) {
                            toast("获取拍照权限成功")
                        }
                    }
                })
        } else if (viewId == R.id.btn_main_request_group) {
            PermissionsX.with(this)
                .permissions(Permission.RECORD_AUDIO)
                .permissions(Permission.Group.CALENDAR)
                .request(object : OnPermissionRequestCallback {
                    override fun onGranted(permissions: List<String>, allGranted: Boolean) {
                        if (allGranted) {
                            toast("获取录音和日历权限成功")
                        }
                    }
                })
        } else if (viewId == R.id.btn_main_request_location) {
            PermissionsX.with(this)
                .permissions(Permission.ACCESS_COARSE_LOCATION)
                .permissions(Permission.ACCESS_FINE_LOCATION) // 如果不需要在后台使用定位功能，请不要申请此权限
                .permissions(Permission.ACCESS_BACKGROUND_LOCATION)
                .request(object : OnPermissionRequestCallback {
                    override fun onGranted(permissions: List<String>, allGranted: Boolean) {
                        if (allGranted) {
                            toast("获取定位权限成功")
                        }
                    }
                })
        } else if (viewId == R.id.btn_main_request_bluetooth) {
            var delayMillis: Long = 0
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R + 1) {
                delayMillis = 2000
                toast("当前版本不是 Android 12 及以上，旧版本的需要定位权限才能进行扫描蓝牙")
            }
            view.postDelayed({
                PermissionsX.with(this@MainActivity)
                    .permissions(Permission.BLUETOOTH_SCAN)
                    .permissions(Permission.BLUETOOTH_CONNECT)
                    .permissions(Permission.BLUETOOTH_ADVERTISE)
                    .request(object : OnPermissionRequestCallback {
                        override fun onGranted(permissions: List<String>, allGranted: Boolean) {
                            if (allGranted) {
                                toast("获取蓝牙权限成功")
                            }
                        }
                    })
            }, delayMillis)
        } else if (viewId == R.id.btn_main_request_storage) {
            var delayMillis: Long = 0
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                delayMillis = 2000
                toast("当前版本不是 Android 11 及以上，会自动变更为旧版的请求方式")
            }
            view.postDelayed({
                PermissionsX.with(this@MainActivity) // 适配 Android 11 分区存储这样写
                    //.permissions(Permission.Group.STORAGE)
                    // 不适配 Android 11 分区存储这样写
                    .permissions(Permission.MANAGE_EXTERNAL_STORAGE)
                    .request(object : OnPermissionRequestCallback {
                        override fun onGranted(permissions: List<String>, allGranted: Boolean) {
                            if (allGranted) {
                                toast("获取存储权限成功")
                            }
                        }
                    })
            }, delayMillis)
        } else if (viewId == R.id.btn_main_request_package) {
            PermissionsX.with(this)
                .permissions(Permission.REQUEST_INSTALL_PACKAGES)
                .request(object : OnPermissionRequestCallback {
                    override fun onGranted(permissions: List<String>, allGranted: Boolean) {
                        toast("获取安装包权限成功")
                    }
                })
        } else if (viewId == R.id.btn_main_request_window) {
            PermissionsX.with(this)
                .permissions(Permission.SYSTEM_ALERT_WINDOW)
                .request(object : OnPermissionRequestCallback {
                    override fun onGranted(permissions: List<String>, allGranted: Boolean) {
                        toast("获取悬浮窗权限成功")
                    }
                })
        } else if (viewId == R.id.btn_main_request_notification) {
            PermissionsX.with(this)
                .permissions(Permission.NOTIFICATION_SERVICE)
                .request(object : OnPermissionRequestCallback {
                    override fun onGranted(permissions: List<String>, allGranted: Boolean) {
                        toast("获取通知栏权限成功")
                    }
                })
        } else if (viewId == R.id.btn_main_request_setting) {
            PermissionsX.with(this)
                .permissions(Permission.WRITE_SETTINGS)
                .request(object : OnPermissionRequestCallback {
                    override fun onGranted(permissions: List<String>, allGranted: Boolean) {
                        toast("获取系统设置权限成功")
                    }
                })
        } else if (viewId == R.id.btn_main_app_details) {
            PermissionsX.startPermissionSettingPage(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PermissionsX.REQUEST_CODE) {
            toast("检测到你刚刚从权限设置界面返回回来")
        }
    }

    fun toast(text: CharSequence?) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}