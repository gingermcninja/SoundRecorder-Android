package com.example.soundrecorder_android

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val button1 = findViewById<Button>(R.id.button1)
        val button2 = findViewById<Button>(R.id.button2)
        val button3 = findViewById<Button>(R.id.button3)
        val button4 = findViewById<Button>(R.id.button4)
        val button5 = findViewById<Button>(R.id.button5)
        val button6 = findViewById<Button>(R.id.button6)

        val buttons = listOf(button1, button2, button3, button4, button5, button6)

        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                Toast.makeText(this, "Button ${index + 1} clicked!", Toast.LENGTH_SHORT).show()
            }
        }

    }
}