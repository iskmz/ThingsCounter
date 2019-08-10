package com.iskandar.thingscounter

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import java.time.chrono.ChronoLocalDateTime

data class Thing(val dateTime: Long, val name:String, val count:Int)



class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
