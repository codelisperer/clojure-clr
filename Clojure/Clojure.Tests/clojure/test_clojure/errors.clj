﻿;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;; Tests for error handling and messages

(ns clojure.test-clojure.errors
  (:use clojure.test clojure.test-helper)               ;;; DM: Added clojure.test-helper -- not sure why this being missing wasn't causing an error (did so on individual file load)
  (:import clojure.lang.ArityException))

(defn f0 [] 0)

(defn f1 [a] a)

;; Function name that includes many special characters to test demunge
(defn f2:+><->!#%&*b [x] x)                            ;;; f2:+><->!#%&*|b  -- removed | because we use for escaping

(defmacro m0 [] `(identity 0))

(defmacro m1 [a] `(inc ~a))

(defmacro m2 [] (assoc))

(deftest arity-exception
  ;; IllegalArgumentException is pre-1.3
  (is (thrown-with-msg? ArgumentException #"Wrong number of args \(1\) passed to"  ;;; IllegalArgumentException
        (f0 1)))
  (is (thrown-with-msg? ArityException #"Wrong number of args \(0\) passed to"
        (f1)))
  (is (thrown-with-msg? ArityException #"Wrong number of args \(1\) passed to"
        (macroexpand `(m0 1))))
  (is (thrown-with-msg? ArityException #"Wrong number of args \(2\) passed to"
        (macroexpand `(m1 1 2))))
  (is (thrown-with-msg? ArityException  (System.Text.RegularExpressions.Regex. (System.Text.RegularExpressions.Regex/Escape "f2:+><->!#%&*b"))       ;;;  We don't have \Q... \E :  #"\Q/f2:+><->!#%&*|b\E"
        (f2:+><->!#%&*b 1 2))                                                                                                                        ;;; f2:+><->!#%&*|b  
      "ArityException messages should demunge function names")
  (is (try
        (macroexpand `(m2))
        (throw (Exception. "fail"))           ;;; RuntimeException.
        (catch ArityException e
          (is (= 0 (.-Actual e))))))
 (is (try
       (macroexpand `(m2 5))
       (throw (Exception. "fail"))            ;;; RuntimeException.
       (catch ArityException e
         (is (= 1 (.-Actual e)))))))

(deftest compile-error-examples
  (are [form errtype re] (thrown-with-cause-msg? errtype re (eval form))
       '(Int32/Parse) Exception #"No .*taking 0 args"                                                ;;; Long/parseLong  #"No method.*taking 0 args"
       '(Int32/Parse :a :b :c :d) Exception #"No matching member.*taking 4 args")                    ;;; (Long/parseLong :a :b :c)  #"No matching method.*taking 3 args" 
  (are [form errtype re] (thrown-with-msg? errtype re (eval form))
       '(.jump "foo" 1) Exception #"No matching member.*taking 1 arg"))                              ;;; #"No matching method.*taking 1 arg"

(deftest assert-arg-messages
  ; used to ensure that error messages properly use local names for macros
  (refer 'clojure.core :rename '{with-open renamed-with-open})
  
  ; would have used `are` here, but :line meta on &form doesn't survive successive macroexpansions
 (doseq [[msg-regex-str form] [["renamed-with-open" "(renamed-with-open [a])"]]]
    (is (thrown-with-cause-msg? clojure.lang.Compiler+CompilerException                              ;;; clojure.lang.Compiler$CompilerException
                                (re-pattern (format msg-regex-str *ns*))
                                (macroexpand (read-string form))))))

(deftest extract-ex-data
  (try
   (throw (ex-info "example error" {:foo 1}))
   (catch Exception t                                                  ;;; Throwable
     (is (= {:foo 1} (ex-data t)))))
  (is (nil? (ex-data (Exception. "example non ex-data")))))            ;;; RuntimeException

(deftest Throwable->map-test
  (testing "base functionality"
    (let [{:keys [cause via trace]} (Throwable->map
                                     (Exception. "I am a string literal"))]
      (is (= cause "I am a string literal"))
      (is (= 1 (count via)))
      (is (vector? via))
      (is (= ["I am a string literal"] (map :message via)))))
  (testing "causes"
    (let [{:keys [cause via trace]} (Throwable->map
                                     (Exception. "I am not a number"
                                                 (Exception. "double two")))]
      (is (= cause "double two"))
      (is (= ["I am not a number" "double two"]
             (map :message via)))))
  (testing "ex-data"
    (let [{[{:keys [data]}] :via
           data-top-level :data}
          (Throwable->map (ex-info "ex-info"
                                   {:some "data"}))]
      (is (= data data-top-level {:some "data"}))))
  (testing "nil stack handled"
    (let [t (Exception. "abc")]                                       ;;; Throwable.
      ;; simulate what can happen when Java omits stack traces
                                                                      ;;;(.setStackTrace t (into-array StackTraceElement []))  -- no equivalent, but an unthrown exception has a null stacktrace
      (let [{:keys [cause via trace]} (Throwable->map t)]
        (is (= cause "abc"))
        (is (= trace []))

        ;; fail if printing throws an exception
        (try
          (with-out-str (pr t))
          (catch Exception t (is nil)))))))                          ;;; Throwable

(deftest ex-info-allows-nil-data
  (is (= {} (ex-data (ex-info "message" nil))))
  (is (= {} (ex-data (ex-info "message" nil (Exception. "cause"))))))     ;;; Throwable.

(deftest ex-info-arities-construct-equivalent-exceptions
  (let [ex1 (ex-info "message" {:foo "bar"})
        ex2 (ex-info "message" {:foo "bar"} nil)]
    (is (= (.Message ex1) (.Message ex2)))                                                ;;; .getMessage  .getMessage
    (is (= (.getData ex1) (.getData ex2)))
    (is (= (.InnerException ex1) (.InnerException ex2)))))                                ;;; .getCause   .getCause 