# Collecting a card payment on mobile (iOS & Android)
Charging a card consists of three steps:

**🕵️ Authentication -** Card information is sent to the card issuer for verification. Some cards may require the cardholder to strongly authenticate the purchase through protocols like [3D Secure](https://stripe.com/ie/guides/3d-secure-2).

**💁 Authorization -** Funds from the customer's account are put on hold but not transferred to the merchant.

**💸 Capture -** Funds are transferred to the merchant's account and the payment is complete.

The [Payment Intents API](https://stripe.com/docs/api/payment_intents) abstracts away these three stages by handling all steps of the process through the [confirm method](https://stripe.com/docs/api/payment_intents/confirm). After you create a PaymentIntent, call confirm to authenticate, authorize, and capture the funds in one API call.

There are two implementations depending on whether you want to use webhooks for any post-payment process:

* **[/using-webhooks](/using-webhooks)** Confirms the payment on the client and requires using webhooks or other async event handlers for any post-payment logic (e.g. sending email receipts, fulfilling orders).
* **[/without-webhooks](/without-webhooks)** Confirms the payment on the server and allows you to run any post-payment logic right after.

This sample shows:
<!-- prettier-ignore -->
|     | Using webhooks | Without webhooks
:--- | :---: | :---:
💳 **Collecting card and cardholder details.** Both integrations use the card Element from our mobile SDKs to build a custom checkout form. | ✅  | ✅ |
🙅 **Handling card authentication requests and declines.** Attempts to charge a card can fail if the bank declines the purchase or requests additional authentication.  | ✅  | ✅ |
↪️ **Using webhooks to respond to a hold being placed on the card.** Confirming the payment on the client requires using webhooks for any follow up actions, like emailing a receipt. | ✅ | ❌ |
🏦 **Easily scalable to other payment methods.** Webhooks enable easy adoption of other asynchroneous payment methods like direct debits and push-based payment flows. | ✅ | ❌ |

## How to run
This sample includes 5 server implementations in Node, Ruby, Python, Java, and PHP for the two integration types: [using-webhooks](/using-webhooks) and [without-webhooks](/without-webhooks). For each integration type, there are 4 mobile client implementations, in Swift (iOS), Objective-C (iOS), Kotlin (Android), and Java (Android).

**1. Clone the repository:**

```
git clone https://github.com/stripe-samples/mobile-saving-card-without-payment
```

**2. Copy the .env.example to a .env file:**

```
cp .env.example .env
```

You will need a Stripe account with its own set of [API keys](https://stripe.com/docs/development#api-keys). Enter your Stripe secret API key in the .env file.

```
STRIPE_PUBLIC_KEY=<replace-with-your-publishable-key>
STRIPE_SECRET_KEY=<replace-with-your-secret-key>
```

**3. Follow the server instructions on how to run:**

Pick the [server language](server) you want and navigate to its directory (e.g. `cd using-webhooks/server/node`).  Follow the instructions in the server folder README to run the server locally.

**4. [Optional] Run a webhook locally:**

If you're using the `using-webhooks` integration and want to test it with a local webhook on your machine, you can use the Stripe CLI to easily spin one up.

First [install the CLI](https://stripe.com/docs/stripe-cli) and [link your Stripe account](https://stripe.com/docs/stripe-cli#link-account).

```
stripe listen --forward-to localhost:4242/webhook
```

The CLI will print a webhook secret key to the console. Set `STRIPE_WEBHOOK_SECRET` to this value in your .env file.

You should see events logged in the console where the CLI is running.

When you are ready to create a live webhook endpoint, follow our guide in the docs on [configuring a webhook endpoint in the dashboard](https://stripe.com/docs/webhooks/setup#configure-webhook-settings). 

**5. Set up the client app:**

Next, choose a client implementation and follow the instruction in the app's README (e.g. `using-webhooks/client/ios/README.md`) to run.

When the app is running, use `4242424242424242` as a test card number with any CVC code + a future expiration date.

Use the `4000000000003220` test card number to trigger a 3D Secure challenge flow.

Read more about testing on Stripe at https://stripe.com/docs/testing.

## FAQ
Q: Why did you pick these frameworks?

A: We chose the most minimal framework to convey the key Stripe calls and concepts you need to understand. These demos are meant as an educational tool that helps you roadmap how to integrate Stripe within your own system independent of the framework.

Q: Can you show me how to build X?

A: We are always looking for new sample ideas, please email dev-samples@stripe.com with your suggestion!

## Author(s)
- [@yuki-stripe](https://github.com/yuki-stripe)
- [@bg-stripe](https://github.com/bg-stripe)
