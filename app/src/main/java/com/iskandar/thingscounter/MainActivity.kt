package com.iskandar.thingscounter

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.frag_counters.*
import kotlinx.android.synthetic.main.item_counter.view.*
import java.io.File
import java.text.SimpleDateFormat
import java.time.chrono.ChronoLocalDateTime
import java.util.*


/////////////////////////// DATA classes ( & SQLLITE) ////////////////////////////////////////


data class Thing(val dateTime: Long, val name:String, val count:Int)
var thingsCounted = mutableListOf<Thing>()

class CountersDB(context: Context) : SQLiteOpenHelper(context,"counters.db",null,1)
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

    fun addCounterNow(name : String, count : Int) : Boolean {
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
        return addCounterNow(name,count)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
}


/////////////////////////// MAIN ACTIVITY  //////////////////////////////////////


class MainActivity : AppCompatActivity() {

    private lateinit var fm : FragmentManager
    private lateinit var countersFragment: CountersFragment
    private lateinit var addCounterFragment: AddCounterFragment

    companion object {
        val TAG_FRAG_COUNTERS = "COUNTERS"
        val TAG_FRAG_ADDCOUNTER = "ADDCOUNTER"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initFrags()
        setListeners()
    }


    private fun initFrags() {
        countersFragment = CountersFragment()
        addCounterFragment = AddCounterFragment()
        fm = supportFragmentManager
        fm.beginTransaction()
            .add(R.id.layContainer, countersFragment, TAG_FRAG_COUNTERS)
            .add(R.id.layContainer,addCounterFragment, TAG_FRAG_ADDCOUNTER)
            .replace(R.id.layContainer,countersFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun setListeners() {

        btnExit.setOnClickListener { saveThenExit() }
        btnAbout.setOnClickListener { showInfoDialog() }

        btnAddCounter.setOnClickListener {
            it.visibility = View.GONE
            btnBackToList.visibility = View.VISIBLE
            switchTo(addCounterFragment, TAG_FRAG_ADDCOUNTER)
        }

        btnBackToList.setOnClickListener {
            it.visibility = View.GONE
            btnAddCounter.visibility = View.VISIBLE
            switchTo(countersFragment, TAG_FRAG_COUNTERS)
        }
    }


    private fun switchTo(frg: Fragment, tag: String) {
        fm.beginTransaction()
            .replace(R.id.layContainer, frg,tag)
            .commit()
    }

    private fun showInfoDialog() {
        val about = AlertDialog.Builder(this@MainActivity)
            .setIcon(R.drawable.ic_bulb)
            .setTitle("Things-Counter")
            .setMessage("by Iskandar Mazzawi \u00A9")
            .setPositiveButton("OK"){ dialog, _ -> dialog.dismiss() }
            .create()
        about.setCanceledOnTouchOutside(false)
        about.show()
    }

    private fun saveThenExit() {
        // code to AUTO-SAVE data for later
        // need to check if add-counter fragment is ON first ! // maybe a boolean would do the job ! // onhiddenshow ?!!? //
        finish()
    }
}

/////////////////////////// FRAGMENTS ///////////////////////////////////////


class CountersFragment : Fragment(){

    private lateinit var myView : View
    private lateinit var adapter : CountersAdapter
    private lateinit var countersDB: CountersDB

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        myView = inflater.inflate(R.layout.frag_counters ,container,false)
        return myView
    }

    override fun onStart() {
        super.onStart()

        countersDB = CountersDB(activity!!)
        refreshAdapter()
    }

    @SuppressLint("SetTextI18n")
    private fun refreshAdapter() {
        thingsCounted = getDataFromDB()
        if(thingsCounted.size==0)
        {
            lstCounters.visibility = View.GONE
            txtEmptyCountersMsg.visibility = View.VISIBLE
            txtEmptyCountersMsg.text = " click on the (+) button below \n to start counting things !"
        }
        else
        {
            lstCounters.visibility = View.VISIBLE
            txtEmptyCountersMsg.visibility = View.GONE
            adapter = CountersAdapter(activity!!)
            lstCounters.setAdapter(adapter)
        }
    }

    private fun getDataFromDB(): MutableList<Thing> {
        val lst = mutableListOf<Thing>()
        // get data from SQL db using Cursor //
        val res = countersDB.getCountersTable()
        if (res.count == 0) return lst
        while (res.moveToNext()) {
            lst.add(
                Thing(
                    res.getString(CountersDB.COLNUM_DATETIME).toLong(),
                    res.getString(CountersDB.COLNUM_NAME),
                    res.getInt(CountersDB.COLNUM_COUNT)
                )
            )
        }
        return lst
    }
}

class AddCounterFragment : Fragment(){

    private lateinit var myView : View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        myView = inflater.inflate(R.layout.frag_add_counter ,container,false)
        return myView
    }
}


/////////////////////////// ADAPTER ///////////////////////////////////////

class CountersAdapter(private val context:Context) : BaseAdapter() {

    val countersDB = CountersDB(context)

    @SuppressLint("ViewHolder", "SimpleDateFormat")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

        val v = LayoutInflater.from(context).inflate(R.layout.item_counter,parent)
        val current = thingsCounted[position]

        v.txtItemCounterName.text = current.name
        v.txtItemCounterValue.text = current.count.toString()
        v.txtItemCounterTime.text = SimpleDateFormat("yyyy-MM-dd , HH:mm:ss").format(Date(current.dateTime))

        v.btnCounterRemove.setOnClickListener { dialogRemoveQuery(position) }

        return v
    }

    private fun dialogRemoveQuery(pos: Int) {
        val removeme = AlertDialog.Builder(context)
            .setIcon(R.drawable.ic_warning_red)
            .setTitle("REMOVE this counter !?")
            .setMessage("Are you sure you want to remove \nthe counter \"${thingsCounted[pos].name}\" ?!")
            .setPositiveButton("CONFIRM"){dialog, _ ->
                countersDB.removeCounter(thingsCounted[pos].dateTime) // from db
                thingsCounted.removeAt(pos) // from data list
                notifyDataSetChanged() // refresh adapter
                dialog.dismiss() // close dialog
            }
            .setNegativeButton("CANCEL"){dialog,_-> dialog.dismiss()}
            .create()
        removeme.setCanceledOnTouchOutside(false)
        removeme.show()
    }

    override fun getItem(position: Int): Any = thingsCounted[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getCount(): Int = thingsCounted.size
}
