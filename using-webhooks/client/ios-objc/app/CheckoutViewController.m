//
//  CheckoutViewController.m
//  app
//
//  Created by Ben Guo on 9/29/19.
//  Copyright © 2019 stripe-samples. All rights reserved.
//

#import "CheckoutViewController.h"
#import <Stripe/Stripe.h>

/**
* To run this app, you'll need to first run the sample server locally.
* Follow the "How to run locally" instructions in the root directory's README.md to get started.
* Once you've started the server, open http://localhost:4242 in your browser to check that the
* server is running locally.
* After verifying the sample server is running locally, build and run the app using the iOS simulator.
*/
NSString *const BackendUrl = @"http://127.0.0.1:4242/";

@interface CheckoutViewController ()  <STPAuthenticationContext>

@property (weak) STPPaymentCardTextField *cardTextField;
@property (weak) UIButton *payButton;
@property (strong) NSString *paymentIntentClientSecret;

@end

@implementation CheckoutViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor whiteColor];

    STPPaymentCardTextField *cardTextField = [[STPPaymentCardTextField alloc] init];
    self.cardTextField = cardTextField;

    UIButton *button = [UIButton buttonWithType:UIButtonTypeCustom];
    button.layer.cornerRadius = 5;
    button.backgroundColor = [UIColor systemBlueColor];
    button.titleLabel.font = [UIFont systemFontOfSize:22];
    [button setTitle:@"Pay" forState:UIControlStateNormal];
    [button addTarget:self action:@selector(pay) forControlEvents:UIControlEventTouchUpInside];
    self.payButton = button;

    UIStackView *stackView = [[UIStackView alloc] initWithArrangedSubviews:@[cardTextField, button]];
    stackView.axis = UILayoutConstraintAxisVertical;
    stackView.translatesAutoresizingMaskIntoConstraints = NO;
    stackView.spacing = 20;
    [self.view addSubview:stackView];

    [NSLayoutConstraint activateConstraints:@[
        [stackView.leftAnchor constraintEqualToSystemSpacingAfterAnchor:self.view.leftAnchor multiplier:2],
        [self.view.rightAnchor constraintEqualToSystemSpacingAfterAnchor:stackView.rightAnchor multiplier:2],
        [stackView.topAnchor constraintEqualToSystemSpacingBelowAnchor:self.view.topAnchor multiplier:2],
    ]];

    [self loadPage];
}

- (void)loadPage {
    // Create a PaymentIntent by calling the sample server's /create-payment-intent endpoint.
    NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"%@create-payment-intent", BackendUrl]];
    NSDictionary *json = @{
        @"currency": @"usd",
        @"items": @[
                @{@"id": @"photo_subscription"}
        ]
    };
    NSData *body = [NSJSONSerialization dataWithJSONObject:json options:0 error:nil];
    NSMutableURLRequest *request = [[NSURLRequest requestWithURL:url] mutableCopy];
    [request setHTTPMethod:@"POST"];
    [request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
    [request setHTTPBody:body];
    NSURLSessionTask *task = [[NSURLSession sharedSession] dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *requestError) {
        NSError *error = requestError;
        NSDictionary *json = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];
        NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
        if (error != nil || httpResponse.statusCode != 200 || json[@"publicKey"] == nil) {
            dispatch_async(dispatch_get_main_queue(), ^{
                NSString *message = error.localizedDescription ?: @"";
                UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"Error loading page" message:message preferredStyle:UIAlertControllerStyleAlert];
                [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleCancel handler:nil]];
                [self presentViewController:alert animated:YES completion:nil];
            });
        }
        else {
            NSLog(@"Created PaymentIntent");
            self.paymentIntentClientSecret = json[@"clientSecret"];
            NSString *stripePublicKey = json[@"publicKey"];
            [Stripe setDefaultPublishableKey:stripePublicKey];
        }
    }];
    [task resume];
}

- (void)pay {
    if (!self.paymentIntentClientSecret) {
        NSLog(@"PaymentIntent hasn't been created");
        return;
    }

    // Collect card details
    STPPaymentMethodCardParams *cardParams = self.cardTextField.cardParams;
    STPPaymentMethodParams *paymentMethodParams = [STPPaymentMethodParams paramsWithCard:cardParams billingDetails:nil metadata:nil];
    STPPaymentIntentParams *paymentIntentParams = [[STPPaymentIntentParams alloc] initWithClientSecret:self.paymentIntentClientSecret];
    paymentIntentParams.paymentMethodParams = paymentMethodParams;

    // Submit the payment
    STPPaymentHandler *paymentHandler = [STPPaymentHandler sharedHandler];
    [paymentHandler confirmPayment:paymentIntentParams withAuthenticationContext:self completion:^(STPPaymentHandlerActionStatus status, STPPaymentIntent *paymentIntent, NSError *error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            switch (status) {
                case STPPaymentHandlerActionStatusFailed: {
                    NSString *message = error.localizedDescription ?: @"";
                    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"Payment failed" message:message preferredStyle:UIAlertControllerStyleAlert];
                    [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleCancel handler:nil]];
                    [self presentViewController:alert animated:YES completion:nil];
                    break;
                }
                case STPPaymentHandlerActionStatusCanceled: {
                    NSString *message = error.localizedDescription ?: @"";
                    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"Payment canceled" message:message preferredStyle:UIAlertControllerStyleAlert];
                    [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleCancel handler:nil]];
                    [self presentViewController:alert animated:YES completion:nil];
                    break;
                }
                case STPPaymentHandlerActionStatusSucceeded: {
                    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"Payment succeeded" message:paymentIntent.description preferredStyle:UIAlertControllerStyleAlert];
                    [alert addAction:[UIAlertAction actionWithTitle:@"Restart demo" style:UIAlertActionStyleCancel handler:^(UIAlertAction *action) {
                        [self.cardTextField clear];
                        [self loadPage];
                    }]];
                    [self presentViewController:alert animated:YES completion:nil];
                    break;
                }
                default:
                    break;
            }
        });
    }];
}

# pragma mark STPAuthenticationContext
- (UIViewController *)authenticationPresentingViewController {
    return self;
}

@end
