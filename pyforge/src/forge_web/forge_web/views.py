from __future__ import annotations

from django.http import HttpRequest, HttpResponse
from django.shortcuts import redirect, render
from django.views.decorators.http import require_http_methods

from forge_web.forms import SessionForm
from forge_web.metrics import current
from forge_web.models import Session


def health(_request: HttpRequest) -> HttpResponse:
    return HttpResponse("ok")


def metrics(_request: HttpRequest) -> HttpResponse:
    return HttpResponse(f"requests {current()}\n", content_type="text/plain")


def _week_sessions(tag: str) -> list[Session]:
    qs = Session.objects.all().order_by("id")
    if tag:
        qs = [row for row in qs if tag in (row.tags or [])]
        return list(qs)
    return list(qs)


def render_weeks(request: HttpRequest, form: SessionForm | None = None) -> HttpResponse:
    tag = request.GET.get("tag", "")
    rows = _week_sessions(tag)
    template = "forge_web/weeks_rows.html" if request.headers.get("HX-Request") else "forge_web/weeks.html"
    return render(request, template, {"sessions": rows, "tag": tag, "form": form or SessionForm()})


@require_http_methods(["GET"])
def weeks(request: HttpRequest) -> HttpResponse:
    return render_weeks(request)


@require_http_methods(["POST"])
def session_create(request: HttpRequest) -> HttpResponse:
    form = SessionForm(request.POST)
    if form.is_valid():
        form.save()
        if request.headers.get("HX-Request"):
            return render_weeks(request)
        return redirect("weeks")
    return render_weeks(request, form=form)
