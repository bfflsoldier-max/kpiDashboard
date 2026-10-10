const form = document.getElementById("project-settings-form");
const statusMessage = document.getElementById("form-status");
const submitButton = form.querySelector('button[type="submit"]');
const domainInput = form.elements.domain;
const domainTrigger = document.getElementById("domain-select-trigger");
const domainOptions = document.getElementById("domain-options");
const domainSelectedValue = document.getElementById("domain-selected-value");
const domainOptionElements = Array.from(
  domainOptions.querySelectorAll('[role="option"]'),
);

function setDomain(value) {
  domainInput.value = value;
  domainSelectedValue.textContent = value || "Select a domain";
  domainTrigger.setAttribute("aria-invalid", "false");
  domainOptionElements.forEach((option) => {
    option.setAttribute(
      "aria-selected",
      String(option.dataset.value === value),
    );
  });
}

function closeDomainOptions(returnFocus = false) {
  domainOptions.hidden = true;
  domainTrigger.setAttribute("aria-expanded", "false");
  if (returnFocus) domainTrigger.focus();
}

function openDomainOptions() {
  domainOptions.hidden = false;
  domainTrigger.setAttribute("aria-expanded", "true");
  const selectedOption =
    domainOptionElements.find(
      (option) => option.dataset.value === domainInput.value,
    ) ?? domainOptionElements[0];
  selectedOption.focus();
}

function selectDomain(option) {
  setDomain(option.dataset.value);
  closeDomainOptions(true);
}

domainTrigger.addEventListener("click", () => {
  if (domainOptions.hidden) {
    openDomainOptions();
  } else {
    closeDomainOptions();
  }
});

domainTrigger.addEventListener("keydown", (event) => {
  if (["ArrowDown", "ArrowUp", "Enter", " "].includes(event.key)) {
    event.preventDefault();
    openDomainOptions();
  }
});

domainOptions.addEventListener("click", (event) => {
  const option = event.target.closest('[role="option"]');
  if (option) selectDomain(option);
});

domainOptions.addEventListener("keydown", (event) => {
  const currentIndex = domainOptionElements.indexOf(document.activeElement);
  let nextIndex = currentIndex;

  if (event.key === "ArrowDown") nextIndex = (currentIndex + 1) % domainOptionElements.length;
  else if (event.key === "ArrowUp") {
    nextIndex =
      (currentIndex - 1 + domainOptionElements.length) %
      domainOptionElements.length;
  } else if (event.key === "Home") nextIndex = 0;
  else if (event.key === "End") nextIndex = domainOptionElements.length - 1;
  else if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    selectDomain(domainOptionElements[currentIndex]);
    return;
  } else if (event.key === "Escape") {
    event.preventDefault();
    closeDomainOptions(true);
    return;
  } else {
    return;
  }

  event.preventDefault();
  domainOptionElements[nextIndex].focus();
});

document.addEventListener("click", (event) => {
  if (!event.target.closest(".domain-select")) closeDomainOptions();
});

form.addEventListener("reset", () => {
  requestAnimationFrame(() => setDomain(""));
});

function showStatus(message, isError = false) {
  statusMessage.textContent = message;
  statusMessage.classList.toggle("is-error", isError);
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  showStatus("");
  if (!domainInput.value) {
    showStatus("Select a domain.", true);
    domainTrigger.setAttribute("aria-invalid", "true");
    domainTrigger.focus();
    return;
  }
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
