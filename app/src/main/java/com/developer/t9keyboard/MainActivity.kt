package com.developer.t9keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnEnable = findViewById<Button>(R.id.btnEnableKeyboard)
        val btnSelect = findViewById<Button>(R.id.btnSelectKeyboard)
        val etTest = findViewById<EditText>(R.id.etTestInput)

        btnEnable.setOnClickListener {
            // Buka Pengaturan Masukan / Keyboard di Android / ColorOS
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        btnSelect.setOnClickListener {
            // Tampilkan dialog pemilih keyboard bawaan Android
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }
}
