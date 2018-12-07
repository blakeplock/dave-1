(ns com.yetanalytics.dave.ui.views.nav
  "Header, footer and breadcrumb nav components"
  (:require [re-frame.core :refer [dispatch subscribe]]
            [clojure.string :as cs]
            [goog.string :refer [format]]
            [goog.string.format]
            ;; TODO: remove
            [cljs.pprint :refer [pprint]]
            ))

(defn app-description
  "Small description of the app that appears on every page and allows the user
   to launch the new work wizard."
  []
  [:div.app-description
   [:h2.title
    "Data Analytics and Visualization Efficiency Framework for xAPI and the Total Learning Architecture"]
   [:p.description
    "If the objective is to analyze, interpret, and visualize micro-level behavior-driven learning, we need a framework for analysis and visualization which aligns with xAPI, xAPI Profiles, and the Total Learning Architecture (TLA)."]
   [:button "Create Your Own Report"]])

(defn top-image
  "Main group image that is on the home page"
  []
  [:img {:src "/img/dev/top_image.png"}]) ;; FIXME: No image will show in DAVE

(defn crumb
  "A single breadcrumb box"
  [{:keys [title text active?
           href]}]
  [:div
   {:class (when active? "active")}
   [:a {:href (when href
                href)}

    [:div.title title]
    [:div.text text]]])

(defn breadcrumbs
  "Based on context/path, display the DAVE breadcrumb nav to the user."
  []
  (let [context @(subscribe [:nav/context])
        [?workbook
         ?question
         ?visualization] @(subscribe [:nav/path-items])]
    [:div.breadcrumbs
     [:div ;; inner
      [crumb
       {:title "DAVE"
        :text "DAVE provides a framework for increasing the efficiency of implementing learning analytics and creating data visualizations."
        :active? (= :root context)
        :href "#/"}]
      [crumb
       {:title (if ?workbook
                 (format "Workbook: %s" (:title ?workbook))
                 "Workbooks")
        :text (if ?workbook
                (:description ?workbook)
                "Workbooks wrap your functions and visualizations into an easily accessible space. ")
        :active? (= :workbook context)
        :href (when ?workbook
                (format "#/workbooks/%s" (:id ?workbook)))}
       ]
      ;; TODO: figure out contextual behaviour for questions/vis
      [crumb
       {:title (if ?question
                 (format "Question %d" (inc (:index ?question)))
                 "Questions")
        :text (if ?question
                (:text ?question)
                "Questions feature learning-domain problems. ")
        :active? (= :question context)
        :href (when ?question
                ;; We know that if a question is there, a workbook is too
                (format "#/workbooks/%s/questions/%s"
                        (:id ?workbook)
                        (:id ?question)))}]
      [crumb
       {:title (if ?visualization
                 (format "Visualization %d" (inc (:index ?visualization)))
                 "Visualizations")
        ;; TODO: something
        :text "Visualizations make insights accessible to a wide audience."
        :active? (= :visualization context)
        :href (when ?visualization
                (format "#/workbooks/%s/questions/%s/visualizations/%s"
                        (:id ?workbook)
                        (:id ?question)
                        (:id ?visualization)))}]]]))


(defn top-bar-links
  "The links in the top app bar"
  []
  (into [:ul.top-bar-links]
        (for [[title href] [
                            ["Menu" "#/"]
                            ["More Info" "#/"]
                            ["Contribute" "#/"]
                            ["Google Group" "#/"]]]
          [:li [:a {:href href}
                title]])))

(defn top-bar
  "The top bar of the application"
  []
  [:header.top-bar
   [:div ;row
    [:section
     [top-bar-links]]]])

(defn footer
  "The footer at the bottom of the app."
  []
  [:footer
   ])