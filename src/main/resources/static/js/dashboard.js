let activeDomainId = null;
let activeFeederDomainKey = null;
let registeredProjects = [];
let adminToken = null;
let logoTapCount = 0;
let logoTapTimer = null;
const emptyKpis = {
  defectLeakageRate: "--",
  testEffectiveness: "--",
  executionRate: "--",
  automationPassRate: "--",
};

function initializeApp() {
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
  loadRegisteredProjects();
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
    refreshVisibleFeederDomain();
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
  refreshVisibleFeederDomain();
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
    refreshVisibleFeederDomain();
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
    registeredProjects = registeredProjects.filter(
      (project) =>
        normalizedName(project.domain) !== normalizedName(domain) ||
        normalizedName(project.streamName) !== normalizedName(streamName),
    );
    renderRegisteredProjects(registeredProjects);
    refreshVisibleFeederDomain();
  } catch (error) {
    showDashboardStatus(`Could not delete stream: ${error.message}`);
  }
}

function showDashboardStatus(message) {
  const status = document.getElementById("dashboard-status");
  status.textContent = message;
  status.classList.remove("hidden");
}

function normalizedName(value) {
  return value.trim().toLocaleLowerCase();
}

function feederDomainKey(streamName, projectName, domain) {
  return [streamName, projectName, domain].map(normalizedName).join("\u0000");
}

function refreshVisibleFeederDomain() {
  if (!activeFeederDomainKey) return;
  const projects = registeredProjects.filter(
    (project) =>
      feederDomainKey(project.streamName, project.projectName, project.domain) ===
      activeFeederDomainKey,
  );
  if (projects.length === 0) {
    showOverview();
    return;
  }
  renderDomainKpis(projects);
}

function showOverview() {
  activeFeederDomainKey = null;
  activeDomainId = null;
  document.getElementById("userSection").classList.remove("active");
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
  const nav = document.getElementById("heroNavPanel");
  nav.replaceChildren();
  const hasProjects = projects.length > 0;
  const overviewDescription = document.getElementById("overviewDescription");
  overviewDescription.textContent = hasProjects
    ? "Delivery 360 provides a single view of project delivery across Development, QA, and DevOps. It brings together key project metrics, sprint progress, team activity, and delivery status to help teams and managers quickly understand how projects are progressing."
    : "";
  overviewDescription.classList.toggle("hidden", !hasProjects);
  document.getElementById("overviewMessage").textContent = hasProjects
    ? "Use the navigation menu to select a domain, project, and dashboard to view the metrics relevant to your team."
    : "Add a stream, project name, and domain in the Data Feeder to get started.";
  document.getElementById("overviewTitle").classList.toggle(
    "hidden",
    hasProjects,
  );
  if (!hasProjects) {
    document.getElementById("overviewTitle").textContent =
      "No dashboards configured yet";
  }
  if (projects.length === 0) {
    const emptyNote = document.createElement("p");
    emptyNote.className = "hero-nav-empty";
    emptyNote.textContent = "No streams have been added yet.";
    nav.append(emptyNote);
    return;
  }

  const streams = groupBy(projects, (project) => project.streamName);
  let groupIndex = 0;
  streams.forEach((streamProjects, streamName) => {
    const streamGroup = createNavGroup(streamName, nav, groupIndex++);
    const streamDomains = new Set();
    const namedProjects = groupBy(streamProjects, (project) => project.projectName);

    namedProjects.forEach((namedProjectEntries, projectName) => {
      const projectGroup = createNavGroup(
        projectName,
        streamGroup.subitems,
        groupIndex++,
      );
      const domains = groupBy(namedProjectEntries, (project) => project.domain);

      domains.forEach((domainProjects, domainName) => {
        const row = document.createElement("div");
        row.className = "registered-domain-row";
        const item = document.createElement("button");
        item.type = "button";
        item.className = "hero-nav-subitem registered-domain-item";
        item.textContent = domainName;
        item.addEventListener("click", (event) =>
          openFeederDomain(event, domainProjects),
        );
        row.append(item);

        const domainKey = normalizedName(domainName);
        if (adminToken && !streamDomains.has(domainKey)) {
          row.append(
            createDeleteButton(
              `Delete ${domainName} from ${streamName}`,
              () =>
                deleteFeederStream(
                  domainProjects[0].domain,
                  streamName,
                  streamProjects.filter(
                    (project) => normalizedName(project.domain) === domainKey,
                  ).length,
                ),
            ),
          );
        }
        streamDomains.add(domainKey);
        projectGroup.subitems.append(row);
      });
    });
  });
}

function groupBy(items, getName) {
  const groups = new Map();
  items.forEach((item) => {
    const name = getName(item);
    const key = normalizedName(name);
    if (!groups.has(key)) groups.set(key, { name, items: [] });
    groups.get(key).items.push(item);
  });
  return new Map(
    [...groups.values()].map(({ name, items }) => [name, items]),
  );
}

function createNavGroup(label, parent, index) {
  const group = document.createElement("div");
  group.className = "hero-nav-group registered-nav-group";
  const toggle = document.createElement("button");
  const subitemsId = `hero-nav-subitems-${index}`;
  toggle.type = "button";
  toggle.className = "hero-nav-toggle";
  toggle.setAttribute("aria-expanded", "false");
  toggle.setAttribute("aria-controls", subitemsId);
  toggle.addEventListener("click", () => toggleNavGroup(toggle));

  const name = document.createElement("span");
  name.textContent = label;
  const chevron = document.createElement("span");
  chevron.className = "hero-nav-chevron";
  chevron.setAttribute("aria-hidden", "true");
  toggle.append(name, chevron);

  const subitems = document.createElement("div");
  subitems.id = subitemsId;
  subitems.className = "hero-nav-subitems";
  group.append(toggle, subitems);
  parent.append(group);
  return { group, subitems };
}

function openFeederDomain(event, projects) {
  const { domain, streamName, projectName } = projects[0];
  activeDomainId = normalizedName(domain);
  activeFeederDomainKey = feederDomainKey(streamName, projectName, domain);
  document.getElementById("selectedProjectLabel").textContent =
    `${streamName} · ${domain}`;
  document.getElementById("selectedProjectTitle").textContent = projectName;
  document.getElementById("selectedProjectContext").classList.remove("hidden");
  document.getElementById("overviewSection").classList.add("hidden");
  document.getElementById("userSection").classList.add("active");
  document
    .querySelectorAll(".hero-nav-subitem")
    .forEach((item) => item.classList.remove("is-active"));
  event.currentTarget.classList.add("is-active");
  closeHeroMenu();
  renderDomainKpis(projects);
}

function formatProjectName(projectName) {
  return projectName.replace(/datahub/gi, "Data Hub");
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

function renderDomainKpis(projects) {
  const sections = projects.map((project) => {
    const section = document.createElement("section");
    section.className = "kpi-section";

    const heading = document.createElement("div");
    heading.className = "project-dashboard-heading";
    const tabs = document.createElement("div");
    tabs.className = "project-dashboard-tabs";
    [
      {
        label: `${project.projectId} · Board ${project.boardId}`,
        className: "project-dashboard-tab is-current",
      },
      {
        label: "Last Sprint",
        className: "project-dashboard-tab is-placeholder",
      },
      {
        label: "Last to Last Sprint",
        className: "project-dashboard-tab is-placeholder",
      },
    ].forEach(({ label, className }) => {
      const tab = document.createElement("button");
      tab.type = "button";
      tab.className = className;
      tab.textContent = label;
      tab.setAttribute("aria-pressed", String(className.includes("is-current")));
      tab.addEventListener("click", () => {
        tabs.querySelectorAll(".project-dashboard-tab").forEach((item) => {
          const isSelected = item === tab;
          item.classList.toggle("is-current", isSelected);
          item.classList.toggle("is-placeholder", !isSelected);
          item.setAttribute("aria-pressed", String(isSelected));
        });
      });
      tabs.append(tab);
    });
    heading.append(tabs);

    if (adminToken) {
      heading.classList.add("has-project-delete");
      heading.append(
        createDeleteButton(
          `Delete ${formatProjectName(project.projectName)} project`,
          () => deleteFeederProject(project),
          "project-delete-button",
        ),
      );
    }

    const title = document.createElement("h4");
    title.className = "kpi-title";
    title.textContent = "Performance KPIs";
    const grid = document.createElement("div");
    grid.className = "kpi-grid";
    renderTeamKpis(project.kpis ?? emptyKpis, grid);
    section.append(heading, title, grid);
    return section;
  });
  document.getElementById("kpiSections").replaceChildren(...sections);
}

function renderTeamKpis(kpis = {}, gridElement) {
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

  gridElement.replaceChildren(...cards);
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
  [...group.parentElement.children]
    .filter((item) => item.classList.contains("hero-nav-group"))
    .forEach((item) => {
      item.classList.remove("is-expanded");
      item
        .querySelector(":scope > .hero-nav-toggle")
        ?.setAttribute("aria-expanded", "false");
    });
  group.classList.toggle("is-expanded", willExpand);
  toggleButton.setAttribute("aria-expanded", String(willExpand));
}

initializeApp();
