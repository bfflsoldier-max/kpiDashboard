const form = document.getElementById("project-settings-form");
const statusMessage = document.getElementById("form-status");
const submitButton = form.querySelector('button[type="submit"]');

function showStatus(message, isError = false) {
  statusMessage.textContent = message;
  statusMessage.classList.toggle("is-error", isError);
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  showStatus("");
  submitButton.disabled = true;

  const formData = new FormData(form);
  const project = {
    streamName: formData.get("stream-name").trim(),
    domain: formData.get("domain").trim(),
    projectName: formData.get("project-name").trim(),
    projectId: formData.get("project-id").trim(),
    boardId: Number(formData.get("board-id")),
  };
  const apiBaseUrl = document.querySelector(
    'meta[name="datafeeder-api-base-url"]',
  ).content;
  const apiUrl = new URL("/api/projects", apiBaseUrl);

  try {
    const response = await fetch(apiUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(project),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new Error(
        body?.detail ??
          body?.message ??
          (body?.error
            ? `${body.error} from ${apiUrl.href}`
            : null) ??
          `Request to ${apiUrl.href} failed (${response.status})`,
      );
    }
    showStatus("Project settings saved. Refresh the dashboard menu to see it.");
  } catch (error) {
    showStatus(`Could not save project settings: ${error.message}`, true);
  } finally {
    submitButton.disabled = false;
  }
});

document
  .querySelector(".section-title .button-secondary")
  .addEventListener("click", () => {
    form.reset();
    showStatus("");
  });
