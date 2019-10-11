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

@interface CheckoutViewController ()

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

    [self startCheckout];
}

- (void)displayAlertWithTitle:(NSString *)title message:(NSString *)message restartDemo:(BOOL)restartDemo {
    dispatch_async(dispatch_get_main_queue(), ^{
        UIAlertController *alert = [UIAlertController alertControllerWithTitle:title message:message preferredStyle:UIAlertControllerStyleAlert];
        if (restartDemo) {
            [alert addAction:[UIAlertAction actionWithTitle:@"Restart demo" style:UIAlertActionStyleCancel handler:^(UIAlertAction *action) {
                [self.cardTextField clear];
                [self startCheckout];
            }]];
        }
        else {
            [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleCancel handler:nil]];
        }
        [self presentViewController:alert animated:YES completion:nil];
    });
}

- (void)startCheckout {
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

        NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
        if (error != nil || httpResponse.statusCode != 200 || json[@"publishableKey"] == nil) {
            [self displayAlertWithTitle:@"Error loading page" message:error.localizedDescription ?: @"" restartDemo:NO];
        }
        else {
            NSLog(@"Created PaymentIntent");
            self.paymentIntentClientSecret = json[@"clientSecret"];
            NSString *publishableKey = json[@"publishableKey"];
            // Configure the SDK with your Stripe publishable key so that it can make requests to the Stripe API
            [Stripe setDefaultPublishableKey:publishableKey];
        }
    }];
    [task resume];
}

- (void)pay {
}

@end

// docs_window_spec {"setup-ui-objc": [[21, 25], [26, 57], [59, 60], ["// ..."], [148, 150]]}
