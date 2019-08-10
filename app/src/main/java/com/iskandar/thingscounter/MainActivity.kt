package com.iskandar.thingscounter

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import java.io.File
import java.time.chrono.ChronoLocalDateTime




/////////////////////////// DATA classes ( & SQLLITE) ////////////////////////////////////////


data class Thing(val dateTime: Long, val name:String, val count:Int)
var thingsCounted = mutableListOf<Thing>()

class NotesDB(context: Context) : SQLiteOpenHelper(context,"counters.db",null,1)
{
    private val db = writableDatabase

    companion object {
        val TABLE_NAME ="things"

        val COL_DATETIME = "DATETIME"
        val COL_NAME = "NAME"
        val COL_COUNT = "COUNT"

        val COLNUM_DATETIME = 0
        val COLNUM_NAME = 1
        val COLNUM_COUNT = 2
    }

    init { // for dev. db access
        provideAccessToDev()
    }

    private fun provideAccessToDev() {
        if (BuildConfig.DEBUG) File(db.path).setReadable(true,false)
        //@terminal:
        // follow instructions from:  https://stackoverflow.com/a/21151598
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // create data table //
        val sql = "CREATE TABLE IF NOT EXISTS $TABLE_NAME ($COL_DATETIME TEXT PRIMARY KEY,$COL_NAME TEXT,$COL_COUNT INTEGER)"
        db!!.execSQL(sql)
    }

    fun getCountersTable(): Cursor = db.rawQuery("SELECT * FROM $TABLE_NAME",null)

    fun addCountNow(name : String, count : Int) : Boolean {
        //create instance of ContentValues to hold our values
        val myValues = ContentValues()
        val datetime = System.currentTimeMillis().toString()
        //insert data by key and value
        myValues.put(COL_DATETIME,datetime)
        myValues.put(COL_NAME,name)
        myValues.put(COL_COUNT,count)
        // INSERT new row //
        // put values in table and get res (row id) // if res = -1 then ERROR //
        val res = db.insert(TABLE_NAME, null, myValues)
        //return true if we not get -1, error
        return res != (-1).toLong()
    }

    fun removeCounter(datetime: Long) = db.execSQL("DELETE FROM "+ TABLE_NAME
            +" WHERE "+ COL_DATETIME+"=" + datetime.toString())

    fun updateCounterNow(prevDatetime:Long, name:String, count:Int):Boolean{
        removeCounter(prevDatetime)
        return addCountNow(name,count)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
