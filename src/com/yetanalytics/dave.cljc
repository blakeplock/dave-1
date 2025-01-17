(ns com.yetanalytics.dave
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.dave.workbook :as workbook]
            [com.yetanalytics.dave.func :as func]))

;; DAVE Object Specs
(s/def ::workbook
  workbook/workbook-spec)

(s/def ::function
  func/func-spec)
