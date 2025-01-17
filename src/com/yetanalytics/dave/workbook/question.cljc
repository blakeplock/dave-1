(ns com.yetanalytics.dave.workbook.question
  "Questions contain a specific question about a dataset, a reference to a
  function to get the answer, any user-configurable constants the function
  requires, and any number of visualizations to represent the result."
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.dave.util.spec :as su]
            [com.yetanalytics.dave.workbook.question.visualization :as v]
            [com.yetanalytics.dave.func :as func]
            [com.yetanalytics.dave.func.ret :as func-ret]
            [com.yetanalytics.dave.workbook.data.state :as data-state]))

(s/def ::id
  uuid?)

(s/def ::text
  su/string-not-empty-spec)

;; Func record that allows stateful reduction
(s/def :com.yetanalytics.dave.workbook.question.function/func
 #(satisfies? func/AFunc %))

(s/def :com.yetanalytics.dave.workbook.question.function/state
  data-state/spec)

;; The dave function that this question uses to get its data
(s/def ::function
  (s/and (s/keys :req-un [::func/id
                          :com.yetanalytics.dave.workbook.question.function/func
                          :com.yetanalytics.dave.workbook.question.function/state]
                 :opt-un [::func/args
                          ::func-ret/result])
         ;; Validate that the args are OK
         (fn valid-args [{:keys [id args]}]
           (nil? (func/explain-args id args)))))

(s/def ::visualizations
  (s/and (s/map-of ::v/id
                   v/visualization-spec)
         (comp su/sequential-indices? vals)))

(s/def ::index
  su/index-spec)

(def question-spec
  (s/keys :req-un [::id
                   ::text
                   ::visualizations
                   ::index]
          :opt-un [::function]))
