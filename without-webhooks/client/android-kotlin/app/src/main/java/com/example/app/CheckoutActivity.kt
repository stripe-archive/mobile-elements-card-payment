package com.example.app

import java.io.IOException
import java.lang.ref.WeakReference

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

import com.google.gson.GsonBuilder
import org.json.JSONObject

import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentIntentResult
import com.stripe.android.Stripe
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.StripeIntent
import com.stripe.android.view.CardInputWidget


class CheckoutActivity : AppCompatActivity() {

    /**
     * To run this app, you'll need to first run the sample server locally.
     * Follow the "How to run locally" instructions in the root directory's README.md to get started.
     * Once you've started the server, open http://localhost:4242 in your browser to check that the
     * server is running locally.
     * After verifying the sample server is running locally, build and run the app using the
     * Android emulator.
     */
    // 10.0.2.2 is the Android emulator's alias to localhost
    private val backendUrl = "http://10.0.2.2:4242/"
    private val httpClient = OkHttpClient()
    private lateinit var stripePublicKey: String
    private lateinit var paymentIntentClientSecret: String
    private lateinit var stripe: Stripe

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)
        loadPage()
    }

    private fun loadPage() {
        // Load Stripe key from the server
        val request = Request.Builder()
            .url(backendUrl + "stripe-key")
            .get()
            .build()
        httpClient.newCall(request)
            .enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Error: $e", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        runOnUiThread {
                            Toast.makeText(applicationContext, "Error: $response", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val responseData = response.body?.string()
                        var json = JSONObject(responseData)

                        // The response from the server includes the Stripe public key and
                        // PaymentIntent details.
                        stripePublicKey = json.getString("publicKey")

                        // Use the public key from the server to initialize the Stripe instance.
                        stripe = Stripe(applicationContext, stripePublicKey)
                    }
                }
            })

        val payButton: Button = findViewById(R.id.payButton)
        payButton.setOnClickListener {
            pay()
        }
    }

    private fun pay() {
        // Collect card details on the client
        val cardInputWidget =
            findViewById<CardInputWidget>(R.id.cardInputWidget)
        val params = cardInputWidget.paymentMethodCreateParams
        if (params == null) {
            return
        }
        stripe.createPaymentMethod(params!!, object : ApiResultCallback<PaymentMethod> {
            // Create PaymentMethod failed
            override fun onError(e: Exception) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Error: $e", Toast.LENGTH_LONG).show()
                }
            }
            override fun onSuccess(result: PaymentMethod) {
                // Create a PaymentIntent on the server with a PaymentMethod
                print("Created PaymentMethod")
                confirm(result.id, null)
            }
        })
    }

    private fun confirm(paymentMethod: String?, paymentIntent: String?) {
        val weakActivity = WeakReference<Activity>(this)
        var json = ""
        if (!paymentMethod.isNullOrEmpty()) {
            json = """
                {
                    "paymentMethodId":"$paymentMethod",
                    "currency":"usd",
                    "items": [
                        {"id":"photo_subscription"}
                    ]
                }
                """
        }
        else if (!paymentIntent.isNullOrEmpty()) {
            json = """
                {
                    "paymentIntentId":"$paymentIntent"
                }
                """
        }
        // Create a PaymentIntent on the server
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(backendUrl + "pay")
            .post(body)
            .build()
        httpClient.newCall(request)
            .enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Request failed
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Error: $e", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    // Request failed
                    if (!response.isSuccessful) {
                        runOnUiThread {
                            Toast.makeText(applicationContext, "Error: $response", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val responseData = response.body?.string()
                        var json = JSONObject(responseData)
                        val payError: String? = json.optString("error")
                        val clientSecret: String? = json.optString("clientSecret")
                        val requiresAction: Boolean? = json.optBoolean("requiresAction")
                        // Payment failed
                        if (payError != null && payError.isNotEmpty()) {
                            runOnUiThread {
                                Toast.makeText(applicationContext, "Error: $payError", Toast.LENGTH_LONG).show()
                            }
                        }
                        // Payment succeeded
                        else if ((clientSecret != null && clientSecret.isNotEmpty())
                            && (requiresAction == null || requiresAction == false)) {
                            runOnUiThread {
                                if (weakActivity.get() != null) {
                                    val activity = weakActivity.get()!!
                                    val builder = AlertDialog.Builder(activity)
                                    builder.setTitle("Payment completed")
                                    builder.setPositiveButton("Restart demo") { _, _ ->
                                        val cardInputWidget =
                                            findViewById<CardInputWidget>(R.id.cardInputWidget)
                                        cardInputWidget.clear()
                                        loadPage()
                                    }
                                    val dialog = builder.create()
                                    dialog.show()
                                }
                            }
                        }
                        // Payment requires additional actions
                        else if ((clientSecret != null && clientSecret.isNotEmpty())
                            && requiresAction == true) {
                            runOnUiThread {
                                if (weakActivity.get() != null) {
                                    val activity = weakActivity.get()!!
                                    stripe.authenticatePayment(activity, clientSecret)
                                }
                            }
                        }
                    }
                }
            })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val weakActivity = WeakReference<Activity>(this)

        // Handle the result of stripe.authenticatePayment
        stripe.onPaymentResult(requestCode, data, object : ApiResultCallback<PaymentIntentResult> {
            override fun onSuccess(result: PaymentIntentResult) {
                val paymentIntent = result.intent
                val status = paymentIntent.status
                if (status == StripeIntent.Status.Succeeded) {
                    // Payment completed successfully
                    runOnUiThread {
                        if (weakActivity.get() != null) {
                            val activity = weakActivity.get()!!
                            val builder = AlertDialog.Builder(activity)
                            builder.setTitle("Payment completed")
                            val gson = GsonBuilder().setPrettyPrinting().create()
                            builder.setMessage(gson.toJson(paymentIntent))
                            builder.setPositiveButton("Restart demo") { _, _ ->
                                loadPage()
                            }
                            val dialog = builder.create()
                            dialog.show()
                        }
                    }
                } else if (status == StripeIntent.Status.RequiresPaymentMethod) {
                    // Payment failed – allow retrying using a different payment method
                    runOnUiThread {
                        if (weakActivity.get() != null) {
                            val activity = weakActivity.get()!!
                            val builder = AlertDialog.Builder(activity)
                            builder.setTitle("Payment failed")
                            builder.setMessage(paymentIntent.lastPaymentError!!.message)
                            builder.setPositiveButton("Ok") { _, _ ->
                                val cardInputWidget =
                                    findViewById<CardInputWidget>(R.id.cardInputWidget)
                                cardInputWidget.clear()
                            }
                            val dialog = builder.create()
                            dialog.show()
                        }
                    }
                }
                else if (status == StripeIntent.Status.RequiresConfirmation) {
                    print("Re-confirming PaymentIntent after handling action")
                    confirm(null, paymentIntent.id)
                }
                else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "unhandled status: $status", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onError(e: Exception) {
                // Payment request failed – allow retrying using the same payment method
                runOnUiThread {
                    if (weakActivity.get() != null) {
                        val activity = weakActivity.get()!!
                        val builder = AlertDialog.Builder(activity)
                        builder.setMessage(e.toString())
                        builder.setPositiveButton("Ok", null)
                        val dialog = builder.create()
                        dialog.show()
                    }
                }
            }
        })
    }

}
