from __future__ import annotations

from django import forms

from forge_web.models import Session


class SessionForm(forms.ModelForm):
    tags_text = forms.CharField(required=False, label="tags")

    class Meta:
        model = Session
        fields = ["slice_id", "started_at", "ended_at"]

    def save(self, commit: bool = True) -> Session:
        session: Session = super().save(commit=False)
        raw = self.cleaned_data.get("tags_text") or ""
        session.tags = [part.strip() for part in raw.split(",") if part.strip()]
        if commit:
            session.save()
        return session
