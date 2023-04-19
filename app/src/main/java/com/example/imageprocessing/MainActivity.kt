package com.example.imageprocessing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import android.provider.Settings
import com.example.imageprocessing.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.face_detection_fragment.*
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(
            this,
            R.layout.activity_main
        )



//        val fragment  = supportFragmentManager.findFragmentById(R.id.container)
        supportFragmentManager.beginTransaction().add(R.id.container, FaceDetectionFragment()).commit()


        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        } else {

        }

        //Checking for Android 11 and above to use MANAGE_EXTERNAL_STORAGE permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Check if the app has permission to access external storage
//            if (ContextCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.MANAGE_EXTERNAL_STORAGE
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                // If the app doesn't have permission, request it
//                ActivityCompat.requestPermissions(
//                    this,
//                    arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE),
//                    4
//                )
//            }
//            else {
//                startLogging()
//            }


            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, 4)
            } else {
                // Permission granted
                // Access external storage here
                startLogging()
            }

        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted, request it
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    2)
            }

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted, request it
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    3)
            } else {
                startLogging()
            }
        }


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 4) {
            if (Environment.isExternalStorageManager()) {
                // Permission granted
                // Access external storage here
                Log.i("MANAGE GRANTED", "Manage permission granted")
                startLogging()
            } else {
                // Permission denied
                // Handle the error here
                select_image.isEnabled = false
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            Log.i("CAMERA PERMISSION", "inside onrequest permissions result")
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted, perform your file-related task here
                Log.i("CAMERA GRANTED", "Camera permission granted")
            } else {
                // Permission has been denied, handle the situation accordingly
                select_image.isEnabled = false
            }
        }
        if (requestCode == 2) {
            Log.i("READ PERMISSION", "inside onrequest permissions result")
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted, perform your file-related task here
                Log.i("READ GRANTED", "Read permission granted")
            } else {
                // Permission has been denied, handle the situation accordingly
                select_image.isEnabled = false
            }
        }
        if (requestCode == 3) {
            Log.i("WRITE PERMISSION", "inside onrequest permissions result")
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted, perform your file-related task here
                Log.i("Write GRANTED", "Write permission granted")
                startLogging()
            } else {
                // Permission has been denied, handle the situation accordingly
                select_image.isEnabled = false
            }
        }

        if (requestCode == 4) {
            Log.i("MANAGE PERMISSION", "inside onrequest permissions result")
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // Permission granted, do your work here
//                    startLogging()
//            } else {
                // Permission denied, handle accordingly
//                Log.i("MANAGE GRANTED", "Manage permission granted")
//                select_image.isEnabled = false
//            }

//            if (Environment.isExternalStorageManager()) {
//                // Permission granted
//                // Access external storage here
//
//                Log.i("MANAGE GRANTED", "Manage permission granted")
//                startLogging()
//            } else {
//                // Permission denied
//                // Handle the error here
//                select_image.isEnabled = false
//            }
        }
    }

    private fun startLogging() {
        try {
            val fileName = "logcat_" + System.currentTimeMillis() + ".txt"
            val documentsDirectory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val logDirectory = File(documentsDirectory, "log")
            if (!logDirectory.exists()) {
                logDirectory.mkdirs()
            }
            val outputFile = File(logDirectory, fileName)
            Runtime.getRuntime().exec("logcat -f " + outputFile.absolutePath)
        } catch (e: Exception) {
            // Handle exception
        }
    }
}