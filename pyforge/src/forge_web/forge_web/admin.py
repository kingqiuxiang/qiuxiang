from django.contrib import admin

from forge_web.models import GateAttempt, Session, Slice

admin.site.register(Session)
admin.site.register(Slice)
admin.site.register(GateAttempt)
