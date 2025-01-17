(ns com.yetanalytics.dave.ui.views.workbook.question.visualization
  (:require [re-frame.core :refer [dispatch subscribe]]
            [com.yetanalytics.dave.ui.views.vega :as v :refer [vega]]
            [reagent.core :as r]
            [com.yetanalytics.dave.ui.views.download :as download]))

(defn display
  [workbook-id
   question-id
   visualization-id
   & {:keys [vega-override]}]
  (when-let [vega-spec @(subscribe [:workbook.question.visualization/vega-spec
                                    workbook-id question-id visualization-id])]
    [vega (merge vega-spec
                 vega-override)
     ;; :signals-in {"bar_color" [:debug/bar-color]} ;; signals in from subs
     ;; signals out to handlers
     #_:signals-out #_{"tooltip" [:debug/log "tooltip state:"]
                       "bar_color" [:debug/log "bar color out:"]}
     ;; dom + vega events out to handlers
     #_:events-out #_{"click" [:debug/log "click event:"]}
     ;; Other options:
     ;; :renderer "canvas" ;; use canvas rather than SVG
     ;; :hover? false ;; don't initialize hovering
     ;; :log-level :debug ;; set log level (default is :warn)

     ]))

(defn edit-button
  [workbook-id question-id visualization-id]
  [:button.minorbutton
   {:on-click #(dispatch
                [:workbook.question.visualization/edit
                 workbook-id question-id visualization-id])}
   "Edit"])

(defn delete-button
  [workbook-id question-id visualization-id]
  [:button.minorbutton
   {:on-click #(dispatch
                [:crud/delete-confirm workbook-id question-id visualization-id])}
   "Delete"])

(defn page []
  (let [{:keys [id
                title]
         :as visualization} @(subscribe [:nav/focus])
        [_ workbook-id _ question-id] @(subscribe [:nav/path])]
    [:div.page.visualization
     [:div ;; inner
      [:div.splash
       [:h2 title]
       #_[:button.majorbutton
        {:on-click #(dispatch [:workbook.question.visualization/offer-picker
                               workbook-id question-id id])}
        "Change Visualization"]
       [edit-button workbook-id question-id id]
       [delete-button workbook-id question-id id]
       [display workbook-id question-id id]
       [download/download-text
        "Download Vega JSON..."
        @(subscribe [:workbook.question.visualization/vega-spec-json-pp
                     workbook-id question-id id])]]]]))


(defn cell [workbook-id
            question-id
            {:keys [id
                    title] :as visualization}]
  [:div.boxselection
   {:on-click #(dispatch [:nav/nav-path!
                          [:workbooks
                           workbook-id
                           :questions
                           question-id
                           :visualizations
                           id]])}
   [:div.cardtitle
    "Visualization"]
   [:h4
    [:a
     {:href (str "#/workbooks/" workbook-id
                 "/questions/" question-id
                 "/visualizations/" id)}
     title]]
   [display workbook-id question-id id]])

(defn grid-list
  "A list of Visualizations"
  [workbook-id question-id visualizations]
  [:div.visualization.list
   (into [:div] ;; inner
         (for [[id visualization] visualizations
               :let [k (str "visualization-list-cell-" id)]]
           ^{:key k}
           [cell workbook-id question-id visualization]))])
