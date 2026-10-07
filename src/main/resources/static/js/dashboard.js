let activeDomainId = null;
let activeProjectTile = null;
let activeProjectData = null;
let activeFeederStreamKey = null;
let registeredProjects = [];
let adminToken = null;
let logoTapCount = 0;
let logoTapTimer = null;
const emptyKpis = {
  projectScore: "--",
  defectLeakageRate: "--",
  testEffectiveness: "--",
  executionRate: "--",
  automationPassRate: "--",
};

function createCircularGauge(value) {
  const numericValue = Number.parseFloat(value);
  const hasValue = Number.isFinite(numericValue);
  const percentage = hasValue ? Math.min(100, Math.max(0, numericValue)) : 0;
  const radius = 49;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - percentage / 100);
  const color = percentage >= 50 ? "#20a464" : "#ef4444";
  const label = hasValue ? `${percentage}%` : "--";

  return `
    <svg class="circular-gauge" viewBox="0 0 120 120" role="img" aria-label="Project score ${label}">
      <circle cx="60" cy="60" r="${radius}" fill="none" stroke="#b8b5b5" stroke-width="8" />
      ${hasValue ? `<circle cx="60" cy="60" r="${radius}" fill="none" stroke="${color}" stroke-width="8" stroke-dasharray="${circumference}" stroke-dashoffset="${offset}" stroke-linecap="round" transform="rotate(-90 60 60)" />` : ""}
      <text x="60" y="60" text-anchor="middle" dominant-baseline="middle" font-size="25" font-weight="700" fill="#111111">${label}</text>
    </svg>
  `;
}

function initializeApp() {
  document.getElementById("tiles").style.display = "none";
  document.getElementById("userSection").classList.remove("active");
  document.getElementById("overviewSection").classList.remove("hidden");
  document
    .querySelector(".hero-wordmark")
    .addEventListener("click", handleLogoTap);
  document
    .querySelector(".hero-wordmark")
    .addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        handleLogoTap();
      }
    });
  document
    .getElementById("admin-login-form")
    .addEventListener("submit", loginAdmin);
}

function feederApiUrl(path) {
  const baseUrl = window.dashboardDataFeederBaseUrl.replace(/\/+$/, "");
  return `${baseUrl}${path}`;
}

function handleLogoTap() {
  logoTapCount += 1;
  clearTimeout(logoTapTimer);
  logoTapTimer = setTimeout(() => {
    logoTapCount = 0;
  }, 1800);

  if (logoTapCount === 5) {
    logoTapCount = 0;
    openAdminLogin();
  }
}

function openAdminLogin() {
  if (adminToken) return;
  const dialog = document.getElementById("admin-login-dialog");
  document.getElementById("admin-login-status").textContent = "";
  dialog.showModal();
  dialog.querySelector('[name="username"]').focus();
}

function closeAdminLogin() {
  const dialog = document.getElementById("admin-login-dialog");
  dialog.close();
  document.getElementById("admin-login-form").reset();
}

async function loginAdmin(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const status = document.getElementById("admin-login-status");
  const submitButton = form.querySelector('button[type="submit"]');
  submitButton.disabled = true;
  status.textContent = "";

  try {
    const formData = new FormData(form);
    const response = await fetch(feederApiUrl("/api/admin/login"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: formData.get("username").trim(),
        password: formData.get("password"),
      }),
    });
    if (!response.ok) {
      throw new Error(
        response.status === 401
          ? "Invalid username or password"
          : `Login request failed (${response.status})`,
      );
    }
    const result = await response.json();
    if (typeof result.token !== "string" || result.token.length === 0) {
      throw new Error("The Data Feeder returned an invalid admin session");
    }

    adminToken = result.token;
    document.body.classList.add("admin-mode");
    document.getElementById("admin-mode-button").classList.remove("hidden");
    closeAdminLogin();
    renderRegisteredProjects(registeredProjects);
    refreshVisibleFeederStream();
  } catch (error) {
    status.textContent = error.message;
  } finally {
    submitButton.disabled = false;
  }
}

function logoutAdmin() {
  adminToken = null;
  document.body.classList.remove("admin-mode");
  document.getElementById("admin-mode-button").classList.add("hidden");
  renderRegisteredProjects(registeredProjects);
  refreshVisibleFeederStream();
}

async function adminDelete(path, requestBody) {
  if (!adminToken) {
    throw new Error("Log in to admin mode before deleting data");
  }
  const response = await fetch(feederApiUrl(path), {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${adminToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(requestBody),
  });
  if (response.status === 401) {
    logoutAdmin();
    throw new Error("Your admin session expired. Log in again.");
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(
      body?.detail ?? body?.message ?? `Delete request failed (${response.status})`,
    );
  }
}

async function deleteFeederProject(project) {
  const projectLabel = formatProjectName(project.projectName);
  if (!window.confirm(`Delete project "${projectLabel}" from ${project.streamName}?`)) {
    return;
  }

  try {
    await adminDelete("/api/projects", {
      domain: project.domain,
      streamName: project.streamName,
      projectId: project.projectId,
      boardId: project.boardId,
    });
    registeredProjects = registeredProjects.filter(
      (entry) =>
        entry.domain !== project.domain ||
        entry.streamName !== project.streamName ||
        entry.projectId !== project.projectId ||
        entry.boardId !== project.boardId,
    );
    renderRegisteredProjects(registeredProjects);
    refreshVisibleFeederStream();
  } catch (error) {
    showDashboardStatus(`Could not delete project: ${error.message}`);
  }
}

async function deleteFeederStream(domain, streamName, projectCount) {
  const detail = projectCount === 1 ? "1 project" : `${projectCount} projects`;
  if (
    !window.confirm(
      `Delete stream "${streamName}" from ${domain} and all ${detail} in it?`,
    )
  ) {
    return;
  }

  try {
    await adminDelete("/api/projects/stream", { domain, streamName });
    const deletedStreamKey = feederStreamKey(domain, streamName);
    registeredProjects = registeredProjects.filter(
      (project) =>
        feederStreamKey(project.domain, project.streamName) !== deletedStreamKey,
    );
    renderRegisteredProjects(registeredProjects);
    if (activeFeederStreamKey === feederStreamKey(domain, streamName)) {
      showOverview();
    }
  } catch (error) {
    showDashboardStatus(`Could not delete stream: ${error.message}`);
  }
}

function showDashboardStatus(message) {
  const status = document.getElementById("dashboard-status");
  status.textContent = message;
  status.classList.remove("hidden");
}

function feederStreamKey(domain, streamName) {
  return `${domain.trim().toLocaleLowerCase()}\u0000${streamName.trim().toLocaleLowerCase()}`;
}

function refreshVisibleFeederStream() {
  if (!activeFeederStreamKey) return;
  const projects = registeredProjects.filter(
    (project) => feederStreamKey(project.domain, project.streamName) === activeFeederStreamKey,
  );
  if (projects.length === 0) {
    showOverview();
    return;
  }
  activeProjectData = null;
  document.getElementById("userSection").classList.remove("active");
  document.getElementById("tiles").style.display = "grid";
  renderProjectTiles(projects);
}

function showOverview() {
  activeFeederStreamKey = null;
  activeDomainId = null;
  activeProjectData = null;
  document.getElementById("userSection").classList.remove("active");
  document.getElementById("tiles").style.display = "none";
  document.getElementById("overviewSection").classList.remove("hidden");
  document.getElementById("selectedProjectContext").classList.add("hidden");
}

async function loadRegisteredProjects() {
  const status = document.getElementById("dashboard-status");
  try {
    const response = await fetch(feederApiUrl("/api/projects"));
    if (!response.ok) {
      throw new Error(`Request failed (${response.status})`);
    }
    const projects = await response.json();
    if (
      !Array.isArray(projects) ||
      projects.some(
        (project) =>
          typeof project.streamName !== "string" ||
          typeof project.domain !== "string" ||
          typeof project.projectName !== "string" ||
          typeof project.projectId !== "string" ||
          !Number.isInteger(project.boardId),
      )
    ) {
      throw new Error("The Data Feeder returned an invalid project list");
    }
    registeredProjects = projects;
    renderRegisteredProjects(projects);
    status.textContent = "";
    status.classList.add("hidden");
  } catch (error) {
    status.textContent = `Could not load feeder projects: ${error.message}. Check that the Data Feeder service is running.`;
    status.classList.remove("hidden");
  }
}

function renderRegisteredProjects(projects) {
  document
    .querySelectorAll(".registered-stream-row")
    .forEach((item) => item.remove());
  document
    .querySelectorAll(".registered-domain-group")
    .forEach((group) => group.remove());

  const streams = new Map();
  projects.forEach((project) => {
    const key = feederStreamKey(project.domain, project.streamName);
    if (!streams.has(key)) streams.set(key, []);
    streams.get(key).push(project);
  });

  streams.forEach((streamProjects) => {
    const { domain, streamName } = streamProjects[0];
    const group = findOrCreateDomainGroup(domain);
    const row = document.createElement("div");
    row.className = "registered-stream-row";
    const item = document.createElement("button");
    item.type = "button";
    item.className = "hero-nav-subitem registered-stream-item";
    item.textContent = streamName;
    item.addEventListener("click", (event) =>
      openFeederStream(event, streamProjects),
    );
    row.append(item);
    if (adminToken) {
      const deleteButton = createDeleteButton(
        `Delete ${streamName} stream`,
        () => deleteFeederStream(domain, streamName, streamProjects.length),
      );
      row.append(deleteButton);
    }
    group.querySelector(".hero-nav-subitems").append(row);
  });
}

function findOrCreateDomainGroup(domainName) {
  const normalizedDomain = domainName.trim().toLocaleLowerCase();
  const existingGroup = [...document.querySelectorAll(".hero-nav-group")].find(
    (group) =>
      group.querySelector(".hero-nav-toggle span")?.textContent
        .trim()
        .toLocaleLowerCase() === normalizedDomain,
  );
  if (existingGroup) return existingGroup;

  const group = document.createElement("div");
  group.className = "hero-nav-group registered-domain-group";
  const toggle = document.createElement("button");
  toggle.type = "button";
  toggle.className = "hero-nav-toggle";
  toggle.addEventListener("click", () => toggleNavGroup(toggle));

  const name = document.createElement("span");
  name.textContent = domainName;
  const chevron = document.createElement("span");
  chevron.className = "hero-nav-chevron";
  chevron.setAttribute("aria-hidden", "true");
  toggle.append(name, chevron);

  const subitems = document.createElement("div");
  subitems.className = "hero-nav-subitems";
  group.append(toggle, subitems);
  document.getElementById("heroNavPanel").append(group);
  return group;
}

function openSampleDomain(event, domainId) {
  const domain = window.dashboardSampleData[domainId];
  if (!domain) return;

  activeDomainId = domainId;
  activeFeederStreamKey = null;
  activeProjectData = { ...domain };
  document.getElementById("selectedProjectLabel").textContent =
    `${domain.domain} · Sample Data`;
  document.getElementById("selectedProjectTitle").textContent = domain.project;
  document.getElementById("selectedProjectContext").classList.remove("hidden");
  document.getElementById("overviewSection").classList.add("hidden");
  document.getElementById("tiles").style.display = "grid";
  document.getElementById("userSection").classList.remove("active");
  document
    .querySelectorAll(".hero-nav-subitem")
    .forEach((item) => item.classList.remove("is-active"));
  event?.currentTarget?.classList.add("is-active");
  closeHeroMenu();
  renderProjectTiles([domain], true);
}

function openFeederStream(event, projects) {
  const { domain, streamName } = projects[0];
  activeDomainId = domain.trim().toLocaleLowerCase();
  activeFeederStreamKey = feederStreamKey(domain, streamName);
  activeProjectData = null;
  document.getElementById("selectedProjectLabel").textContent =
    `${domain} · ${streamName}`;
  document.getElementById("selectedProjectTitle").textContent = streamName;
  document.getElementById("selectedProjectContext").classList.remove("hidden");
  document.getElementById("overviewSection").classList.add("hidden");
  document.getElementById("tiles").style.display = "grid";
  document.getElementById("userSection").classList.remove("active");
  document
    .querySelectorAll(".hero-nav-subitem")
    .forEach((item) => item.classList.remove("is-active"));
  event.currentTarget.classList.add("is-active");
  closeHeroMenu();
  renderProjectTiles(projects);
}

function formatProjectName(projectName) {
  return projectName.replace(/datahub/gi, "Data Hub");
}

function renderProjectTiles(projects, isSampleData = false) {
  const tiles = document.getElementById("tiles");
  const projectTiles = projects.map((project) => {
    const tile = document.createElement("article");
    tile.className = "tile sample-project-tile";
    const openButton = document.createElement("button");
    openButton.type = "button";
    openButton.className = "project-tile-open";
    openButton.addEventListener("click", () =>
      openProjectMetrics(project, openButton, isSampleData),
    );

    const header = document.createElement("span");
    header.className = "tile-header sample-tile-heading";
    const text = document.createElement("span");
    text.className = "tile-text";
    const projectName = document.createElement("span");
    projectName.className = "tile-team-name";
    projectName.textContent = isSampleData
      ? project.project
      : formatProjectName(project.projectName);
    const sprintName = document.createElement("span");
    sprintName.className = "tile-sprint";
    sprintName.textContent = isSampleData
      ? project.sprint
      : `${project.projectId}_${project.boardId}`;
    text.append(projectName, sprintName);
    header.append(text);

    const gauge = document.createElement("span");
    gauge.className = "sample-tile-gauge";
    gauge.innerHTML = createCircularGauge(
      (project.kpis ?? emptyKpis).projectScore,
    );

    const insightsTitle = document.createElement("span");
    insightsTitle.className = "insights-title";
    insightsTitle.textContent = "Key Insights";
    const insights = document.createElement("span");
    insights.className = "title-content";
    [
      "Defect Leakage",
      "Test Effectiveness",
      "Execution Rate",
      "Automation Pass Rate",
    ].forEach((insight) => {
      const item = document.createElement("span");
      item.className = "titlestyle";
      item.textContent = insight;
      insights.append(item);
    });

    openButton.append(header, gauge, insightsTitle, insights);
    tile.append(openButton);
    if (!isSampleData && adminToken) {
      tile.append(
        createDeleteButton(
          `Delete ${formatProjectName(project.projectName)} project`,
          () => deleteFeederProject(project),
          "project-delete-button",
        ),
      );
    }
    return tile;
  });
  tiles.replaceChildren(...projectTiles);
  activeProjectTile =
    projectTiles[0]?.querySelector(".project-tile-open") ?? null;
}

function createDeleteButton(label, onDelete, extraClass = "") {
  const button = document.createElement("button");
  button.type = "button";
  button.className = `admin-delete-button ${extraClass}`.trim();
  button.setAttribute("aria-label", label);
  button.title = label;
  button.textContent = "×";
  button.addEventListener("click", (event) => {
    event.stopPropagation();
    onDelete();
  });
  return button;
}

function openProjectMetrics(project, tile, isSampleData) {
  activeProjectData = {
    ...project,
    kpis: project.kpis ?? emptyKpis,
    isSampleData,
  };
  activeProjectTile = tile;

  document.getElementById("tiles").style.display = "none";
  document.getElementById("userSection").classList.add("active");
  document.querySelector(".sample-data-note").textContent = isSampleData
    ? "Sample metrics for demonstration"
    : "No KPI metrics have been provided for this project yet.";
  renderTeamKpis(activeProjectData.kpis);
}

function renderTeamKpis(kpis = {}) {
  const metrics = [
    {
      key: "defectLeakageRate",
      name: "Defect Leakage Rate",
      formula: "Production defects / total defects found x 100",
    },
    {
      key: "testEffectiveness",
      name: "Test Effectiveness",
      formula: "Defects found during testing / total defects x 100",
    },
    {
      key: "executionRate",
      name: "Execution Rate",
      formula: "Executed QA subtasks / planned QA subtasks x 100",
    },
    {
      key: "automationPassRate",
      name: "Automation Pass Rate",
      formula: "Passed automation tests / executed automation tests x 100",
    },
  ];

  const cards = metrics.map(({ key, name, formula }) => {
    const article = document.createElement("article");
    article.className = "kpi-card";

    const header = document.createElement("div");
    header.className = "kpi-header";

    const title = document.createElement("div");
    title.className = "kpi-name";
    title.textContent = `${name} (%)`;

    const help = document.createElement("div");
    help.className = "kpi-help";
    help.tabIndex = 0;
    help.setAttribute("aria-label", `Formula for ${name}`);
    help.append("i");

    const tooltip = document.createElement("span");
    tooltip.className = "tooltip";
    tooltip.textContent = formula;
    help.append(tooltip);
    header.append(title, help);

    const value = document.createElement("div");
    value.className = "kpi-value";
    const feederValue = kpis?.[key];
    value.textContent =
      feederValue == null || feederValue === "" ? "--" : feederValue;

    article.append(header, value);
    return article;
  });

  document.getElementById("kpiGrid").replaceChildren(...cards);
}

function goBack() {
  if (!activeDomainId) return;
  document.getElementById("userSection").classList.remove("active");
  document.getElementById("tiles").style.display = "grid";
  activeProjectTile?.focus();
}

function toggleHeroMenu() {
  const menuButton = document.querySelector(".hero-menu-button");
  const isOpen = menuButton.classList.toggle("is-open");
  document.getElementById("heroNavPanel").classList.toggle("hidden", !isOpen);
  menuButton.setAttribute("aria-expanded", String(isOpen));
  if (isOpen) loadRegisteredProjects();
}

function closeHeroMenu() {
  const menuButton = document.querySelector(".hero-menu-button");
  menuButton.classList.remove("is-open");
  menuButton.setAttribute("aria-expanded", "false");
  document.getElementById("heroNavPanel").classList.add("hidden");
}

function toggleNavGroup(toggleButton) {
  const group = toggleButton.closest(".hero-nav-group");
  const willExpand = !group.classList.contains("is-expanded");
  document
    .querySelectorAll(".hero-nav-group")
    .forEach((item) => item.classList.remove("is-expanded"));
  group.classList.toggle("is-expanded", willExpand);
}

initializeApp();
