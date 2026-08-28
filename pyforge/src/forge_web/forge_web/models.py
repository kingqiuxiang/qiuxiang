from __future__ import annotations

from django.db import models


class Session(models.Model):
    slice_id = models.CharField(max_length=64)
    started_at = models.DateTimeField(null=True, blank=True)
    ended_at = models.DateTimeField(null=True, blank=True)
    tags = models.JSONField(default=list)

    class Meta:
        db_table = "sessions"


class Slice(models.Model):
    slice_id = models.CharField(max_length=64, primary_key=True)
    title = models.CharField(max_length=200)
    tags = models.JSONField(default=list)

    class Meta:
        db_table = "slices"


class GateAttempt(models.Model):
    gate_name = models.CharField(max_length=64)
    slice_id = models.CharField(max_length=64)
    ok = models.BooleanField(default=False)
    error = models.TextField(blank=True)

    class Meta:
        db_table = "gate_attempts"
        constraints = [
            models.UniqueConstraint(fields=["gate_name", "slice_id"], name="uniq_gate_slice"),
        ]
