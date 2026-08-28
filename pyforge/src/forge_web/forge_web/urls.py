from django.contrib import admin
from django.urls import path

from forge_web import views
from forge_web.api import api

urlpatterns = [
    path("admin/", admin.site.urls),
    path("health/", views.health, name="health"),
    path("metrics/", views.metrics, name="metrics"),
    path("weeks/", views.weeks, name="weeks"),
    path("sessions/new/", views.session_create, name="session_create"),
    path("api/", api.urls),
]
