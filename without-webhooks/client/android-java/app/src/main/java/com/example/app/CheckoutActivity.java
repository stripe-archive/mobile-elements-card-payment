package com.example.app;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import com.stripe.android.ApiResultCallback;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.PaymentIntentResult;
import com.stripe.android.Stripe;
import com.stripe.android.exception.APIConnectionException;
import com.stripe.android.exception.APIException;
import com.stripe.android.exception.AuthenticationException;
import com.stripe.android.exception.InvalidRequestException;
import com.stripe.android.model.ConfirmPaymentIntentParams;
import com.stripe.android.model.PaymentIntent;
import com.stripe.android.model.PaymentMethod;
import com.stripe.android.model.PaymentMethodCreateParams;
import com.stripe.android.model.StripeIntent;
import com.stripe.android.view.CardInputWidget;

import org.jetbrains.annotations.NotNull;


public class CheckoutActivity extends AppCompatActivity {
    /**
     * To run this app, you'll need to first run the sample server locally.
     * Follow the "How to run locally" instructions in the root directory's README.md to get started.
     * Once you've started the server, open http://localhost:4242 in your browser to check that the
     * server is running locally.
     * After verifying the sample server is running locally, build and run the app using the
     * Android emulator.
     */
    // 10.0.2.2 is the Android emulator's alias to localhost
    private String backendUrl = "http://10.0.2.2:4242/";
    private OkHttpClient httpClient = new OkHttpClient();
    private Stripe stripe;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);
        loadPage();
    }

    private void loadPage() {
        // Clear the card widget
        CardInputWidget cardInputWidget = findViewById(R.id.cardInputWidget);
        cardInputWidget.clear();

        // Load Stripe publishable key from the server
        Request request = new Request.Builder()
                .url(backendUrl + "stripe-key")
                .get()
                .build();
        httpClient.newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> {
                            Context applicationContext = getApplicationContext();
                            Toast.makeText(applicationContext, "Error: " + e.toString(), Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onResponse(Call call, final Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Context applicationContext = getApplicationContext();
                                Toast.makeText(applicationContext, "Error: " + response.toString(), Toast.LENGTH_LONG).show();
                            });
                        } else {
                            Gson gson = new Gson();
                            Type type = new TypeToken<Map<String, String>>(){}.getType();
                            Map<String, String> map = gson.fromJson(response.body().string(), type);
                            String stripePublishableKey = map.get("publishableKey");

                            runOnUiThread(() -> {
                                // Use the publishable key from the server to initialize the Stripe instance.
                                Context applicationContext = getApplicationContext();
                                PaymentConfiguration.init(applicationContext, stripePublishableKey);
                                stripe = new Stripe(applicationContext, stripePublishableKey);

                                // Hook up the pay button to the card widget and stripe instance
                                Button payButton = findViewById(R.id.payButton);
                                payButton.setOnClickListener((View view) -> {
                                    pay();
                                });
                            });
                        }
                    }
                });
    }

    private void pay() {
        CardInputWidget cardInputWidget = findViewById(R.id.cardInputWidget);
        PaymentMethodCreateParams params = cardInputWidget.getPaymentMethodCreateParams();

        if (params == null) {
            return;
        }
        stripe.createPaymentMethod(params, new ApiResultCallback<PaymentMethod>() {
            @Override
            public void onSuccess(@NonNull PaymentMethod result) {
                // Create and confirm the PaymentIntent by calling the sample server's /pay endpoint.
                pay(result.id, null);
            }

            @Override
            public void onError(@NonNull Exception e) {

            }
        });
    }

    private void pay(String paymentMethod, String paymentIntent) {
        WeakReference<Activity> weakActivity = new WeakReference<>(this);
        MediaType mediaType = MediaType.get("application/json; charset=utf-8");
        String json;
        if (paymentMethod != null) {
            json = "{"
                    + "\"paymentMethodId\":" + "\"" + paymentMethod + "\","
                    + "\"currency\":\"usd\","
                    + "\"items\":["
                    + "{\"id\":\"photo_subscription\"}"
                    + "]"
                    + "}";
        } else {
            json = "{"
                    + "\"paymentIntentId\":" +  "\"" + paymentIntent + "\""
                    + "}";
        }
        RequestBody body = RequestBody.create(json, mediaType);
        Request request = new Request.Builder()
                .url(backendUrl + "pay")
                .post(body)
                .build();
        httpClient.newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> {
                            Context applicationContext = getApplicationContext();
                            Toast.makeText(applicationContext, "Error: " + e.toString(), Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onResponse(Call call, final Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Context applicationContext = getApplicationContext();
                                Toast.makeText(applicationContext, "Error: " + response.toString(), Toast.LENGTH_LONG).show();
                            });
                        } else {
                            Gson gson = new Gson();
                            Type type = new TypeToken<Map<String, String>>(){}.getType();
                            Map<String, String> responseMap = gson.fromJson(response.body().string(), type);

                            String error = responseMap.get("error");
                            String paymentIntentClientSecret = responseMap.get("clientSecret");
                            String requiresAction = responseMap.get("requiresAction");

                            if (error != null) {
                                displayAlert(weakActivity.get(), "Error", error.toString(), false);
                            } else if (paymentIntentClientSecret != null && requiresAction != "true") {
                                displayAlert(weakActivity.get(), "Payment succeeded", paymentIntentClientSecret, true);
                            } else if (paymentIntentClientSecret != null && requiresAction == "true") {
                                runOnUiThread(() -> {
                                    stripe.authenticatePayment(weakActivity.get(), paymentIntentClientSecret);
                                });
                            }
                        }
                    }
                });
    }

    private void displayAlert(Activity activity, String title, String message, boolean restartDemo) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(title);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            builder.setMessage(message);
            if (restartDemo) {
                builder.setPositiveButton("Restart demo", (DialogInterface dialog, int index) -> {
                    loadPage();
                });
            } else {
                builder.setPositiveButton("Ok", null);
            }
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        WeakReference<Activity> weakActivity = new WeakReference<>(this);

        // Handle the result of stripe.confirmPayment
        stripe.onPaymentResult(requestCode, data, new ApiResultCallback<PaymentIntentResult>() {
            @Override
            public void onSuccess(@NonNull PaymentIntentResult result) {
                PaymentIntent paymentIntent = result.getIntent();
                PaymentIntent.Status status = paymentIntent.getStatus();
                if (status == PaymentIntent.Status.Succeeded) {
                    // Payment completed successfully
                    runOnUiThread(() -> {
                        if (weakActivity.get() != null) {
                            Activity activity = weakActivity.get();
                            Gson gson = new GsonBuilder().setPrettyPrinting().create();
                            displayAlert(activity, "Payment completed", gson.toJson(paymentIntent), true);
                        }
                    });
                } else if (status == PaymentIntent.Status.RequiresPaymentMethod) {
                    // Payment failed – allow retrying using a different payment method
                    runOnUiThread(() -> {
                        if (weakActivity.get() != null) {
                            Activity activity = weakActivity.get();
                            displayAlert(activity, "Payment failed", paymentIntent.getLastPaymentError().message, false);
                        }
                    });
                } else if (status == PaymentIntent.Status.RequiresConfirmation) {
                    // After handling a required action on the client, the status of the PaymentIntent is
                    // requires_confirmation. You must send the PaymentIntent ID to your backend
                    // and confirm it to finalize the payment. This step enables your integration to
                    // synchronously fulfill the order on your backend and return the fulfillment result
                    // to your client.
                    pay(null, paymentIntent.getId());
                }
            }

            @Override
            public void onError(@NonNull Exception e) {
                // Payment request failed – allow retrying using the same payment method
                runOnUiThread(() -> {
                    if (weakActivity.get() != null) {
                        Activity activity = weakActivity.get();
                        displayAlert(activity, "Error", e.toString(), false);
                    }
                });
            }
        });
    }
}
