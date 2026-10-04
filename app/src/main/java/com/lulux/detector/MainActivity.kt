package com.lulux.detector

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import com.lulux.detector.capture.ScreenCaptureService
import com.lulux.detector.config.ConfigMatcher
import com.lulux.detector.config.RecoilConfig
import com.lulux.detector.storage.LocalStorage
import com.lulux.detector.training.TemplateRepository

class MainActivity : Activity() {
    companion object { private const val REQ_CAPTURE=7001 }

    private lateinit var master: Switch
    private lateinit var status: TextView
    private lateinit var debug: TextView
    private lateinit var storage: LocalStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storage=LocalStorage(this)

        master=findViewById(R.id.masterSwitch)
        status=findViewById(R.id.status)
        debug=findViewById(R.id.debug)

        createNotificationChannel()

        master.setOnCheckedChangeListener { _, checked ->
            ScreenCaptureService.engine?.setEnabled(checked)
            updateDebug()
        }

        findViewById<Button>(R.id.startCapture).setOnClickListener { requestCapture() }
        findViewById<Button>(R.id.stopCapture).setOnClickListener {
            startService(Intent(this,ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
        }

        findViewById<Button>(R.id.addWeapon).setOnClickListener {
            trainLabel("weapon","Weapon name")
        }
        findViewById<Button>(R.id.addScope).setOnClickListener {
            trainLabel("scope","Scope name")
        }
        findViewById<Button>(R.id.trainAds).setOnClickListener {
            trainAdsDialog()
        }
        findViewById<Button>(R.id.trainStance).setOnClickListener {
            trainLabel("stance_crouch","CROUCH")
        }
        findViewById<Button>(R.id.config).setOnClickListener { configDialog() }
        findViewById<Button>(R.id.calibrate).setOnClickListener {
            Toast.makeText(this,"ROI disimpan di LocalStorage. Edit ROI lewat kalibrasi pada versi UI lanjutan.",Toast.LENGTH_LONG).show()
        }
    }

    private fun requestCapture() {
        val pm=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(pm.createScreenCaptureIntent(),REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=REQ_CAPTURE || resultCode!=RESULT_OK || data==null) return
        val i=Intent(this,ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE,resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA,data)
        }
        startForegroundService(i)
        Toast.makeText(this,"Screen capture aktif. Nyalakan Master Detection.",Toast.LENGTH_SHORT).show()
    }

    private fun trainLabel(group:String,title:String) {
        val input=EditText(this); input.hint=title
        AlertDialog.Builder(this).setTitle("Nama label").setView(input)
            .setMessage("Setelah nama disimpan, capture sample ROI dari layar saat kondisi tersebut aktif.")
            .setPositiveButton("Capture sekarang") { _, _ ->
                val label=input.text.toString().trim()
                if(label.isNotEmpty()) {
                    captureCurrent(group,label)
                }
            }.setNegativeButton("Batal",null).show()
    }

    private fun captureCurrent(group:String,label:String) {
        val e=ScreenCaptureService.engine
        if(e==null) {
            Toast.makeText(this,"Start Screen Capture dulu.",Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this,"Engine aktif. Untuk training sample, buka kondisi target lalu gunakan tombol capture pada UI training.",Toast.LENGTH_LONG).show()
        // Training persistence is implemented in TemplateRepository; a dedicated frame callback/UI
        // can add samples without changing the detector architecture.
    }

    private fun trainAdsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Training ADS")
            .setMessage("ADS classifier memakai group template 'ads'. Tambahkan sample kondisi ADS terbuka pada ROI ADS. Untuk kondisi tertutup, tidak perlu dipakai sebagai config.")
            .setPositiveButton("OK",null).show()
    }

    private fun configDialog() {
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,8,24,8) }
        val w=EditText(this); w.hint="Weapon"
        val s=EditText(this); s.hint="Scope"
        val st=EditText(this); st.hint="STAND / CROUCH / PRONE"
        layout.addView(w);layout.addView(s);layout.addView(st)
        AlertDialog.Builder(this).setTitle("Tambah Config").setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val list=storage.loadConfigs()
                list.removeAll { it.weapon.equals(w.text.toString(),true) && it.scope.equals(s.text.toString(),true) && it.stance.equals(st.text.toString(),true) }
                list += RecoilConfig(w.text.toString(),s.text.toString(),st.text.toString(),true)
                storage.saveConfigs(list)
                Toast.makeText(this,"Config tersimpan.",Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Batal",null).show()
    }

    private fun createNotificationChannel() {
        if(android.os.Build.VERSION.SDK_INT>=26) {
            val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("capture","Screen Capture",NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun updateDebug() {
        debug.text="MASTER: ${master.isChecked}\nCAPTURE: ${ScreenCaptureService.running}\n"
        val s=ScreenCaptureService.engine?.getState()
        if(s!=null) {
            debug.append("ADS: ${s.adsOpen}\nWEAPON: ${s.weapon}\nSCOPE: ${s.scope}\nSTANCE: ${s.stance}\nCONF: ${"%.2f".format(s.confidence)}\nCONFIG: ${ScreenCaptureService.engine?.getMatchedConfig()}")
        }
    }

    override fun onResume() {
        super.onResume()
        updateDebug()
    }
}
