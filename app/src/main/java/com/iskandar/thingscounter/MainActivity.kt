package com.iskandar.thingscounter

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_add_new_counter.view.*
import kotlinx.android.synthetic.main.frag_add_counter.*
import kotlinx.android.synthetic.main.frag_counters.*
import kotlinx.android.synthetic.main.item_counter.view.*
import java.io.File
import java.text.SimpleDateFormat
import java.time.chrono.ChronoLocalDateTime
import java.util.*
import javax.xml.parsers.FactoryConfigurationError


/////////////////////////// DATA classes ( & SQLLITE) ////////////////////////////////////////

var CURRENT_POS = -1 // pos of current "thing" the list // -1 is initial condition //
var AUTOSAVE_NEEDED = false // to know if to autosave or not !!

data class Thing(var dateTime: Long, var name:String, var count:Int)
var thingsCounted = mutableListOf<Thing>()

open class CountersDB(context: Context) : SQLiteOpenHelper(context,"counters.db",null,1)
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

    fun addCounterNow(name : String, count : Int) : Boolean =
        addCounterData(System.currentTimeMillis().toString(),name,count)

    fun addCounterData(datetime:String,name:String,count:Int):Boolean {
        //create instance of ContentValues to hold our values
        val myValues = ContentValues()
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

        btnAddCounter.setOnClickListener { dialogAddNewCounter() }

        btnBackToList.setOnClickListener {
            autosaveCounter()
            it.visibility = View.GONE
            btnAddCounter.visibility = View.VISIBLE
            switchTo(countersFragment, TAG_FRAG_COUNTERS)
        }
    }

    private fun autosaveCounter() {
        if(CURRENT_POS != -1 && AUTOSAVE_NEEDED) // i.e. if "updated" or "added" a counter //
        {
            val current = thingsCounted[CURRENT_POS] // ref. to current counter
            CountersDB(this@MainActivity).updateCounterNow(current.dateTime,current.name,current.count)
            // only db needs to be updated, cause list is re-loaded automatically ! onFragment START //
            AUTOSAVE_NEEDED = false // return to default state after saving //
        }
    }

    private fun dialogAddNewCounter() {

        fun checkNameOK(str: String):Boolean = str.isNotBlank() // only check at the moment ! //

        val v = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_add_new_counter,null)
        val dialogCounterName = AlertDialog.Builder(this@MainActivity)
            .setView(v)
            .setTitle("NEW Counter !")
            .setIcon(R.drawable.ic_text_fields_cyan)
            .create()
        dialogCounterName.setCanceledOnTouchOutside(false)
        v.btnCancelCounterName.setOnClickListener { dialogCounterName.dismiss() }
        v.btnConfirmCounterName.setOnClickListener {
            if(checkNameOK(v.txtInputCounterName.text.toString()))
            {
                // create new 'thing' to count //
                val thing = Thing(System.currentTimeMillis(), v.txtInputCounterName.text.toString(),0)
                // add it to the end of the list
                thingsCounted.add(thing)
                // add it to db
                CountersDB(this@MainActivity).addCounterData(thing.dateTime.toString(),thing.name,thing.count)

                CURRENT_POS = thingsCounted.size-1  // to load from this pos

                // now we can safely go to AddCounterFragment
                btnAddCounter.visibility = View.GONE
                btnBackToList.visibility = View.VISIBLE
                switchTo(addCounterFragment, TAG_FRAG_ADDCOUNTER)

                AUTOSAVE_NEEDED = true // to autosave when done or finished app. //

                // close dialog when done
                dialogCounterName.dismiss()
            }
            else
            {
                Toast.makeText(this@MainActivity,"INVALID / EMPTY Counter Name !",Toast.LENGTH_LONG).show()
            }
        }
        dialogCounterName.show()
    }


    fun switchTo(frg: Fragment, tag: String) {
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
        autosaveCounter()
        finish()
    }


    override fun onBackPressed() {
        // do nothing // override to do nothing
    }

    override fun onDestroy() {
        autosaveCounter()
        super.onDestroy()
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
    fun refreshAdapter() {
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

    companion object {
        private val F_LIST = listOf(1,2,3,5,10,50,100,1000) // factor list //
        const val DELAY_LONG_CLICK:Long = 100 // in msec

        var FACTOR = 1 // default factor for counting , ( +/- 1 )
    }

    private lateinit var myView : View


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        myView = inflater.inflate(R.layout.frag_add_counter ,container,false)
        return myView
    }

    override fun onStart() {
        super.onStart()

        setFields()
        setListeners()
    }

    private fun setFields() {
            FACTOR = 1 // reset on fragment start
            txtCounterName_Add.text =  thingsCounted[CURRENT_POS].name
            txtCounterValue_Add.text = thingsCounted[CURRENT_POS].count.toString()
    }

    private fun setListeners() {
        btnCountUp.setOnClickListener {  increment(); updateCounterView() }
        btnCountUp.setOnLongClickListener{ doIt(1,btnCountUp); false}

        btnCountDown.setOnClickListener {  decrement(); updateCounterView() }
        btnCountDown.setOnLongClickListener {  doIt(-1,btnCountDown); false}

        btnAdjustCountFactor.setOnClickListener { showFactorChoice() }

        txtCounterValue_Add.setOnLongClickListener {
            thingsCounted[CURRENT_POS].count = 0
            updateCounterView()
            true
        }
    }

    private fun showFactorChoice() {
        val choice = AlertDialog.Builder(activity!!)
            .setTitle("  X counting factor X")
            .setIcon(R.drawable.ic_factor_dialog)
            .setItems(F_LIST.map{it.toString()+"x"}.toTypedArray()){
                    dialog, which -> setFactor(F_LIST[which]); dialog.dismiss() }.create()
        choice.setCanceledOnTouchOutside(false)
        choice.show()
    }

    private fun setFactor(f: Int)
    {
        FACTOR = f
        Toast.makeText(activity!!,"Counting Factor ${FACTOR}x was selected!",Toast.LENGTH_LONG).show()
    }

    private fun doIt(switcher: Int, v:View) {
        Thread(Runnable{
            while(true)
            {
                SystemClock.sleep(DELAY_LONG_CLICK)
                activity!!.runOnUiThread {
                    if(switcher==1) { increment() } else { decrement() }
                    updateCounterView()
                }
                if (!v.isPressed) break // breaks when button is released ! //
            }
        }).start()
    }

    private fun updateCounterView() {
        txtCounterValue_Add.text = thingsCounted[CURRENT_POS].count.toString()
    }


    private fun increment() { thingsCounted[CURRENT_POS].count += FACTOR }
    private fun decrement() {
        val tmp = thingsCounted[CURRENT_POS].count - FACTOR
        thingsCounted[CURRENT_POS].count = if (tmp<=0) 0 else tmp
    }
}


/////////////////////////// ADAPTER ///////////////////////////////////////

class CountersAdapter(private val context:Context) : BaseAdapter() {

    val countersDB = CountersDB(context)

    @SuppressLint("ViewHolder", "SimpleDateFormat")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

        val v = LayoutInflater.from(context).inflate(R.layout.item_counter,null)
        val current = thingsCounted[position]

        v.txtItemCounterName.text = current.name
        v.txtItemCounterValue.text = current.count.toString()
        v.txtItemCounterTime.text = SimpleDateFormat("yyyy-MM-dd , HH:mm:ss").format(Date(current.dateTime))

        v.btnCounterRemove.setOnClickListener { dialogRemoveQuery(position) }
        v.layItemCounter.setOnClickListener { updateCounter(position) }

        return v
    }

    private fun updateCounter(pos: Int) {
        CURRENT_POS = pos  // to load from this pos
        AUTOSAVE_NEEDED = true // to autosave when done or finished app. //
        // now we can safely go to AddCounterFragment
        val btnAddC = (context as MainActivity).window.decorView.rootView.findViewById<ImageButton>(R.id.btnAddCounter)
        val btnBackLst = context.window.decorView.rootView.findViewById<ImageButton>(R.id.btnBackToList)
        btnAddC.visibility = View.GONE
        btnBackLst.visibility = View.VISIBLE
        val fm = context.supportFragmentManager
        val frag = fm.findFragmentByTag(MainActivity.TAG_FRAG_ADDCOUNTER)!!
        fm.beginTransaction()
            .replace(R.id.layContainer, frag)
            .commit()
    }

    private fun dialogRemoveQuery(pos: Int) {
        val removeme = AlertDialog.Builder(context)
            .setIcon(R.drawable.ic_warning_red)
            .setTitle("REMOVE this counter !?")
            .setMessage("Are you sure you want to remove \nthe counter \"${thingsCounted[pos].name}\" ?!")
            .setPositiveButton("CONFIRM"){dialog, _ ->
                countersDB.removeCounter(thingsCounted[pos].dateTime) // from db
                thingsCounted.removeAt(pos) // from data list
                notifyDataSetChanged() // refresh adapter (From here) //
                dialog.dismiss() // close dialog
                // really refresh adapter (to change list accordingly ! )  // some "Action at a distance" ! //
                val frag = (context as MainActivity).supportFragmentManager.findFragmentByTag(MainActivity.TAG_FRAG_COUNTERS)
                (frag as CountersFragment).refreshAdapter()
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
