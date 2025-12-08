package com.example.secretsanta

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Color
import android.text.style.ForegroundColorSpan

class RevealActivity : AppCompatActivity() {

    private lateinit var spinnerNames: Spinner
    private lateinit var editPin: EditText
    private lateinit var buttonReveal: Button
    private lateinit var textError: TextView
    private lateinit var textResult: TextView

    private lateinit var bottomRevealSection: LinearLayout

    private var names: List<String> = emptyList()
    private var pins: Map<String, String> = emptyMap()
    private var assignments: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reveal)

        spinnerNames = findViewById(R.id.spinnerNames)
        editPin = findViewById(R.id.editPin)
        buttonReveal = findViewById(R.id.buttonReveal)
        textError = findViewById(R.id.textError)
        textResult = findViewById(R.id.textResult)

        bottomRevealSection = findViewById(R.id.bottomRevealSection)
        bottomRevealSection.visibility = View.GONE   // hide Santa + text at start

        if (!loadDataFromPrefs()) {
            textError.text = "No Secret Santa data found. Ask the organiser to generate it first."
            textError.visibility = TextView.VISIBLE
            buttonReveal.isEnabled = false
            return
        }

        // Populate spinner
        val displayNames = listOf("Select your name") + names
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            displayNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNames.adapter = adapter

        buttonReveal.setOnClickListener {
            revealFriend()
        }
    }

    private fun loadDataFromPrefs(): Boolean {
        val prefs = getSharedPreferences("secret_santa", MODE_PRIVATE)
        val namesJsonStr = prefs.getString("names", null)
        val pinsJsonStr = prefs.getString("pins", null)
        val assignmentsJsonStr = prefs.getString("assignments", null)

        if (namesJsonStr == null || pinsJsonStr == null || assignmentsJsonStr == null) {
            return false
        }

        // Names
        val namesArray = JSONArray(namesJsonStr)
        val namesList = mutableListOf<String>()
        for (i in 0 until namesArray.length()) {
            namesList.add(namesArray.getString(i))
        }
        names = namesList

        // Pins
        val pinsObj = JSONObject(pinsJsonStr)
        val pinsMap = mutableMapOf<String, String>()
        for (key in pinsObj.keys()) {
            pinsMap[key] = pinsObj.getString(key)
        }
        pins = pinsMap

        // Assignments
        val assignObj = JSONObject(assignmentsJsonStr)
        val assignMap = mutableMapOf<String, String>()
        for (key in assignObj.keys()) {
            assignMap[key] = assignObj.getString(key)
        }
        assignments = assignMap

        return true
    }

    private fun revealFriend() {
        textError.visibility = TextView.GONE
        textResult.text = ""
        bottomRevealSection.visibility = View.GONE   // hide before each attempt

        val pos = spinnerNames.selectedItemPosition
        if (pos == 0) {
            textError.text = "Please select your name."
            textError.visibility = TextView.VISIBLE
            return
        }

        val name = spinnerNames.selectedItem.toString()
        val enteredPin = editPin.text.toString().trim()

        if (enteredPin.length != 4) {
            textError.text = "Please enter your 4-digit PIN."
            textError.visibility = TextView.VISIBLE
            return
        }

        val correctPin = pins[name]
        if (correctPin == null) {
            textError.text = "No PIN found for this name. Contact organiser."
            textError.visibility = TextView.VISIBLE
            return
        }

        if (enteredPin != correctPin) {
            textError.text = "Incorrect PIN. Please try again."
            textError.visibility = TextView.VISIBLE
            return
        }

        val friend = assignments[name]
        if (friend == null) {
            textError.text = "No friend assigned. Contact organiser."
            textError.visibility = TextView.VISIBLE
            return
        }

        // Build full reveal message: Hi <name>, your Secret Santa is <friend> 🎄
        val fullText = "Hey $name,\nyour Secret Santa is\n$friend 🎄"
        val spannable = SpannableString(fullText)

        // Where the friend's name appears in the string
        val nameStart = fullText.indexOf(friend)
        val nameEnd = nameStart + friend.length

        if (nameStart >= 0) {
            // 1) Make the FRIEND NAME slightly larger
            spannable.setSpan(
                RelativeSizeSpan(1.4f),
                nameStart,
                nameEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 2) Make the FRIEND NAME bold
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                nameStart,
                nameEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 3) Apply custom font ONLY to FRIEND NAME
            val customFont = ResourcesCompat.getFont(this, R.font.christmas_font)
            if (customFont != null) {
                spannable.setSpan(
                    CustomTypefaceSpan(customFont),
                    nameStart,
                    nameEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // 4) Apply GOLD color to FRIEND NAME
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#FFD700")), // GOLD
                nameStart,
                nameEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Show the reveal section (Santa image + text)
        bottomRevealSection.visibility = View.VISIBLE

        // Set initial animation state
        textResult.alpha = 0f
        textResult.scaleX = 0.8f
        textResult.scaleY = 0.8f

        // Apply styled text
        textResult.text = spannable

        // Simple pop-in animation
        textResult.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .start()
    }
}

/**
 * Span to apply a custom Typeface on part of a text (used for friend's name).
 */
class CustomTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {

    override fun updateDrawState(paint: TextPaint) {
        apply(paint)
    }

    override fun updateMeasureState(paint: TextPaint) {
        apply(paint)
    }

    private fun apply(paint: Paint) {
        paint.typeface = typeface
    }
}
