## JDK11 + Clojure Custom Runtime for AWS Lambda

This code is a small shim that interacts with the [Lambda Custom Runtime API](https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html)
and handles Lambda invocations by calling Clojure vars.

### mechanism

`jlink` a jvm with all the modules (java.se), compile a small bootstrap script (runtime/com/ghadi/lambda/Bootstrap.java) that calls the runtime API to fetch and handle invokes.

The layer including JVM + runtime is 25MB, no AWS library dependencies. The clojure var is passed an ordinary PHM:

```clj
{:ghadi.aws.lambda/function-version "$LATEST",
 :ghadi.aws.lambda/secret-access-key "REDACTED",
 :ghadi.aws.lambda/log-stream-name "2018/12/10/[$LATEST]9ff0d04d871d4a9baef8e94c03a28406",
 :ghadi.aws.lambda/request-id "8004a0a6-fc9a-11e8-b9a6-0d295168b072",
 :ghadi.aws.lambda/session-token "REDACTED",
 :ghadi.aws.lambda/memory-size "256",
 :ghadi.aws.lambda/region "us-east-1",
 :ghadi.aws.lambda/log-group-name "/aws/lambda/ghadi-lambda-test-GhadiTestLambda-1GF8MAVS3KBEM",
 :ghadi.aws.lambda/deadline-ms 1544460130846,
 :ghadi.aws.lambda/trace-id "Root=1-5c0e9751-cc92b8bc8244ce0cdf2ed210;Parent=49d3be7b2da38e0b;Sampled=0",
 :ghadi.aws.lambda/handler "ghadi.sample/lambda",
 :ghadi.aws.lambda/function-arn "arn:aws:lambda:us-east-1:00000000000:function:ghadi-lambda-test-GhadiTestLambda-1GF8MAVS3KBEM",
 :ghadi.aws.lambda/access-key-id "REDACTED",
 :ghadi.aws.lambda/inputstream #object[jdk.internal.net.http.ResponseSubscribers$HttpResponseInputStream 0x31d0e481 "jdk.internal.net.http.ResponseSubscribers$HttpResponseInputStream@31d0e481"],
 :ghadi.aws.lambda/function-name "ghadi-lambda-test-GhadiTestLambda-1GF8MAVS3KBEMN"}
```

There is a small clojure ns that coerces responses back to AWS (com.ghadi.lambda.runtime)


### howto

Call `make layer0.zip` and create a Lambda Layer based on that. Use that as the base layer in your lambda and set your handler to be the fqsym of the target var.
Deploy your code as an uberjar in the Lambda code (not the top layer). AOT doesn't matter.

### Consider

tighter integration with t.d.a.