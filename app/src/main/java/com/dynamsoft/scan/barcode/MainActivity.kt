package com.dynamsoft.scan.barcode

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.dynamsoft.camera.DBR
import android.content.Intent
import android.app.Activity
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private val REQUEST_CODE: Int = 2017
    // https://kotlinlang.org/docs/reference/properties.html#late-initialized-properties
    private lateinit var mTextView: TextView
    private lateinit var mButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        mButton = findViewById(R.id.button) as Button
        mTextView = findViewById(R.id.text) as TextView

        mButton.setOnClickListener {
            val license: String = "AA721B9FAB21454427702FE780B56C50"
            val cameraIntent = Intent(baseContext, DBR::class.java)
            cameraIntent.action = DBR.ACTION_BARCODE
            cameraIntent.putExtra("license", license)

            // avoid calling other phonegap apps
            cameraIntent.`package` = baseContext.getPackageName()

            startActivityForResult(cameraIntent, REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode === REQUEST_CODE) {
            if (resultCode === Activity.RESULT_OK) {
                // https://kotlinlang.org/docs/reference/null-safety.html
                val result = data?.getStringExtra("SCAN_RESULT")
                val format = data?.getStringExtra("SCAN_RESULT_FORMAT")
                // https://stackoverflow.com/questions/33164886/android-textview-do-not-concatenate-text-displayed-with-settext
                mTextView.text = getString(R.string.barcode_result, result, format)
            } else if (resultCode === Activity.RESULT_CANCELED) {
                Toast.makeText(baseContext, "Cancelled", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(baseContext, "Unexpected error", Toast.LENGTH_LONG).show()
            }
        }
    }
}
