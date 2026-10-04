package com.lulux.detector

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.lulux.detector.capture.ScreenCaptureService
import com.lulux.detector.config.RecoilConfig
import com.lulux.detector.overlay.FloatingOverlayService
import com.lulux.detector.storage.LocalStorage

class MainActivity : Activity() {
    companion object { private const val REQ_CAPTURE = 7001 }

    private lateinit var master: Switch
    private lateinit var status: TextView
    private lateinit var debug: TextView
    private lateinit var storage: LocalStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storage = LocalStorage(this)

        master = findViewById(R.id.masterSwitch)
        status = findViewById(R.id.status)
        debug = findViewById(R.id.debug)

        createNotificationChannel()

        master.setOnCheckedChangeListener { _, checked ->
            ScreenCaptureService.engine?.setEnabled(checked)
            updateDebug()
        }

        findViewById<Button>(R.id.startCapture).setOnClickListener { requestCapture() }
        findViewById<Button>(R.id.stopCapture).setOnClickListener {
            startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
        }
        findViewById<Button>(R.id.startFloating).setOnClickListener { showFloating() }
        findViewById<Button>(R.id.stopFloating).setOnClickListener {
            stopService(Intent(this, FloatingOverlayService::class.java))
        }

        findViewById<Button>(R.id.addWeapon).setOnClickListener { trainLabel("weapon", "Nama senjata, mis. M416") }
        findViewById<Button>(R.id.addScope).setOnClickListener { trainLabel("scope", "Nama scope, mis. 3x") }
        findViewById<Button>(R.id.trainAds).setOnClickListener { trainAdsDialog() }
        findViewById<Button>(R.id.trainStance).setOnClickListener { trainStanceDialog() }
        findViewById<Button>(R.id.config).setOnClickListener { configDialog() }
        findViewById<Button>(R.id.calibrate).setOnClickListener {
            Toast.makeText(this, "Buka game, lalu tekan tombol ROI di panel floating.", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestCapture() {
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(pm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE || resultCode != RESULT_OK || data == null) return
        val i = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        startForegroundService(i)
        Toast.makeText(this, "Screen capture aktif. Tampilkan panel floating lalu nyalakan Master.", Toast.LENGTH_SHORT).show()
    }

    private fun showFloating() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Aktifkan izin 'Tampil di atas aplikasi lain', lalu kembali dan tekan lagi.",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, FloatingOverlayService::class.java))
    }

    // Simpan "target" training. Sample diambil dari panel floating (tombol Capture) saat di dalam game.
    private fun setTarget(group: String, label: String) {
        storage.saveTrainTarget(group, label)
        Toast.makeText(
            this,
            "Target: $group = $label. Buka game, aktifkan kondisinya, tekan Capture di panel floating.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun trainLabel(group: String, title: String) {
        val input = EditText(this)
        input.hint = title
        AlertDialog.Builder(this).setTitle("Nama label").setView(input)
            .setMessage("Setelah disimpan, buka game dengan kondisi itu aktif, lalu tekan Capture di panel floating.")
            .setPositiveButton("Simpan target") { _, _ ->
                val label = input.text.toString().trim()
                if (label.isNotEmpty()) setTarget(group, label)
            }.setNegativeButton("Batal", null).show()
    }

    private fun trainAdsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Training ADS")
            .setItems(arrayOf("ADS terbuka (scope aktif)", "ADS tertutup (tidak ADS)")) { _, which ->
                if (which == 0) setTarget("ads", "open") else setTarget("ads_off", "off")
            }.show()
    }

    private fun trainStanceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Training Stance")
            .setItems(arrayOf("Tombol 4 aktif (crouch)", "Tombol 5 aktif (prone)")) { _, which ->
                if (which == 0) setTarget("stance_crouch", "CROUCH") else setTarget("stance_prone", "PRONE")
            }.show()
    }

    private fun configDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val w = EditText(this); w.hint = "Weapon"
        val s = EditText(this); s.hint = "Scope"
        val st = EditText(this); st.hint = "STAND / CROUCH / PRONE"
        layout.addView(w); layout.addView(s); layout.addView(st)
        AlertDialog.Builder(this).setTitle("Tambah Config").setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val list = storage.loadConfigs()
                list.removeAll {
                    it.weapon.equals(w.text.toString(), true) &&
                        it.scope.equals(s.text.toString(), true) &&
                        it.stance.equals(st.text.toString(), true)
                }
                list += RecoilConfig(w.text.toString(), s.text.toString(), st.text.toString(), true)
                storage.saveConfigs(list)
                Toast.makeText(this, "Config tersimpan (berlaku setelah Start Capture ulang).", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Batal", null).show()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("capture", "Screen Capture", NotificationManager.IMPORTANCE_LOW))
    }

    private fun updateDebug() {
        debug.text = "MASTER: ${master.isChecked}\nCAPTURE: ${ScreenCaptureService.running}\n"
        val s = ScreenCaptureService.engine?.getState()
        if (s != null) {
            debug.append("ADS: ${s.adsOpen}\nWEAPON: ${s.weapon}\nSCOPE: ${s.scope}\nSTANCE: ${s.stance}\nCONF: ${"%.2f".format(s.confidence)}\nCONFIG: ${ScreenCaptureService.engine?.getMatchedConfig()}")
        }
    }

    override fun onResume() {
        super.onResume()
        updateDebug()
    }
}
