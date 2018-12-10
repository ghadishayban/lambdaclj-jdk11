(ns com.ghadi.lambda.runtime
  (:import java.net.http.HttpRequest$BodyPublishers))

(defprotocol ToBody
  (body [_]))

(extend-protocol ToBody
  nil
  (body [_] (HttpRequest$BodyPublishers/noBody))

  String
  (body [s] (HttpRequest$BodyPublishers/ofString s))

  java.io.InputStream
  (body [is]
    (HttpRequest$BodyPublishers/ofInputStream
     (reify java.util.function.Supplier (get [_] is)))))

(extend (Class/forName "[B")
  ToBody
  {:body #(HttpRequest$BodyPublishers/ofByteArray %)})

;;
;; runtime API
;; do not rename
;;

(defn resolve-handler
  [sym]
  (or (requiring-resolve sym)
      (throw (ex-info "Could not find handler" {:sym sym}))))

(defn response-body
  [output]
  (body output))

(defn error-response
  [throwable]
  (-> throwable Throwable->map pr-str body))

;;
;; /runtime
;;
