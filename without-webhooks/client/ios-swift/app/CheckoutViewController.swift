//
//  CheckoutViewController.swift
//  app
//
//  Created by Yuki Tokuhiro on 9/25/19.
//  Copyright © 2019 stripe-samples. All rights reserved.
//

import UIKit
import Stripe

/**
 * To run this app, you'll need to first run the sample server locally.
 * Follow the "How to run locally" instructions in the root directory's README.md to get started.
 * Once you've started the server, open http://localhost:4242 in your browser to check that the
 * server is running locally.
 * After verifying the sample server is running locally, build and run the app using the iOS simulator.
 */
let BackendUrl = "http://127.0.0.1:4242/"

class CheckoutViewController: UIViewController {

    lazy var cardTextField: STPPaymentCardTextField = {
        let cardTextField = STPPaymentCardTextField()
        return cardTextField
    }()
    lazy var payButton: UIButton = {
        let button = UIButton(type: .custom)
        button.layer.cornerRadius = 5
        button.backgroundColor = .systemBlue
        button.titleLabel?.font = UIFont.systemFont(ofSize: 22)
        button.setTitle("Pay", for: .normal)
        button.addTarget(self, action: #selector(pay), for: .touchUpInside)
        return button
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .white
        let stackView = UIStackView(arrangedSubviews: [cardTextField, payButton])
        stackView.axis = .vertical
        stackView.spacing = 20
        stackView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stackView)
        NSLayoutConstraint.activate([
            stackView.leftAnchor.constraint(equalToSystemSpacingAfter: view.leftAnchor, multiplier: 2),
            view.rightAnchor.constraint(equalToSystemSpacingAfter: stackView.rightAnchor, multiplier: 2),
            stackView.topAnchor.constraint(equalToSystemSpacingBelow: view.topAnchor, multiplier: 2),
        ])
        loadPage()
    }

    func displayAlert(title: String, message: String, restartDemo: Bool = false) {
        DispatchQueue.main.async {
            let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
            if restartDemo {
                alert.addAction(UIAlertAction(title: "Restart demo", style: .cancel) { _ in
                    self.cardTextField.clear()
                    self.loadPage()
                })
            }
            else {
                alert.addAction(UIAlertAction(title: "OK", style: .cancel))
            }
            self.present(alert, animated: true, completion: nil)
        }
    }

    func loadPage() {
        // Load Stripe key from the server
        let url = URL(string: BackendUrl + "stripe-key")!
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let task = URLSession.shared.dataTask(with: request, completionHandler: { [weak self] (data, response, error) in
            guard let response = response as? HTTPURLResponse,
                response.statusCode == 200,
                let data = data,
                let json = try? JSONSerialization.jsonObject(with: data, options: []) as? [String : Any],
                let stripePublicKey = json["publicKey"] as? String else {
                    let message = error?.localizedDescription ?? "Failed to decode response from server."
                    self?.displayAlert(title: "Error loading page", message: message)
                    return
            }
            print("Loaded Stripe key")
            Stripe.setDefaultPublishableKey(stripePublicKey)
        })
        task.resume()
    }

    @objc
    func pay() {
        // Collect card details on the client
        let cardParams = cardTextField.cardParams
        let paymentMethodParams = STPPaymentMethodParams(card: cardParams, billingDetails: nil, metadata: nil)
        STPAPIClient.shared().createPaymentMethod(with: paymentMethodParams) { [weak self] paymentMethod, error in
            // Create PaymentMethod failed
            if let createError = error {
                self?.displayAlert(title: "Payment failed", message: createError.localizedDescription)
            }
            if let paymentMethodId = paymentMethod?.stripeId {
                print("Created PaymentMethod")
                self?.confirm(paymentMethodId: paymentMethodId)
            }
        }
    }

    // Confirm a payment method or payment intent
    func confirm(paymentMethodId: String? = nil, paymentIntentId: String? = nil) {
        // Create a PaymentIntent on the server
        let url = URL(string: BackendUrl + "pay")!
        var json: [String: Any] = [:]
        if let paymentMethodId = paymentMethodId {
            json = [
                "paymentMethodId": paymentMethodId,
                "currency": "usd",
                "items": [
                    "id": "photo_subscription"
                ]
            ]
        }
        else if let paymentIntentId = paymentIntentId {
            json = [
                "paymentIntentId": paymentIntentId,
            ]
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: json)
        let task = URLSession.shared.dataTask(with: request, completionHandler: { [weak self] (data, response, requestError) in
            guard let response = response as? HTTPURLResponse,
                response.statusCode == 200,
                let data = data,
                let json = try? JSONSerialization.jsonObject(with: data, options: []) as? [String : Any] else {
                    self?.displayAlert(title: "Payment failed", message: requestError?.localizedDescription ?? "")
                    return
            }
            let payError = json["error"] as? String
            let clientSecret = json["clientSecret"] as? String
            let requiresAction = json["requiresAction"] as? Bool

            // Payment failed
            if let payError = payError {
                self?.displayAlert(title: "Payment failed", message: payError)
            }
            // Payment succeeded, no additional action required
            else if clientSecret != nil && (requiresAction == nil || requiresAction == false) {
                STPAPIClient.shared().retrievePaymentIntent(withClientSecret: clientSecret!) { paymentIntent, retrieveError in
                    if let paymentIntent = paymentIntent {
                        self?.displayAlert(title: "Payment succeeded", message: paymentIntent.description, restartDemo: true)
                    }
                    else {
                        let message = retrieveError?.localizedDescription ?? ""
                        self?.displayAlert(title: "Payment failed", message: message)
                    }
                }
            }
            // Payment requires additional action
            else if clientSecret != nil && requiresAction == true && self != nil {
                let paymentHandler = STPPaymentHandler.shared()
                paymentHandler.handleNextAction(forPayment: clientSecret!, authenticationContext: self!, returnURL: nil) { status, paymentIntent, handleActionError in
                    switch (status) {
                    case .failed:
                        let message = handleActionError?.localizedDescription ?? ""
                        self?.displayAlert(title: "Payment failed", message: message)
                        break
                    case .canceled:
                        let message = handleActionError?.localizedDescription ?? ""
                        self?.displayAlert(title: "Payment canceled", message: message)
                        break
                    case .succeeded:
                        // After handling a required action on the client, the status of the PaymentIntent is
                        // requires_confirmation. You must send the PaymentIntent ID to your backend
                        // and confirm it to finalize the payment. This step enables your integration to
                        // synchronously fulfill the order on your backend and return the fulfillment result
                        // to your client.
                        if let paymentIntent = paymentIntent, paymentIntent.status == STPPaymentIntentStatus.requiresConfirmation {
                            print("Re-confirming PaymentIntent after handling action")
                            self?.confirm(paymentIntentId: paymentIntent.stripeId)
                        }
                        else {
                            let message = paymentIntent?.description ?? ""
                            self?.displayAlert(title: "Payment succeeded", message: message, restartDemo: true)
                        }
                        break
                    @unknown default:
                        fatalError()
                        break
                    }
                }
            }
        })
        task.resume()
    }
}

extension CheckoutViewController: STPAuthenticationContext {
    func authenticationPresentingViewController() -> UIViewController {
        return self
    }
}

