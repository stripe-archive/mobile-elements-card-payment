package com.example.app

import java.io.IOException
import java.lang.ref.WeakReference

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
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
    private lateinit var publishableKey: String
    private lateinit var stripe: Stripe

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)
        startCheckout()
    }

    private fun displayAlert(activity: Activity?, title: String, message: String, restartDemo: Boolean = false) {
        if (activity == null) {
            return
        }
        runOnUiThread {
            val builder = AlertDialog.Builder(activity!!)
            builder.setTitle(title)
            builder.setMessage(message)
            if (restartDemo) {
                builder.setPositiveButton("Restart demo") { _, _ ->
                    val cardInputWidget =
                        findViewById<CardInputWidget>(R.id.cardInputWidget)
                    cardInputWidget.clear()
                    startCheckout()
                }
            }
            else {
                builder.setPositiveButton("Ok", null)
            }
            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun startCheckout() {
        val weakActivity = WeakReference<Activity>(this)
        // Load Stripe key from the server
        val request = Request.Builder()
            .url(backendUrl + "stripe-key")
            .get()
            .build()
        httpClient.newCall(request)
            .enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    displayAlert(weakActivity.get(), "Failed to load page", "Error: $e")
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        displayAlert(weakActivity.get(), "Failed to load page", "Error: $response")
                    } else {
                        val responseData = response.body?.string()
                        var json = JSONObject(responseData)

                        // The response from the server includes the Stripe public key and
                        // PaymentIntent details.
                        publishableKey = json.getString("publishableKey")

                        // Use the public key from the server to initialize the Stripe instance.
                        stripe = Stripe(applicationContext, publishableKey)
                    }
                }
            })

        val payButton: Button = findViewById(R.id.payButton)
        payButton.setOnClickListener {
            pay()
        }
    }

    private fun pay() {
        val weakActivity = WeakReference<Activity>(this)
        // Collect card details on the client
        val cardInputWidget =
            findViewById<CardInputWidget>(R.id.cardInputWidget)
        val params = cardInputWidget.paymentMethodCreateParams
        if (params == null) {
            return
        }
        stripe.createPaymentMethod(params, object : ApiResultCallback<PaymentMethod> {
            // Create PaymentMethod failed
            override fun onError(e: Exception) {
                displayAlert(weakActivity.get(), "Payment failed", "Error: $e")
            }
            override fun onSuccess(result: PaymentMethod) {
                // Create a PaymentIntent on the server with a PaymentMethod
                print("Created PaymentMethod")
                pay(result.id, null)
            }
        })
    }

    // Create or confirm a PaymentIntent on the server
    private fun pay(paymentMethod: String?, paymentIntent: String?) {
        val weakActivity = WeakReference<Activity>(this)
        var json = ""
        if (!paymentMethod.isNullOrEmpty()) {
            json = """
                {
                    "useStripeSdk":true,
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
                    displayAlert(weakActivity.get(), "Payment failed", "Error: $e")
                }

                override fun onResponse(call: Call, response: Response) {
                    // Request failed
                    if (!response.isSuccessful) {
                        displayAlert(weakActivity.get(), "Payment failed", "Error: $response")
                    } else {
                        val responseData = response.body?.string()
                        var responseJson = JSONObject(responseData)
                        val payError: String? = responseJson.optString("error")
                        val clientSecret: String? = responseJson.optString("clientSecret")
                        val requiresAction: Boolean? = responseJson.optBoolean("requiresAction")
                        // Payment failed
                        if (payError != null && payError.isNotEmpty()) {
                            displayAlert(weakActivity.get(), "Payment failed", "Error: $payError")
                        }
                        // Payment succeeded
                        else if ((clientSecret != null && clientSecret.isNotEmpty())
                            && (requiresAction == null || requiresAction == false)) {
                            displayAlert(weakActivity.get(), "Payment succeeded", "$clientSecret", restartDemo = true)
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
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    displayAlert(weakActivity.get(), "Payment succeeded", gson.toJson(paymentIntent), restartDemo = true)
                } else if (status == StripeIntent.Status.RequiresPaymentMethod) {
                    // Payment failed – allow retrying using a different payment method
                    displayAlert(weakActivity.get(), "Payment failed", paymentIntent.lastPaymentError!!.message ?: "")
                }
                // After handling a required action on the client, the status of the PaymentIntent is
                // requires_confirmation. You must send the PaymentIntent ID to your backend
                // and confirm it to finalize the payment. This step enables your integration to
                // synchronously fulfill the order on your backend and return the fulfillment result
                // to your client.
                else if (status == StripeIntent.Status.RequiresConfirmation) {
                    print("Re-confirming PaymentIntent after handling a required action")
                    pay(null, paymentIntent.id)
                }
                else {
                    displayAlert(weakActivity.get(), "Payment status unknown", "unhandled status: $status", restartDemo = true)
                }
            }

            override fun onError(e: Exception) {
                // Payment request failed – allow retrying using the same payment method
                displayAlert(weakActivity.get(), "Payment failed", e.toString())
            }
        })
    }

}
