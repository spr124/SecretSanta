package com.example.secretsanta

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var peopleContainer: LinearLayout
    private lateinit var buttonAddPerson: Button
    private lateinit var buttonShuffle: Button
    private lateinit var buttonGoToReveal: Button
    private lateinit var textError: TextView
    private lateinit var textResult: TextView

    private lateinit var textShareTitle: TextView
    private lateinit var spinnerShareName: Spinner
    private lateinit var buttonSendSms: Button
    private lateinit var buttonSendWhatsApp: Button
    private lateinit var shareButtonsRow: LinearLayout

    // Last generated data (for sending PINs)
    private var lastNames: List<String> = emptyList()
    private var lastPins: Map<String, String> = emptyMap()
    private var lastPhones: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        peopleContainer = findViewById(R.id.peopleContainer)
        buttonAddPerson = findViewById(R.id.buttonAddPerson)
        buttonShuffle = findViewById(R.id.buttonShuffle)
        buttonGoToReveal = findViewById(R.id.buttonGoToReveal)
        textError = findViewById(R.id.textError)
        textResult = findViewById(R.id.textResult)

        textShareTitle = findViewById(R.id.textShareTitle)
        spinnerShareName = findViewById(R.id.spinnerShareName)
        buttonSendSms = findViewById(R.id.buttonSendSms)
        buttonSendWhatsApp = findViewById(R.id.buttonSendWhatsApp)
        shareButtonsRow = findViewById(R.id.shareButtonsRow)

        // Start with a few empty rows
        repeat(3) { addPersonRow() }

        buttonAddPerson.setOnClickListener {
            addPersonRow()
        }

        buttonShuffle.setOnClickListener {
            generateSecretSanta()
        }

        buttonGoToReveal.setOnClickListener {
            startActivity(Intent(this, RevealActivity::class.java))
        }

        buttonSendSms.setOnClickListener {
            sendSelectedViaSms()
        }

        buttonSendWhatsApp.setOnClickListener {
            sendSelectedViaWhatsApp()
        }
    }

    private fun addPersonRow() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }

        val nameEdit = EditText(this).apply {
            hint = "Name"
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(8)
            }
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val phoneEdit = EditText(this).apply {
            hint = "Phone"
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            inputType = InputType.TYPE_CLASS_PHONE
        }

        row.addView(nameEdit)
        row.addView(phoneEdit)
        peopleContainer.addView(row)
    }

    private fun generateSecretSanta() {
        textError.visibility = View.GONE
        textResult.text = ""

        val names = mutableListOf<String>()
        val phones = mutableMapOf<String, String>()

        // Read all rows
        for (i in 0 until peopleContainer.childCount) {
            val row = peopleContainer.getChildAt(i) as LinearLayout
            val nameEdit = row.getChildAt(0) as EditText
            val phoneEdit = row.getChildAt(1) as EditText

            val name = nameEdit.text.toString().trim()
            val phone = phoneEdit.text.toString().trim()

            if (name.isNotEmpty()) {
                names.add(name)
                phones[name] = phone // phone can be empty
            }
        }

        if (names.size < 2) {
            textError.text = "Please enter at least 2 names."
            textError.visibility = View.VISIBLE
            return
        }

        val assignments = generateDerangement(names)
        if (assignments == null) {
            textError.text = "Could not generate a valid shuffle. Try again."
            textError.visibility = View.VISIBLE
            return
        }

        val pins = generatePins(names)

        // Save to fields for sharing
        lastNames = names
        lastPins = pins
        lastPhones = phones

        // Save everything to SharedPreferences (for RevealActivity)
        val prefs = getSharedPreferences("secret_santa", MODE_PRIVATE)

        val namesJson = JSONArray()
        names.forEach { namesJson.put(it) }

        val assignmentsJson = JSONObject()
        assignments.forEach { (giver, receiver) ->
            assignmentsJson.put(giver, receiver)
        }

        val pinsJson = JSONObject()
        pins.forEach { (name, pin) ->
            pinsJson.put(name, pin)
        }

        val phonesJson = JSONObject()
        phones.forEach { (name, phone) ->
            phonesJson.put(name, phone)
        }

        prefs.edit()
            .putString("names", namesJson.toString())
            .putString("assignments", assignmentsJson.toString())
            .putString("pins", pinsJson.toString())
            .putString("phones", phonesJson.toString())
            .apply()

        // Show Name + PIN + Phone to organiser
        val sb = StringBuilder()
        sb.append("Secret Santa generated.\n\n")
        sb.append("Share these PINs with each person:\n\n")
        names.forEach { name ->
            val pin = pins[name]
            val phone = phones[name].orEmpty()
            sb.append("$name  -  PIN: $pin")
            if (phone.isNotEmpty()) {
                sb.append("  (Phone: $phone)")
            }
            sb.append("\n")
        }
        sb.append("\nYou can also send the PIN directly from below.")

        textResult.text = sb.toString()

        // Enable share UI
        setupShareUi(names)
    }

    private fun setupShareUi(names: List<String>) {
        if (names.isEmpty()) return

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            names
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerShareName.adapter = adapter

        textShareTitle.visibility = View.VISIBLE
        spinnerShareName.visibility = View.VISIBLE
        shareButtonsRow.visibility = View.VISIBLE
    }

    private fun sendSelectedViaSms() {
        if (lastNames.isEmpty()) {
            Toast.makeText(this, "Generate Secret Santa first.", Toast.LENGTH_SHORT).show()
            return
        }

        val name = spinnerShareName.selectedItem as? String ?: return
        val pin = lastPins[name]
        val phone = lastPhones[name]

        if (pin == null || phone.isNullOrBlank()) {
            Toast.makeText(this, "No phone or PIN for $name.", Toast.LENGTH_SHORT).show()
            return
        }

        val message = "Hi $name, your Secret Santa PIN is $pin. Keep it secret and use the app to reveal your friend!"

        try {
            // More compatible SMS intent
            val uri = Uri.parse("sms:$phone")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                putExtra("sms_body", message)
            }
            startActivity(intent)   // Let Android choose the SMS app
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Could not open SMS app.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun sendSelectedViaWhatsApp() {
        if (lastNames.isEmpty()) {
            Toast.makeText(this, "Generate Secret Santa first.", Toast.LENGTH_SHORT).show()
            return
        }

        val name = spinnerShareName.selectedItem as? String ?: return
        val pin = lastPins[name]
        val phoneRaw = lastPhones[name]

        if (pin == null || phoneRaw.isNullOrBlank()) {
            Toast.makeText(this, "No phone or PIN for $name.", Toast.LENGTH_SHORT).show()
            return
        }

        // Keep only digits
        val digits = phoneRaw.replace("\\D".toRegex(), "")
        if (digits.isEmpty()) {
            Toast.makeText(this, "Invalid phone number for $name.", Toast.LENGTH_SHORT).show()
            return
        }

        // Assume India by default – change "91" if needed
        val phoneWithCountry = if (digits.startsWith("91")) digits else "91$digits"

        val message = "Hi $name, your Secret Santa PIN is $pin. Keep it secret and use the app to reveal your friend!"

        try {
            // Open WhatsApp (or browser with wa.me)
            val url = "https://wa.me/$phoneWithCountry?text=" +
                    URLEncoder.encode(message, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)   // Browser or WhatsApp will handle it
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Could not open WhatsApp or browser.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun generatePins(names: List<String>): Map<String, String> {
        val pins = mutableMapOf<String, String>()
        val used = mutableSetOf<String>()

        for (name in names) {
            var pin: String
            do {
                pin = Random.nextInt(1000, 10000).toString()
            } while (used.contains(pin))
            used.add(pin)
            pins[name] = pin
        }
        return pins
    }

    private fun generateDerangement(list: List<String>): Map<String, String>? {
        val n = list.size
        val maxTries = 100

        repeat(maxTries) {
            val shuffled = list.shuffled(Random(System.currentTimeMillis()))
            val hasSelf = list.indices.any { i -> list[i] == shuffled[i] }
            if (hasSelf) return@repeat
            return list.indices.associate { i ->
                list[i] to shuffled[i]
            }
        }
        return null
    }

    private fun dp(value: Int): Int {
        val scale = resources.displayMetrics.density
        return (value * scale + 0.5f).toInt()
    }
}
