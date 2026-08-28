document.addEventListener("htmx:configRequest", (event) => {
  const meta = document.querySelector('meta[name="csrf-token"]');
  if (meta) {
    event.detail.headers["X-CSRFToken"] = meta.getAttribute("content") || "";
  }
});
