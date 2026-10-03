let activeDomainId = null;
let activeProjectTile = null;

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
}

function openSampleDomain(event, domainId) {
  const domain = window.dashboardSampleData[domainId];
  if (!domain) return;

  activeDomainId = domainId;
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
  renderProjectTile(domain);
}

function renderProjectTile(domain) {
  const tiles = document.getElementById("tiles");
  tiles.innerHTML = `
        <button type="button" class="tile sample-project-tile" onclick="openProjectMetrics()">
      <span class="tile-header sample-tile-heading">
        <span class="tile-text">
          <span class="tile-team-name">${domain.project}</span>
          <span class="tile-sprint">${domain.sprint}</span>
        </span>
            </span>
      <span class="sample-tile-gauge">${createCircularGauge(domain.kpis.projectScore)}</span>
      <span class="insights-title">Key Insights</span>
            <span class="title-content">
                <span class="titlestyle">Defect Leakage</span>
                <span class="titlestyle">Test Effectiveness</span>
                <span class="titlestyle">Execution Rate</span>
                <span class="titlestyle">Automation Pass Rate</span>
            </span>
        </button>
    `;
  activeProjectTile = tiles.querySelector(".sample-project-tile");
}

function openProjectMetrics() {
  const domain = window.dashboardSampleData[activeDomainId];
  if (!domain) return;

  document.getElementById("tiles").style.display = "none";
  document.getElementById("userSection").classList.add("active");
  renderTeamKpis(domain.kpis);
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
