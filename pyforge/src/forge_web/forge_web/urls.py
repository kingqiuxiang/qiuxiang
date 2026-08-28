from django.contrib import admin
from django.urls import path

from forge_web import views
from forge_web.api import api

urlpatterns = [
    path("admin/", admin.site.urls),
    path("", views.board, name="board"),
    path("health/", views.health, name="health"),
    path("metrics/", views.metrics, name="metrics"),
    path("weeks/", views.weeks, name="weeks"),
    path("slices/", views.slice_list, name="slices"),
    path("gates/", views.gate_list, name="gates"),
    path("sessions/new/", views.session_create, name="session_create"),
    path("sessions/<int:pk>/start/", views.session_start_view, name="session_start"),
    path("sessions/<int:pk>/stop/", views.session_stop_view, name="session_stop"),
    path("sessions/<int:pk>/delete/", views.session_delete_view, name="session_delete"),
    path("api/", api.urls),
]
