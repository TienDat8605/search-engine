const searchForm = document.getElementById("searchForm");
const queryInput = document.getElementById("queryInput");
const sortSelect = document.getElementById("sortSelect");
const tagsInput = document.getElementById("tagsInput");
const limitInput = document.getElementById("limitInput");
const statusArea = document.getElementById("statusArea");
const resultsArea = document.getElementById("results");
const searchButton = document.getElementById("searchButton");
const resultCardTemplate = document.getElementById("resultCardTemplate");
const toolbar = document.getElementById("toolbar");
const queryMeta = document.getElementById("queryMeta");
const prevButton = document.getElementById("prevButton");
const nextButton = document.getElementById("nextButton");
const pageLabel = document.getElementById("pageLabel");
const metricTotalQueries = document.getElementById("metricTotalQueries");
const metricQueries24h = document.getElementById("metricQueries24h");
const metricTotalDocs = document.getElementById("metricTotalDocs");
const topQueriesList = document.getElementById("topQueriesList");
const analyticsHint = document.getElementById("analyticsHint");
const docPanel = document.getElementById("docPanel");
const docTitle = document.getElementById("docTitle");
const docMeta = document.getElementById("docMeta");
const docQuestion = document.getElementById("docQuestion");
const docAnswer = document.getElementById("docAnswer");
const docSourceLink = document.getElementById("docSourceLink");
const docCloseButton = document.getElementById("docCloseButton");

let currentOffset = 0;
let lastPayload = null;

initializeFromUrl();

searchForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    currentOffset = 0;
    await runSearch();
});

prevButton.addEventListener("click", async () => {
    if (currentOffset <= 0) {
        return;
    }
    currentOffset = Math.max(0, currentOffset - getLimit());
    await runSearch();
});

nextButton.addEventListener("click", async () => {
    currentOffset += getLimit();
    await runSearch();
});

window.addEventListener("popstate", () => {
    initializeFromUrl();
    runSearch();
});

docCloseButton.addEventListener("click", () => {
    docPanel.hidden = true;
});

async function runSearch() {
    const query = queryInput.value.trim();
    if (!query) {
        setStatus("Please enter a search query.", true);
        return;
    }

    const params = new URLSearchParams({
        q: query,
        sort: sortSelect.value,
        limit: getLimit().toString(),
        offset: currentOffset.toString()
    });

    const tags = tagsInput.value.trim();
    if (tags) {
        params.set("tags", tags);
    }

    setLoading(true);
    renderSkeletons();
    updatePager(0);
    updateUrl(params);

    try {
        const response = await fetch(`/api/search?${params.toString()}`);
        const payload = await response.json();

        if (!response.ok) {
            throw new Error(extractErrorMessage(payload, response.status));
        }

        lastPayload = payload;
        renderResults(payload);
        refreshAnalytics();
    } catch (error) {
        lastPayload = null;
        toolbar.hidden = true;
        resultsArea.innerHTML = "";
        setStatus(error.message || "Something went wrong.", true);
    } finally {
        setLoading(false);
    }
}

async function refreshAnalytics() {
    try {
        const response = await fetch("/api/analytics");
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(extractErrorMessage(payload, response.status));
        }
        renderAnalytics(payload);
    } catch (_error) {
        analyticsHint.textContent = "Analytics unavailable right now.";
    }
}

function renderAnalytics(payload) {
    metricTotalQueries.textContent = formatNumber(payload.totalQueries);
    metricQueries24h.textContent = formatNumber(payload.queriesLast24Hours);
    metricTotalDocs.textContent = formatNumber(payload.totalDocuments);

    topQueriesList.innerHTML = "";
    const top = Array.isArray(payload.topQueries) ? payload.topQueries : [];
    if (!top.length) {
        analyticsHint.textContent = "No query logs yet.";
        return;
    }

    analyticsHint.textContent = "";
    for (const row of top.slice(0, 8)) {
        const li = document.createElement("li");
        li.textContent = `${row.query} (${row.hits})`;
        topQueriesList.appendChild(li);
    }
}

function renderResults(payload) {
    const items = payload.items || [];
    resultsArea.innerHTML = "";
    toolbar.hidden = false;

    const limit = Number.isInteger(payload.limit) ? payload.limit : getLimit();
    currentOffset = Number.isInteger(payload.offset) ? Math.max(0, payload.offset) : currentOffset;
    const page = Math.floor(currentOffset / Math.max(1, limit)) + 1;
    queryMeta.textContent = `Query: ${payload.query} • Sort: ${payload.sort} • Tags: ${(payload.tags || []).join(", ") || "none"}`;
    updatePager(page);

    if (!items.length) {
        setStatus("No results found. Try changing keywords, sort, or tags.");
        nextButton.disabled = true;
        return;
    }

    setStatus(`Found ${payload.total} result${payload.total === 1 ? "" : "s"}.`);
    prevButton.disabled = currentOffset === 0;
    nextButton.disabled = !payload.hasMore;

    for (const item of items) {
        const fragment = resultCardTemplate.content.cloneNode(true);
        const title = fragment.querySelector(".result-title");
        const acceptedBadge = fragment.querySelector(".badge.accepted");
        const answeredBadge = fragment.querySelector(".badge.answered");
        const meta = fragment.querySelector(".meta");
        const snippet = fragment.querySelector(".snippet");
        const tags = fragment.querySelector(".tags");
        const detailButton = fragment.querySelector(".detail-button");

        title.textContent = item.title;
        title.href = item.link;

        const tagText = (item.tags || []).slice(0, 8).map((tag) => `#${tag}`).join(" ");
        meta.textContent = `Score: ${item.questionScore} • Rank: ${item.score.toFixed(2)} • Source: ${item.source}${tagText ? ` • ${tagText}` : ""}`;

        snippet.textContent = item.snippet || "No snippet available.";

        if (item.accepted) {
            acceptedBadge.hidden = false;
        }
        if (item.answered) {
            answeredBadge.hidden = false;
        }

        for (const tag of item.tags || []) {
            const chip = document.createElement("span");
            chip.className = "tag";
            chip.textContent = tag;
            tags.appendChild(chip);
        }

        if (Number.isInteger(item.questionId) || Number.isFinite(item.questionId)) {
            detailButton.addEventListener("click", () => {
                loadDocumentDetail(item.questionId);
            });
        } else {
            detailButton.disabled = true;
            detailButton.textContent = "Detail unavailable";
        }

        resultsArea.appendChild(fragment);
    }
}

async function loadDocumentDetail(questionId) {
    if (!questionId) {
        return;
    }

    docPanel.hidden = false;
    docTitle.textContent = "Loading document...";
    docMeta.textContent = "";
    docQuestion.textContent = "Loading...";
    docAnswer.textContent = "Loading...";
    docSourceLink.textContent = "Open on Stack Overflow";
    docSourceLink.href = "#";

    try {
        const response = await fetch(`/api/doc/${encodeURIComponent(questionId)}`);
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(extractErrorMessage(payload, response.status));
        }

        docTitle.textContent = payload.title || `Question #${questionId}`;
        docMeta.textContent = `Question ID: ${payload.questionId} • Source: ${payload.source} • Tags: ${(payload.tags || []).join(", ") || "none"}`;
        docQuestion.textContent = payload.questionText || "Question text not enriched yet.";
        docAnswer.textContent = payload.bestAnswerText || "Best answer text not enriched yet.";
        docSourceLink.textContent = "Open on Stack Overflow";
        docSourceLink.href = payload.url || "#";
    } catch (error) {
        docTitle.textContent = "Document detail unavailable";
        docMeta.textContent = "";
        docQuestion.textContent = error.message || "Unable to load document detail.";
        docAnswer.textContent = "";
        docSourceLink.textContent = "";
        docSourceLink.removeAttribute("href");
    }
}

function renderSkeletons() {
    const count = 3;
    resultsArea.innerHTML = "";
    for (let index = 0; index < count; index += 1) {
        const block = document.createElement("div");
        block.className = "skeleton";
        resultsArea.appendChild(block);
    }
}

function setStatus(message, isError = false) {
    statusArea.textContent = message;
    statusArea.classList.toggle("error", isError);
}

function setLoading(value) {
    searchButton.disabled = value;
    prevButton.disabled = value;
    nextButton.disabled = value;
    searchButton.textContent = value ? "Searching..." : "Search";
}

function clampLimit(value) {
    const parsed = Number.parseInt(value, 10);
    if (Number.isNaN(parsed)) {
        return 10;
    }
    return Math.min(50, Math.max(1, parsed));
}

function getLimit() {
    const value = clampLimit(limitInput.value);
    limitInput.value = String(value);
    return value;
}

function initializeFromUrl() {
    const params = new URLSearchParams(window.location.search);
    const query = params.get("q") || "spring boot dependency injection error";
    const sort = params.get("sort") || "relevance";
    const tags = params.get("tags") || "";
    const limit = clampLimit(params.get("limit") || "10");
    const offset = Number.parseInt(params.get("offset") || "0", 10);

    queryInput.value = query;
    sortSelect.value = sort === "new" ? "new" : "relevance";
    tagsInput.value = tags;
    limitInput.value = String(limit);
    currentOffset = Number.isNaN(offset) ? 0 : Math.max(0, offset);
}

function updateUrl(params) {
    const url = `${window.location.pathname}?${params.toString()}`;
    window.history.replaceState({}, "", url);
}

function updatePager(pageNumber) {
    pageLabel.textContent = `Page ${Math.max(1, pageNumber)}`;
}

function extractErrorMessage(payload, status) {
    if (payload && typeof payload === "object") {
        if (payload.message) {
            return payload.message;
        }
        if (payload.error) {
            return `${payload.error} (${status})`;
        }
    }
    return `Search request failed (${status}).`;
}

function formatNumber(value) {
    const n = Number(value);
    if (Number.isNaN(n)) {
        return "-";
    }
    return new Intl.NumberFormat().format(n);
}

runSearch();
refreshAnalytics();
