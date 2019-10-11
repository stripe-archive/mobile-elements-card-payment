<?php
use Slim\Http\Request;
use Slim\Http\Response;
use Stripe\Stripe;

require 'vendor/autoload.php';

$ENV_PATH = '../../..';

$dotenv = Dotenv\Dotenv::create(realpath($ENV_PATH));
$dotenv->load();

require './config.php';

if (PHP_SAPI == 'cli-server') {
  $_SERVER['SCRIPT_NAME'] = '/index.php';
}

$app = new \Slim\App;

// Instantiate the logger as a dependency
$container = $app->getContainer();
$container['logger'] = function ($c) {
  $settings = $c->get('settings')['logger'];
  $logger = new Monolog\Logger($settings['name']);
  $logger->pushProcessor(new Monolog\Processor\UidProcessor());
  $logger->pushHandler(new Monolog\Handler\StreamHandler(__DIR__ . '/logs/app.log', \Monolog\Logger::DEBUG));
  return $logger;
};

$app->add(function ($request, $response, $next) {
    Stripe::setApiKey(getenv('STRIPE_SECRET_KEY'));
    return $next($request, $response);
});

$app->get('/', function (Request $request, Response $response, array $args) {   
  // Display checkout page
  return $response->write(file_get_contents(getenv('STATIC_DIR') . '/index.html'));
});

function calculateOrderAmount($items)
{
  // Replace this constant with a calculation of the order's amount
  // Calculate the order total on the server to prevent
  // people from directly manipulating the amount on the client
  return 1400;
}

$app->post('/create-payment-intent', function (Request $request, Response $response, array $args) {
    $pub_key = getenv('STRIPE_PUBLISHABLE_KEY');
    $body = json_decode($request->getBody());

    // Create a PaymentIntent with the order amount and currency
    $payment_intent = \Stripe\PaymentIntent::create([
      "amount" => calculateOrderAmount($body->items),
      "currency" => $body->currency
    ]);
    
    // Send public key and PaymentIntent details to client
    return $response->withJson(array('publishableKey' => $pub_key, 'clientSecret' => $payment_intent->client_secret));
});

$app->run();
