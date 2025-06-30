package com.example.roadguard.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.roadguard.R

class LivePotholeDetectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_pothole_detection)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LivePotholeDetectionFragment())
                .commit()
        }
    }
}
