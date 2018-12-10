package com.ghadi.lambda;

import clojure.lang.*;
import clojure.java.api.Clojure;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Function;

// init error path: /runtime/init/error

public class Bootstrap {

    static final String API = System.getenv("AWS_LAMBDA_RUNTIME_API");
    static final URI fetchEndpoint = URI.create(String.format("http://%s/2018-06-01/runtime/invocation/next", API));

    static final IFn REQUIRE = Clojure.var("clojure.core", "require");
    static final IFn RESOLVE_HANDLER;
    static final IFn RESPONSE_BODY;
    static final IFn ERROR_RESPONSE;
    static final String RUNTIME_NS = "com.ghadi.lambda.runtime";

    static final String AWS_RUNTIME_NS = "ghadi.aws.lambda";

    static {
        REQUIRE.invoke(Symbol.intern(RUNTIME_NS));

        RESOLVE_HANDLER = Clojure.var(RUNTIME_NS, "resolve-handler");
        RESPONSE_BODY = Clojure.var(RUNTIME_NS, "response-body");
        ERROR_RESPONSE = Clojure.var(RUNTIME_NS, "error-response");
    }

    public static IFn resolve() {
        return (IFn) RESOLVE_HANDLER.invoke(Symbol.intern(System.getenv("_HANDLER")));
    }

    public static void run() {
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            throwable.printStackTrace(System.out);
            System.out.flush();
        });

        var client = HttpClient.newBuilder().build();
        var handler = resolve();
        var toInput = inputToData();

        while (true) {
            try {
                poll(client, handler, toInput);
            } catch (Exception e) {
                Util.sneakyThrow(e);
            }
        }
    }

    private static Function<HttpResponse<InputStream>, IPersistentMap> inputToData() {
        IPersistentMap base = RT.mapUniqueKeys(Keyword.intern(AWS_RUNTIME_NS, "region"), System.getenv("AWS_REGION"),
                Keyword.intern(AWS_RUNTIME_NS, "handler"), System.getenv("_HANDLER"),
                Keyword.intern(AWS_RUNTIME_NS, "function-name"), System.getenv("AWS_LAMBDA_FUNCTION_NAME"),
                Keyword.intern(AWS_RUNTIME_NS, "memory-size"), System.getenv("AWS_LAMBDA_FUNCTION_MEMORY_SIZE"),
                Keyword.intern(AWS_RUNTIME_NS, "function-version"), System.getenv("AWS_LAMBDA_FUNCTION_VERSION"),
                Keyword.intern(AWS_RUNTIME_NS, "log-group-name"), System.getenv("AWS_LAMBDA_LOG_GROUP_NAME"),
                Keyword.intern(AWS_RUNTIME_NS, "log-stream-name"), System.getenv("AWS_LAMBDA_LOG_STREAM_NAME"),
                Keyword.intern(AWS_RUNTIME_NS, "access-key-id"), System.getenv("AWS_ACCESS_KEY_ID"),
                Keyword.intern(AWS_RUNTIME_NS, "secret-access-key"), System.getenv("AWS_SECRET_ACCESS_KEY"),
                Keyword.intern(AWS_RUNTIME_NS, "session-token"), System.getenv("AWS_SESSION_TOKEN"));

        return (response) -> {
            var headers = response.headers();
            return base.assoc(Keyword.intern(AWS_RUNTIME_NS, "request-id"), headers.firstValue("lambda-runtime-aws-request-id").orElseThrow())
                    .assoc(Keyword.intern(AWS_RUNTIME_NS, "function-arn"), headers.firstValue("lambda-runtime-invoked-function-arn").orElseThrow())
                    .assoc(Keyword.intern(AWS_RUNTIME_NS, "trace-id"), headers.firstValue("lambda-runtime-trace-id").orElseThrow())
                    .assoc(Keyword.intern(AWS_RUNTIME_NS, "deadline-ms"), headers.firstValueAsLong("lambda-runtime-deadline-ms").orElseThrow())
                    .assoc(Keyword.intern(AWS_RUNTIME_NS, "inputstream"), response.body());
        };
    }

    public static void poll(HttpClient client, IFn handler, Function<HttpResponse<InputStream>, IPersistentMap> toData) throws IOException, InterruptedException {
        var httpReq = HttpRequest.newBuilder().GET().uri(fetchEndpoint).build();
        var httpResp = client.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());

        var reqId = httpResp.headers().firstValue("Lambda-Runtime-Aws-Request-Id").orElseThrow();

        try {
            var data = toData.apply(httpResp);
            var response = handler.invoke(data);

            notifySuccess(client, reqId, response);
        } catch (Throwable t) {
            notifyFailure(client, reqId, t);
        }
    }

    public static void notifySuccess(HttpClient client, String reqId, Object response) throws IOException, InterruptedException {
        var endpoint = URI.create(String.format("http://%s/2018-06-01/runtime/invocation/%s/response", API, reqId));
        var req = HttpRequest.newBuilder(endpoint).POST((HttpRequest.BodyPublisher) RESPONSE_BODY.invoke(response)).build();
        client.send(req, HttpResponse.BodyHandlers.discarding());
    }

    public static void notifyFailure(HttpClient client, String reqId, Throwable t) throws IOException, InterruptedException {
        var endpoint = URI.create(String.format("http://%s/2018-06-01/runtime/invocation/%s/error", API, reqId));
        var req = HttpRequest.newBuilder(endpoint).POST((HttpRequest.BodyPublisher) ERROR_RESPONSE.invoke(t)).build();
        client.send(req, HttpResponse.BodyHandlers.discarding());
    }

    public static void main(String[] ignored) {
        Bootstrap.run();
    }

}
