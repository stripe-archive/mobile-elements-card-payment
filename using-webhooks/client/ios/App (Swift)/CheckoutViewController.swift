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
    var paymentIntentClientSecret: String?

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
        startCheckout()
    }

    func displayAlert(title: String, message: String, restartDemo: Bool = false) {
        DispatchQueue.main.async {
            let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
            if restartDemo {
                alert.addAction(UIAlertAction(title: "Restart demo", style: .cancel) { _ in
                    self.cardTextField.clear()
                    self.startCheckout()
                })
            }
            else {
                alert.addAction(UIAlertAction(title: "OK", style: .cancel))
            }
            self.present(alert, animated: true, completion: nil)
        }
    }

    func startCheckout() {
        // Create a PaymentIntent by calling the sample server's /create-payment-intent endpoint.
        let url = URL(string: BackendUrl + "create-payment-intent")!
        let json: [String: Any] = [
            "currency": "usd",
            "items": [
                "id": "photo_subscription"
            ]
        ]
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: json)
        let task = URLSession.shared.dataTask(with: request, completionHandler: { [weak self] (data, response, error) in
            guard let response = response as? HTTPURLResponse,
                response.statusCode == 200,
                let data = data,
                let json = try? JSONSerialization.jsonObject(with: data, options: []) as? [String : Any],
                let clientSecret = json["clientSecret"] as? String,
                let publishableKey = json["publishableKey"] as? String else {
                    let message = error?.localizedDescription ?? "Failed to decode response from server."
                    self?.displayAlert(title: "Error loading page", message: message)
                    return
            }
            print("Created PaymentIntent")
            self?.paymentIntentClientSecret = clientSecret
            // Configure the SDK with your Stripe publishable key so that it can make requests to the Stripe API
            Stripe.setDefaultPublishableKey(publishableKey)
        })
        task.resume()
    }

    @objc
    func pay() {
    }
}

// docs_window_spec {"setup-ui-swift": [[21, 51], [52, 53], ["// ..."], [131, 132]]}
