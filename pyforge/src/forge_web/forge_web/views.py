from __future__ import annotations

from django.http import HttpRequest, HttpResponse
from django.shortcuts import get_object_or_404, redirect, render
from django.views.decorators.http import require_http_methods

from forge_web.actions import delete_session, start_session, stop_session
from forge_web.forms import SessionForm
from forge_web.metrics import current
from forge_web.models import GateAttempt, Session, Slice


def health(_request: HttpRequest) -> HttpResponse:
    return HttpResponse("ok")


def metrics(_request: HttpRequest) -> HttpResponse:
    return HttpResponse(f"requests {current()}\n", content_type="text/plain")


def _week_sessions(tag: str) -> list[Session]:
    rows = list(Session.objects.all().order_by("-id"))
    if tag:
        return [row for row in rows if tag in (row.tags or [])]
    return rows


def _weeks_context(
    request: HttpRequest, form: SessionForm | None = None
) -> dict[str, object]:
    tag = request.GET.get("tag", "")
    prefill = request.GET.get("prefill", "")
    if form is None:
        form = SessionForm(initial={"slice_id": prefill} if prefill else None)
    rows = _week_sessions(tag)
    open_count = Session.objects.filter(started_at__isnull=False, ended_at__isnull=True).count()
    return {
        "sessions": rows,
        "tag": tag,
        "form": form,
        "open_count": open_count,
        "request_count": current(),
        "form_status": "",
    }


def render_weeks(request: HttpRequest, form: SessionForm | None = None) -> HttpResponse:
    context = _weeks_context(request, form=form)
    if form is not None and form.errors:
        context["form_status"] = "slice_id is required" if form["slice_id"].errors else "fix the form"
    template = "forge_web/weeks_partial.html" if request.headers.get("HX-Request") else "forge_web/weeks.html"
    return render(request, template, context)


def board(request: HttpRequest) -> HttpResponse:
    recent = list(Session.objects.all().order_by("-id")[:5])
    return render(
        request,
        "forge_web/board.html",
        {
            "open_count": Session.objects.filter(started_at__isnull=False, ended_at__isnull=True).count(),
            "slice_count": Slice.objects.count(),
            "gate_count": GateAttempt.objects.count(),
            "session_count": Session.objects.count(),
            "request_count": current(),
            "recent": recent,
        },
    )


def slice_list(request: HttpRequest) -> HttpResponse:
    return render(
        request,
        "forge_web/slices.html",
        {
            "slices": list(Slice.objects.all().order_by("slice_id")),
            "request_count": current(),
            "open_count": Session.objects.filter(started_at__isnull=False, ended_at__isnull=True).count(),
        },
    )


def gate_list(request: HttpRequest) -> HttpResponse:
    return render(
        request,
        "forge_web/gates.html",
        {
            "attempts": list(GateAttempt.objects.all().order_by("-id")),
            "request_count": current(),
            "open_count": Session.objects.filter(started_at__isnull=False, ended_at__isnull=True).count(),
        },
    )


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
    response = render_weeks(request, form=form)
    response.status_code = 422
    return response


@require_http_methods(["POST"])
def session_start_view(request: HttpRequest, pk: int) -> HttpResponse:
    start_session(get_object_or_404(Session, pk=pk))
    return render_weeks(request)


@require_http_methods(["POST"])
def session_stop_view(request: HttpRequest, pk: int) -> HttpResponse:
    stop_session(get_object_or_404(Session, pk=pk))
    return render_weeks(request)


@require_http_methods(["POST"])
def session_delete_view(request: HttpRequest, pk: int) -> HttpResponse:
    delete_session(get_object_or_404(Session, pk=pk))
    return render_weeks(request)
