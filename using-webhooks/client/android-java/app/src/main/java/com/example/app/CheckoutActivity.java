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
import com.stripe.android.PaymentIntentResult;
import com.stripe.android.Stripe;
import com.stripe.android.model.ConfirmPaymentIntentParams;
import com.stripe.android.model.PaymentIntent;
import com.stripe.android.model.PaymentMethodCreateParams;
import com.stripe.android.view.CardInputWidget;


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
    private String paymentIntentClientSecret;
    private Stripe stripe;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);
        startCheckout();
    }

    private void startCheckout() {
        // Create a PaymentIntent by calling the sample server's /create-payment-intent endpoint.
        MediaType mediaType = MediaType.get("application/json; charset=utf-8");
        String json = "{"
                + "\"currency\":\"usd\","
                + "\"items\":["
                + "{\"id\":\"photo_subscription\"}"
                + "]"
                + "}";
        RequestBody body = RequestBody.create(json, mediaType);
        Request request = new Request.Builder()
                .url(backendUrl + "create-payment-intent")
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

                            // The response from the server includes the Stripe public key and
                            // PaymentIntent details.
                            String stripePublishableKey = responseMap.get("publishableKey");
                            paymentIntentClientSecret = responseMap.get("clientSecret");

                            // Use the key from the server to initialize the Stripe instance.
                            stripe = new Stripe(getApplicationContext(), stripePublishableKey);
                        }
                    }
                });

        // Hook up the pay button to the card widget and stripe instance
        Button payButton = findViewById(R.id.payButton);
        payButton.setOnClickListener((View view) -> {
            CardInputWidget cardInputWidget = findViewById(R.id.cardInputWidget);
            PaymentMethodCreateParams params = cardInputWidget.getPaymentMethodCreateParams();
            if (params != null) {
                ConfirmPaymentIntentParams confirmParams = ConfirmPaymentIntentParams
                        .createWithPaymentMethodCreateParams(params, paymentIntentClientSecret);
                stripe.confirmPayment(this, confirmParams);
            }
        });
    }

    private void displayAlert(@NonNull Activity activity, @NonNull String title, @NonNull String message, boolean restartDemo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        builder.setMessage(message);
        if (restartDemo) {
            builder.setPositiveButton("Restart demo", (DialogInterface dialog, int index) -> {
                CardInputWidget cardInputWidget = findViewById(R.id.cardInputWidget);
                cardInputWidget.clear();
                startCheckout();
            });
        } else {
            builder.setPositiveButton("Ok", null);
        }
        AlertDialog dialog = builder.create();
        dialog.show();
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
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    displayAlert(weakActivity.get(), "Payment completed", gson.toJson(paymentIntent), true);
                } else if (status == PaymentIntent.Status.RequiresPaymentMethod) {
                    // Payment failed – allow retrying using a different payment method
                    displayAlert(weakActivity.get(), "Payment failed", paymentIntent.getLastPaymentError().message, false);
                }
            }

            @Override
            public void onError(@NonNull Exception e) {
                // Payment request failed – allow retrying using the same payment method
                if (weakActivity.get() != null) {
                    displayAlert(weakActivity.get(), "Error", e.toString(), false);
                }
            }
        });
    }
}
