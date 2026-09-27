function createCircularGauge(percentage) {
    const ringRadius = 49;
    const centerRadius = 39;
    const circumference = 2 * Math.PI * ringRadius;
    const offset = circumference - (percentage / 100) * circumference;
    const progressColor = percentage < 50 ? "#ef4444" : "#20f004";
    return `
    <svg width="120" height="120" viewBox="0 0 120 120" class="circular-gauge">
        <!-- Background circle -->
        <circle cx="60" cy="60" r="${ringRadius}" fill="none" stroke="#b6b0b0" stroke-width="8"/>
        <!-- White center fill -->
        <circle cx="60" cy="60" r="${centerRadius}" fill="#ffffff"/>
        <!-- Progress circle -->
        <circle cx="60" cy="60" r="${ringRadius}" fill="none" stroke="${progressColor}" stroke-width="8" 
                stroke-dasharray="${circumference}" stroke-dashoffset="${offset}"
                stroke-linecap="round" style="transform: rotate(-90deg); transform-origin: 60px 60px; transition: stroke-dashoffset 0.5s ease;"/>
        <!-- Percentage text in center -->
        <text x="60" y="60" text-anchor="middle" dominant-baseline="middle" font-size="32" font-weight="700" fill="#000000">${percentage}%</text>
    </svg>
    `;
}

let globalTeamMap = {};
let globalAutomationCoverage = {};
let sprintInfo = {};
let currentSprintData = {
    summary: null,
    users: []
};
let currentSprintName = "";
let isBellMediaQaSelected = false;
const REQUEST_TIMEOUT_MS = 15000;
const LOADER_FAILSAFE_MS = 20000;
const INITIAL_LOAD_MAX_RETRIES = 3;
const INITIAL_LOAD_RETRY_DELAY_MS = 1500;
let loaderFailSafeTimer = null;

function setPageTitleText(title) {
    const pageTitle = document.getElementById("pageTitle");
    if (pageTitle) {
        pageTitle.innerText = title;
    }
}

function wait(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function setLoaderVisible(isVisible, message = "Loading dashboard") {
    const loader = document.getElementById("loader");
    const loaderText = document.querySelector(".loader-text");
    if (!loader || !loaderText) return;
    if (loaderFailSafeTimer) {
        clearTimeout(loaderFailSafeTimer);
        loaderFailSafeTimer = null;
    }
    loaderText.innerText = message;
    if (isVisible) {
        loader.classList.remove("hidden", "hide");
        loader.style.display = "flex";
        loaderFailSafeTimer = setTimeout(() => {
            console.warn("Loader fail-safe triggered: hiding loader after timeout.");
            setLoaderVisible(false);
        }, LOADER_FAILSAFE_MS);
        return;
    }
    loader.classList.add("hide");
    setTimeout(() => {
        loader.classList.add("hidden");
        loader.style.display = "none";
        loader.classList.remove("hide");
    }, 400);
}

function resetDashboardState() {
    globalTeamMap = {};
    sprintInfo = {};
    currentSprintData = {
        summary: null,
        users: []
    };
    currentSprintName = "";
    window.currentTeam = null;
    document.getElementById("search").value = "";
    document.getElementById("searchResults").innerHTML = "";
    document.getElementById("searchSection").classList.add("hidden");
    document.getElementById("tiles").innerHTML = "";
    document.getElementById("sprintTabs").innerHTML = "";
    document.getElementById("userDropdown").innerHTML = '<option value="">Select User</option>';
    document.getElementById("workflowGrid").innerHTML = "";
    document.getElementById("kpiGrid").innerHTML = "";
    document.getElementById("selectedProjectContext").classList.add("hidden");
    setPageTitleText("QA Delivery Dashboard");
    document.querySelector(".user-filter").style.display = "block";
    updateUI(null);
}

function initializeApp() {
    updateUI(null);
}

function openBellMediaQaDashboard(event) {
    isBellMediaQaSelected = true;
    document.getElementById("selectedProjectContext").classList.remove("hidden");
    const heroNavPanel = document.getElementById("heroNavPanel");
    const menuButton = document.querySelector(".hero-menu-button");
    heroNavPanel.classList.add("hidden");
    menuButton.classList.remove("is-open");
    menuButton.setAttribute("aria-expanded", "false");
    document.querySelectorAll(".hero-nav-subitem").forEach((item) => {
        item.classList.remove("is-active");
    });
    event?.currentTarget?.classList.add("is-active");
    updateUI(null);
    fetchData();
}

function setExecutionInsightsTitleVisible(isVisible) {
    const workflowTitle = document.querySelector(".workflow-title");
    if (!workflowTitle) return;
    workflowTitle.style.display = isVisible ? "" : "none";
}

function fetchJsonOrThrow(url, timeoutMs = REQUEST_TIMEOUT_MS) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => {
        controller.abort();
    }, timeoutMs);
    return fetch(url, { signal: controller.signal })
        .then(response => {
            if (!response.ok) {
                throw new Error(`Request failed: ${response.status}`);
            }
            return response.json();
        })
        .catch(err => {
            if (err.name === "AbortError") {
                throw new Error(`Request timeout after ${timeoutMs}ms: ${url}`);
            }
            throw err;
        })
        .finally(() => {
            clearTimeout(timeoutId);
        });
}

function fetchData(attempt = 1) {
    if (attempt === 1) {
        setLoaderVisible(true, "Loading dashboard");
    }
    Promise.all([
        fetchJsonOrThrow("/arcxpTeamSummary"),
        fetchJsonOrThrow("/datahubTeamSummary"),
        fetchJsonOrThrow("/sprintInfo"),
        fetchJsonOrThrow("/automationCoverage")
    ])
        .then(([arcxpSummary, datahubSummary, sprint, automationCoverage]) => {
            sprintInfo = sprint;
            globalAutomationCoverage = automationCoverage?.projects || {};
            globalTeamMap = {};
            globalTeamMap["Arc Xp"] = {
                summary: arcxpSummary,
                users: arcxpSummary.users || []
            };
            globalTeamMap["Data Hub"] = {
                summary: datahubSummary,
                users: datahubSummary.users || []
            };
            Object.values(teamMapping).forEach(team => {
                if (!globalTeamMap[team]) {

                    globalTeamMap[team] = {
                        summary: {},
                        users: []
                    };
                }
            });
            renderTiles();
            hydrateTileDefectDensity();
            setLoaderVisible(false);
        })
        .catch(async err => {
            console.error(`Dashboard load attempt ${attempt} failed`, err);
            if (attempt < INITIAL_LOAD_MAX_RETRIES) {
                const retryMessages = [
                    "Please wait...Hang in there, one more try!",
                    "Still loading... have you tried turning it off and on again?"
                ];
                const msg = retryMessages[attempt - 1] ?? `This bug is marked as "Won't Fix"... not just kidding, retrying`;
                setLoaderVisible(true, msg);
                await wait(INITIAL_LOAD_RETRY_DELAY_MS * attempt);
                fetchData(attempt + 1);
                return;
            }
            resetDashboardState();
            setLoaderVisible(false);
        });
}

function getTeam(name) {
    for (let key in teamMapping) {
        if (name.includes(key)) return teamMapping[key];
    }
    return null;
}

function getCurrentSprintNameForTeam(team) {
    const project = mapTeamToProject(team);
    return project ? sprintInfo[project]?.name || "" : "";
}

function hydrateTileDefectDensity() {
    const kpiTeams = Object.keys(globalTeamMap).filter(team => isKpiSupportedTeam(team));
    const requests = kpiTeams.map(team => {
        const sprintName = getCurrentSprintNameForTeam(team);
        if (!sprintName) {
            return Promise.resolve();
        }
        const endpoint = getDefectDensityEndpoint(team);
        const url = `${endpoint}?sprintName=${encodeURIComponent(sprintName)}`;
        return fetchJsonOrThrow(url)
            .then(kpi => {
                if (!globalTeamMap[team]) {
                    return;
                }
                globalTeamMap[team].summary = globalTeamMap[team].summary || {};
                globalTeamMap[team].summary.defectDensity = kpi?.defectDensity ?? "--";
            })
            .catch(err => {
                console.error(`Tile defect density load failed for ${team}`, err);
            });
    });
    Promise.all(requests).finally(() => {
        renderTiles();
    });
}

function renderTiles() {
    const container = document.getElementById("tiles");
    container.innerHTML = "";
    Object.keys(globalTeamMap).forEach(team => {
        const project = mapTeamToProject(team);
        const activeSprint = project ? sprintInfo[project]?.name || "No Sprint" : "No Sprint";
        const automationCoveragePercent = getAutomationCoveragePercentForTeam(team);

        container.innerHTML += `
        <div class="tile" onclick="openTeam('${team}')">
            <div class="tile-header">
                <div class="tile-text">
                    <span class="tile-team-name">${team}</span>
                    <span class="tile-sprint">${activeSprint}</span>
                </div>
                <div class="automation-widget">
                <div class="tile-gauge-container">
                    ${createCircularGauge(automationCoveragePercent)}
                </div>
                </div>
            </div>
            <div class="insights-title" id="insightsStyle">Key Insights</div>
            <div class="title-content">
                <div class="titlestyle">Defect Leakage</div>
                <div class="titlestyle">Execution Rate</div>
                <div class="titlestyle">Test Effectiveness</div>
                <div class="titlestyle">Automation Pass Rate</div>
            </div>
        </div>
        `;
    });
}

function getAutomationCoveragePercentForTeam(team) {
    const project = mapTeamToProject(team);
    const coverageData = project ? (globalAutomationCoverage[project] || {}) : {};
    const totalCases = coverageData.totalTestCases || 0;
    const automatedCases = coverageData.totalAutomatedCases || 0;
    return totalCases > 0 ? Math.round((automatedCases / totalCases) * 100) : 0;
}

function getAutomationCoverageDisplayForTeam(team) {
    return `${getAutomationCoveragePercentForTeam(team)}%`;
}

function getAutomationPassRatePercentForTeam(team) {
    const project = mapTeamToProject(team);
    const coverageData = project ? (globalAutomationCoverage[project] || {}) : {};
    const totalAutomatedCases = coverageData.totalAutomatedCases || 0;
    const passedAutomationTests = coverageData.passedAutomationTests || 0;
    return totalAutomatedCases > 0 ? Math.round((passedAutomationTests / totalAutomatedCases) * 100) : 0;
}

function getAutomationPassRateDisplayForTeam(team) {
    return `${getAutomationPassRatePercentForTeam(team)}%`;
}

function getWorkflowSummaryBySprintEndpoint(team) {
    if (team === "Data Hub") {
        return "/datahubTeamSummaryBySprint";
    }
    return "/arcxpTeamSummaryBySprint";
}

function isKpiSupportedTeam(team) {
    return team === "Arc Xp" || team === "Data Hub";
}

function getDefectDensityEndpoint(team) {
    return team === "Data Hub"
        ? "/datahubDefectDensityKpiBySprint"
        : "/arcxpDefectDensityKpiBySprint";
}

function getOpenCloseEndpoint(team) {
    return team === "Data Hub"
        ? "/datahubOpenCloseRatioKpiBySprints"
        : "/arcxpOpenCloseRatioKpiBySprints";
}

function clearAndHideWorkflowGrid() {
    const workflowGrid = document.getElementById("workflowGrid");
    workflowGrid.innerHTML = "";
    workflowGrid.classList.add("hidden");
    setExecutionInsightsTitleVisible(false);
}

function showWorkflowGrid() {
    const workflowGrid = document.getElementById("workflowGrid");
    workflowGrid.classList.remove("hidden");
}

function loadSprintData(sprintName, callback = null) {
    const currentTeam = window.currentTeam;
    if (!currentTeam) return;
    const dropdown = document.getElementById("userDropdown");
    const previousSelectedUser =
        dropdown.options[dropdown.selectedIndex]?.text;
    const workflowEndpoint = getWorkflowSummaryBySprintEndpoint(currentTeam);
    fetch(`${workflowEndpoint}?sprintName=${encodeURIComponent(sprintName)}`)
        .then(r => r.json())
        .then(summary => {
            currentSprintData.summary = summary;
            currentSprintData.users = summary.users || [];
            currentSprintName = sprintName;
            populateUserDropdown(summary.users || []);
            let restored = false;
            if (previousSelectedUser &&
                previousSelectedUser !== "Select User") {
                const newDropdown =
                    document.getElementById("userDropdown");
                for (let i = 0; i < newDropdown.options.length; i++) {
                    if (newDropdown.options[i].text === previousSelectedUser) {
                        newDropdown.selectedIndex = i;
                        restored = true;
                        break;
                    }
                }
            }
            const table = document.getElementById("userTable");
            table.style.opacity = "0";
            table.style.transform = "translateY(10px)";
            if (restored) {
                onUserChange();
            } else {
                clearAndHideWorkflowGrid();
                renderTeamKPIs(sprintName);
                document.getElementById("kpiSection").classList.remove("hidden");
            }
            setTimeout(() => {
                table.style.transition = "all 0.25s ease";
                table.style.opacity = "1";
                table.style.transform = "translateY(0)";
            }, 50);
            document.querySelectorAll(".sprint-tab").forEach(tab => {
                tab.classList.remove("active");
                if (tab.innerText.trim() === sprintName) {
                    tab.classList.add("active");
                }
            });
            if (callback) {
                callback();
            }
        })
        .catch(err => {
            console.error("Sprint load failed", err);
        });
}

function openTeam(team) {
    window.currentTeam = team;
    updateUI({ team: team });
    clearAndHideWorkflowGrid();
    setPageTitleText(`Sprint Insights - ${team}`);
    renderSprintTabs(team);
    const summary = globalTeamMap[team]?.summary;
    const users = globalTeamMap[team]?.users || [];
    currentSprintData.summary = summary;
    currentSprintData.users = users;
    const project = mapTeamToProject(team);
    currentSprintName = sprintInfo[project]?.name || "";
    populateUserDropdown(users);
    //renderTeamWorkflow(summary);
    renderTeamKPIs(currentSprintName);
    const dropdown = document.getElementById("userDropdown");
    const kpiSection = document.getElementById("kpiSection");
    if (dropdown.value === "") {
        kpiSection.classList.remove("hidden");
    }
}

function populateUserDropdown(users) {
    const dropdown = document.getElementById("userDropdown");
    dropdown.innerHTML = `
        <option value="">Select User</option>
    `;
    users.forEach((u, index) => {
        dropdown.innerHTML += `
            <option value="${index}">
                ${u.name}
            </option>
        `;
    });
}

function onUserChange() {
    const dropdown = document.getElementById("userDropdown");
    const selectedIndex = dropdown.value;
    const currentTeam = window.currentTeam;
    const downloadBtn = document.getElementById("downloadBtn");
    const kpiSection = document.getElementById("kpiSection");
    if (!currentTeam) return;
    if (selectedIndex === "") {
        clearAndHideWorkflowGrid();
        renderTeamKPIs(currentSprintName);
        kpiSection.classList.remove("hidden");
        return;
    } else {
        kpiSection.classList.add("hidden");
    }
    const user = currentSprintData.users[selectedIndex];
    showWorkflowGrid();
    renderUserWorkflow(user);
}

function goBack() {
    resetSearch();
    document.querySelector(".user-filter").style.display = "block";
    setExecutionInsightsTitleVisible(true);
    window.currentTeam = null;
    document.getElementById("selectedProjectContext").classList.remove("hidden");
    updateUI(null);
    document.getElementById("tiles").style.display = "grid";
    document.getElementById("userSection").classList.remove("active");
    currentSprintData = { summary: null, users: [] };
    currentSprintName = "";
    // Reset dropdown to "Select User" to ensure KPIs are shown next time
    document.getElementById("userDropdown").selectedIndex = 0;
    // Reset page title to original
    setPageTitleText("Shared metrics. Smarter sprints");
    const adminStatusIcon = document.getElementById("adminStatusIcon");
    if (adminStatusIcon.getAttribute("aria-hidden") == "true") {
        hideUserDetails();
    } else if (adminStatusIcon.getAttribute("aria-hidden") == "false") {
        showUserDetails();
    }
}

function updateUI(state) {
    const controls = document.getElementById("controls");
    const userSection = document.getElementById("userSection");
    const overviewSection = document.getElementById("overviewSection");
    if (!state) {
        overviewSection.classList.toggle("hidden", isBellMediaQaSelected);
        document.getElementById("tiles").style.display = isBellMediaQaSelected ? "grid" : "none";
        userSection.classList.remove("active");
        controls.classList.toggle("hidden", !isBellMediaQaSelected);
        const adminStatusIcon = document.getElementById("adminStatusIcon");
        if (adminStatusIcon.getAttribute("aria-hidden") !== "false") {
            hideUserDetails();
        }
    } else if (state.team) {
        document.getElementById("tiles").style.display = "none";
        userSection.classList.add("active");
        controls.classList.add("hidden");
    }
}

function hideUserDetails() {
    const searchTab = document.getElementById("search");
    const userDrpDwn = document.getElementById("userDropdown");
    searchTab.classList.add("hidden");
    userDrpDwn.classList.add("hidden");
}

function showUserDetails() {
    const searchTab = document.getElementById("search");
    const userDrpDwn = document.getElementById("userDropdown");
    searchTab.classList.remove("hidden");
    userDrpDwn.classList.remove("hidden");
}

function enterAdminCred() {
    const adminPopup = document.getElementById("adminPopup");
    adminPopup.classList.remove("hidden");
}

function toggleHeroMenu() {
    const heroNavPanel = document.getElementById("heroNavPanel");
    const menuButton = document.querySelector(".hero-menu-button");
    const isOpen = menuButton.classList.toggle("is-open");
    heroNavPanel.classList.toggle("hidden", !isOpen);
    menuButton.setAttribute("aria-expanded", String(isOpen));
}

function toggleNavGroup(toggleBtn) {
    const group = toggleBtn.closest(".hero-nav-group");
    group.classList.toggle("is-expanded");
}

function validateCredentials() {
    const username = document.getElementById("username-btn");
    const password = document.getElementById("pwd-btn");
    const adminStatusIcon = document.getElementById("adminStatusIcon");
    if (username.value === "admin" && password.value === "admin123") {
        username.value = "";
        password.value = "";
        closeAdminPopup();
        showUserDetails();
        adminStatusIcon.classList.remove("hidden");
        adminStatusIcon.setAttribute("aria-hidden", "false");
    } else {
        alert("Invalid credentials. Please try again.");
    }
}

function closeAdminPopup() {
    const adminPopup = document.getElementById("adminPopup");
    adminPopup.classList.add("hidden");
}

function renderUsersOnly() {
    const searchText = document.getElementById("search").value.trim().toLowerCase();
    const searchSection = document.getElementById("searchSection");
    const resultsContainer = document.getElementById("searchResults");
    if (!searchText) {
        searchSection.classList.add("hidden");
        document.getElementById("tiles").style.display = "grid";
        return;
    }
    resultsContainer.innerHTML = "";
    searchSection.classList.remove("hidden");
    document.getElementById("tiles")
        .style.display = "none";
    let hasResults = false;
    Object.keys(globalTeamMap).forEach(team => {
        const users =
            globalTeamMap[team]?.users || [];
        users.forEach(user => {
            if (
                user.name &&
                user.name.toLowerCase().includes(searchText)
            ) {
                hasResults = true;
                const tile = document.createElement("div");
                tile.className = "tile";
                tile.innerHTML = `
                    <h4>${user.name}</h4>
                    <small>${team}</small>
                `;
                tile.addEventListener("click", function () {
                    selectUser(team, user.name);
                });
                resultsContainer.appendChild(tile);
            }
        });
    });
    if (!hasResults) {
        resultsContainer.innerHTML = `
            <div class="no-result">
                No matching users found
            </div>
        `;
    }
}

function clearMetricsView() {
    const workflowGrid = document.getElementById("workflowGrid");
    workflowGrid.innerHTML = `
        <div class="workflow-card">
            <div class="workflow-label">Loading</div>
            <div class="workflow-value">...</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">Loading</div>
            <div class="workflow-value">...</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">Loading</div>
            <div class="workflow-value">...</div>
        </div>
    `;
    renderLoadingKPIs();
}

function resetSearch(showTiles = true) {
    document.getElementById("search").value = "";
    document.getElementById("searchSection").classList.add("hidden");
    if (showTiles) {
        document.getElementById("tiles").style.display = "grid";
        renderTiles();
    }
}

function selectUser(team, userName) {
    document.getElementById("searchSection").classList.add("hidden");
    document.getElementById("searchResults").innerHTML = "";
    window.currentTeam = team;
    updateUI({ team: team });
    clearMetricsView();
    document.querySelector(".user-filter").style.display = "none";
    // Hide KPIs immediately when selecting a user
    document.getElementById("kpiSection").classList.add("hidden");
    // Set page title to user name
    setPageTitleText(`Sprint Insights - ${userName}`);
    renderSprintTabs(team);
    const project = mapTeamToProject(team);
    const latestSprint = sprintInfo[project]?.name;
    if (!latestSprint) {
        console.error("No latest sprint found");
        return;
    }
    loadSprintData(latestSprint, () => {
        const users = currentSprintData.users || [];
        const selectedUser = users.find(
            u => u.name.toLowerCase() === userName.toLowerCase()
        );
        if (!selectedUser) {
            return;
        }
        const dropdown = document.getElementById("userDropdown");
        users.forEach((u, index) => {
            if (u.name.toLowerCase() === userName.toLowerCase()) {
                dropdown.value = index;
            }
        });
        renderUserWorkflow(selectedUser);
        renderUserKPIs(selectedUser);
    });
}

function renderSprintTabs(team) {
    const sprintContainer = document.getElementById("sprintTabs");
    sprintContainer.innerHTML = "";
    const project = mapTeamToProject(team);
    const sprint = sprintInfo[project];
    if (!sprint) {
        return;
    }
    const tabs = [];
    if (sprint.name) {
        tabs.push({
            label: sprint.name,
            active: true
        });
    }
    if (sprint.history) {
        const historyList = sprint.history.split(",");
        historyList.forEach(name => {
            tabs.push({
                label: name.trim(),
                active: false
            });
        });
    }
    tabs.forEach(tab => {
        const div = document.createElement("div");
        div.className = `sprint-tab ${tab.active ? 'active' : ''}`;
        div.innerText = tab.label;
        div.addEventListener("click", function () {
            loadSprintData(tab.label);
        });
        sprintContainer.appendChild(div);
    });
}

function renderTeamWorkflow(summary) {
    setExecutionInsightsTitleVisible(false);
    const grid = document.getElementById("workflowGrid");
    grid.innerHTML = `
        <div class="workflow-card">
            <div class="workflow-label">Total</div>
            <div class="workflow-value">${summary.total || 0}</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">Todo</div>
            <div class="workflow-value">${summary.todo || 0}</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">In QA</div>
            <div class="workflow-value">${summary.inQa || 0}</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">Review</div>
            <div class="workflow-value">${summary.review || 0}</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">Blocked</div>
            <div class="workflow-value">${summary.blocked || 0}</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">Done</div>
            <div class="workflow-value">${summary.done || 0}</div>
        </div>
    `;
}

function renderUserWorkflow(user) {
    setExecutionInsightsTitleVisible(true);
    showWorkflowGrid();
    const grid = document.getElementById("workflowGrid");
    grid.innerHTML = `
        <div class="workflow-card">
            <div class="workflow-label">Total</div>
            <div class="workflow-value">${user.total || 0}</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">Todo</div>
            <div class="workflow-value">${user.todo || 0}</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">In QA</div>
            <div class="workflow-value">${user.inQa || 0}</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">Review</div>
            <div class="workflow-value">${user.review || 0}</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">Blocked</div>
            <div class="workflow-value">${user.blocked || 0}</div>
        </div>
        <div class="workflow-card">
            <div class="workflow-label">Done</div>
            <div class="workflow-value">${user.done || 0}</div>
        </div>
    `;
}

function getTeamKpiMarkup(kpi) {
    return `
        <div class="kpi-card">
            <div class="kpi-header">
                <div class="kpi-name">Defect Leakage Rate %</div>
                <div class="kpi-help">i
                    <div class="tooltip">Production defects / Total defects found * 100</div>
                </div>
            </div>
            <div class="kpi-value">${kpi.defectLeakageRate ?? "yet to implement"}</div>
        </div>
        <div class="kpi-card">
            <div class="kpi-header">
                <div class="kpi-name">Test Effectiveness (%)</div>
                <div class="kpi-help">i
                    <div class="tooltip">Defects found during testing / Total defects(Testing+production)*100</div>
                </div>
            </div>
            <div class="kpi-value">yet to implement</div>
        </div>
        <div class="kpi-card">
            <div class="kpi-header">
                <div class="kpi-name">Execution rate (%)</div>
                <div class="kpi-help">i
                    <div class="tooltip">Executed QA Subtasks / planned QA Subtasks * 100</div>
                </div>
            </div>
            <div class="kpi-value">${kpi.executionRate ?? "yet to implement"}</div>
        </div>
        <div class="kpi-card kpi-card-bottom-left">
            <div class="kpi-header">
                <div class="kpi-name">Automation Coverage (%)</div>
                <div class="kpi-help">i
                    <div class="tooltip">Automated Test Cases / Total Regression Test Cases * 100</div>
                </div>
            </div>
            <div class="kpi-value">${kpi.automationCoverage ?? "yet to implement"}</div>
        </div>
        <div class="kpi-card kpi-card-bottom-right">
            <div class="kpi-header">
                <div class="kpi-name">Automation Pass Rate (%)</div>
                <div class="kpi-help">i
                    <div class="tooltip">Passed Automation Tests / Executed Automation Tests * 100</div>
                </div>
            </div>
            <div class="kpi-value">${kpi.automationPassRate ?? "yet to implement"}</div>
        </div>
    `;
}

function formatPercentageValue(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
        return "--";
    }
    return `${Number(value).toFixed(2)}%`;
}

function getKpiSprintNames(selectedSprintName = currentSprintName) {
    const project = mapTeamToProject(window.currentTeam);
    const sprint = project ? sprintInfo[project] : null;
    if (!sprint) {
        return selectedSprintName ? [selectedSprintName] : [];
    }
    const candidates = [selectedSprintName, sprint.name];
    if (sprint.history) {
        sprint.history.split(",").forEach(name => candidates.push(name.trim()));
    }
    const unique = [];
    candidates.forEach(name => {
        if (!name) {
            return;
        }
        const cleaned = name.trim();
        if (!cleaned) {
            return;
        }
        if (!unique.includes(cleaned)) {
            unique.push(cleaned);
        }
    });
    return unique.slice(0, 3);
}

function renderLoadingKPIs() {
    const kpiGrid = document.getElementById("kpiGrid");
    kpiGrid.innerHTML = getTeamKpiMarkup({ defectLeakageRate: "...", executionRate: "...", automationCoverage: "...", automationPassRate: "..." });
}

function renderTeamKPIs(sprintName = currentSprintName) {
    const kpiGrid = document.getElementById("kpiGrid");
    const currentTeam = window.currentTeam;
    const currentProjectKey = mapTeamToProject(currentTeam);
    if (!currentTeam) {
        kpiGrid.innerHTML = getTeamKpiMarkup({ defectLeakageRate: "--", executionRate: "--", automationCoverage: "--", automationPassRate: "--" });
        return;
    }

    const automationCoverage = getAutomationCoverageDisplayForTeam(currentTeam);
    const automationPassRate = getAutomationPassRateDisplayForTeam(currentTeam);

    kpiGrid.innerHTML = getTeamKpiMarkup({ defectLeakageRate: "...", executionRate: "...", automationCoverage, automationPassRate });
    if (!sprintName) {
        kpiGrid.innerHTML = getTeamKpiMarkup({ defectLeakageRate: "--", executionRate: "--", automationCoverage, automationPassRate });
        return;
    }

    if (currentProjectKey === "BMARC") {
        Promise.all([
            fetch(`/arcxpDefectLeakage?sprint=${encodeURIComponent(sprintName)}`).then(r => {
                if (!r.ok) {
                    throw new Error(`Request failed: ${r.status}`);
                }
                return r.json();
            }),
            fetch(`/arcxpExecutionRate?sprint=${encodeURIComponent(sprintName)}`).then(r => {
                if (!r.ok) {
                    throw new Error(`Request failed: ${r.status}`);
                }
                return r.json();
            })
        ])
            .then(([leakageKpi, executionRateKpi]) => {
                kpiGrid.innerHTML = getTeamKpiMarkup({
                    defectLeakageRate: formatPercentageValue(leakageKpi?.defectLeakageRatePercent),
                    executionRate: formatPercentageValue(executionRateKpi?.executionRatePercent),
                    automationCoverage,
                    automationPassRate
                });
            })
            .catch(err => {
                console.error("Arc Xp KPI load failed", err);
                kpiGrid.innerHTML = getTeamKpiMarkup({ defectLeakageRate: "--", executionRate: "--", automationCoverage, automationPassRate });
            });
        return;
    }

    if (currentProjectKey !== "RDSDEV") {
        kpiGrid.innerHTML = getTeamKpiMarkup({ defectLeakageRate: "yet to implement", executionRate: "yet to implement", automationCoverage, automationPassRate });
        return;
    }

    Promise.all([
        fetch(`/datahubDefectLeakage?sprint=${encodeURIComponent(sprintName)}`).then(r => {
            if (!r.ok) {
                throw new Error(`Request failed: ${r.status}`);
            }
            return r.json();
        }),
        fetch(`/datahubExecutionRate?sprint=${encodeURIComponent(sprintName)}`).then(r => {
            if (!r.ok) {
                throw new Error(`Request failed: ${r.status}`);
            }
            return r.json();
        })
    ])
        .then(([leakageKpi, executionRateKpi]) => {
            kpiGrid.innerHTML = getTeamKpiMarkup({
                defectLeakageRate: formatPercentageValue(leakageKpi?.defectLeakageRatePercent),
                executionRate: formatPercentageValue(executionRateKpi?.executionRatePercent),
                automationCoverage,
                automationPassRate
            });
        })
        .catch(err => {
            console.error("Data Hub KPI load failed", err);
            kpiGrid.innerHTML = getTeamKpiMarkup({ defectLeakageRate: "--", executionRate: "--", automationCoverage, automationPassRate });
        });
}

function renderUserKPIs(user) {
    // Hide KPI section when viewing individual user metrics
    const kpiSection = document.getElementById("kpiSection");
    kpiSection.classList.add("hidden");
}

function downloadSprintReport() {
    const dropdown = document.getElementById("userDropdown");
    const selectedIndex = dropdown.value;
    const selectedText = dropdown.options[dropdown.selectedIndex]?.text;
    const activeSprint = document.querySelector(".sprint-tab.active")?.innerText?.trim();
    const currentTeam = window.currentTeam;
    let type = "team";
    let url =
        `/downloadReport?team=${encodeURIComponent(currentTeam)}`
        + `&sprint=${encodeURIComponent(activeSprint)}`;
    if (selectedIndex !== "") {
        type = "user";
        url += `&user=${encodeURIComponent(selectedText)}`;
    }
    url += `&type=${type}`;
    window.location.href = url;
}

initializeApp();
