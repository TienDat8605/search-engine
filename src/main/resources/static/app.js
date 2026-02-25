// ── Constants ─────────────────────────────────────────────────────────────────
const BATCH_SIZE      = 50;          // items fetched from API per request
const PAGE_SIZE       = 10;          // items displayed per page
const PAGES_PER_BATCH = BATCH_SIZE / PAGE_SIZE; // 5

// ── DOM refs ──────────────────────────────────────────────────────────────────
const searchForm         = document.getElementById("searchForm");
const queryInput         = document.getElementById("queryInput");
const sortSelect         = document.getElementById("sortSelect");
const tagsInput          = document.getElementById("tagsInput");
const statusArea         = document.getElementById("statusArea");
const resultsArea        = document.getElementById("results");
const searchButton       = document.getElementById("searchButton");
const resultCardTemplate = document.getElementById("resultCardTemplate");
const queryMeta          = document.getElementById("queryMeta");
const resultsHeader      = document.getElementById("resultsHeader");
const pager              = document.getElementById("pager");
const aiOverview         = document.getElementById("ai-overview");
const aiOverviewContent  = document.getElementById("ai-overview-content");
const aiCitations        = document.getElementById("ai-citations");
const docPanel           = document.getElementById("docPanel");
const docTitle           = document.getElementById("docTitle");
const docMeta            = document.getElementById("docMeta");
const docQuestion        = document.getElementById("docQuestion");
const docAnswer          = document.getElementById("docAnswer");
const docSourceLink      = document.getElementById("docSourceLink");
const docCloseButton     = document.getElementById("docCloseButton");
const themeToggle        = document.getElementById("themeToggle");

// ── Pagination state ──────────────────────────────────────────────────────────
let currentPage      = 1;   // current display page (1-indexed)
let batchStartPage   = 1;   // first page number of the loaded batch
let batchItems       = [];  // up to BATCH_SIZE items in memory
let hasMoreFromServer = false;
let activeQuery      = { q: "", sort: "relevance", tags: "" };

// ── Dark mode ─────────────────────────────────────────────────────────────────
function initTheme() {
    const saved       = localStorage.getItem("theme");
    const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
    const theme       = saved || (prefersDark ? "dark" : "light");
    document.documentElement.setAttribute("data-theme", theme);
}

initTheme();

themeToggle.addEventListener("click", () => {
    const next = document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", next);
    localStorage.setItem("theme", next);
});

// ── Startup ───────────────────────────────────────────────────────────────────
pager.hidden         = true;
resultsHeader.hidden = true;
resultsArea.innerHTML = "";
setStatus("");

initSearchFromUrl();

searchForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    await runSearch();
});

window.addEventListener("popstate", () => {
    initSearchFromUrl();
});

docCloseButton.addEventListener("click", () => {
    docPanel.hidden = true;
});

// ── Search ────────────────────────────────────────────────────────────────────
async function runSearch() {
    const q = queryInput.value.trim();
    if (!q) {
        setStatus("Please enter a search query.", true);
        return;
    }

    activeQuery = {
        q,
        sort: sortSelect.value,
        tags: tagsInput.value.trim(),
    };

    currentPage       = 1;
    batchStartPage    = 1;
    batchItems        = [];
    hasMoreFromServer = false;
    resultsHeader.hidden = true;
    pager.hidden         = true;
    hideAiOverview();
    showAiSkeleton();

    const aiPromise = fetchAiOverview(q);
    await fetchBatch(1);

    if (batchItems.length > 0) {
        showCurrentPage();
    }

    await aiPromise;
}

async function initSearchFromUrl() {
    const params = new URLSearchParams(window.location.search);
    const q      = params.get("q")    || "";
    const sort   = params.get("sort") || "relevance";
    const tags   = params.get("tags") || "";
    const page   = Math.max(1, parseInt(params.get("page") || "1", 10));

    queryInput.value = q;
    sortSelect.value = sort === "new" ? "new" : "relevance";
    tagsInput.value  = tags;

    if (!q.trim()) {
        pager.hidden         = true;
        resultsHeader.hidden = true;
        setStatus("");
        return;
    }

    activeQuery = { q: q.trim(), sort: sortSelect.value, tags: tags.trim() };

    const targetBatchStart = getBatchStart(page);
    await fetchBatch(targetBatchStart);

    if (batchItems.length > 0) {
        currentPage = Math.min(page, batchStartPage + Math.ceil(batchItems.length / PAGE_SIZE) - 1);
        showCurrentPage();
    }

    fetchAiOverview(q);
}

// ── Batch fetching ────────────────────────────────────────────────────────────
async function fetchBatch(newBatchStart) {
    const batchIndex   = Math.floor((newBatchStart - 1) / PAGES_PER_BATCH);
    const serverOffset = batchIndex * BATCH_SIZE;

    const params = new URLSearchParams({
        q:      activeQuery.q,
        sort:   activeQuery.sort,
        limit:  String(BATCH_SIZE),
        offset: String(serverOffset),
    });
    if (activeQuery.tags) params.set("tags", activeQuery.tags);

    setLoading(true);
    renderSkeletons();
    pager.hidden         = true;
    resultsHeader.hidden = true;

    const startTime = Date.now();
    try {
        const response = await fetch(`/api/search?${params}`);
        const payload  = await response.json();
        const elapsed  = Date.now() - startTime;

        if (!response.ok) throw new Error(extractErrorMessage(payload, response.status));

        batchItems        = payload.items || [];
        hasMoreFromServer = typeof payload.providerHasMore === "boolean"
            ? payload.providerHasMore
            : Boolean(payload.hasMore);
        batchStartPage    = newBatchStart;

        const total = payload.total ?? batchItems.length;
        setStatus(
            batchItems.length
                ? `About ${total.toLocaleString()} result${total === 1 ? "" : "s"} · ${elapsed}ms`
                : "No results found. Try different keywords or filters."
        );

        queryMeta.textContent =
            `Query: ${payload.query} · Sort: ${payload.sort}` +
            (activeQuery.tags ? ` · Tags: ${activeQuery.tags}` : "");
        resultsHeader.hidden = false;

    } catch (err) {
        batchItems           = [];
        resultsHeader.hidden = true;
        pager.hidden         = true;
        resultsArea.innerHTML = "";
        setStatus(err.message || "Something went wrong.", true);
    } finally {
        setLoading(false);
    }
}

// ── Page navigation ───────────────────────────────────────────────────────────
function getBatchStart(pageNum) {
    const batchIndex = Math.floor((pageNum - 1) / PAGES_PER_BATCH);
    return batchIndex * PAGES_PER_BATCH + 1;
}

async function goToPage(pageNum) {
    const targetBatchStart = getBatchStart(pageNum);
    if (targetBatchStart !== batchStartPage) {
        await fetchBatch(targetBatchStart);
    }
    currentPage = pageNum;
    showCurrentPage();
    resultsArea.scrollIntoView({ behavior: "smooth", block: "start" });
}

function showCurrentPage() {
    resultsArea.innerHTML = "";

    const startIdx  = (currentPage - batchStartPage) * PAGE_SIZE;
    const pageItems = batchItems.slice(startIdx, startIdx + PAGE_SIZE);

    if (pageItems.length === 0) {
        renderPager();
        return;
    }

    let globalPosition = (currentPage - 1) * PAGE_SIZE;
    for (const item of pageItems) {
        globalPosition += 1;
        resultsArea.appendChild(buildCard(item, globalPosition));
    }

    renderPager();
    updateUrl();
}

// ── Pager rendering ───────────────────────────────────────────────────────────
function renderPager() {
    pager.innerHTML = "";

    const pagesInBatch  = Math.ceil(batchItems.length / PAGE_SIZE);
    const lastKnownPage = batchStartPage + pagesInBatch - 1;
    const hasPages      = pagesInBatch > 0;

    if (!hasPages && !hasMoreFromServer) {
        pager.hidden = true;
        return;
    }

    pager.hidden = false;

    const hasPrev = currentPage > 1;
    const hasNext = currentPage < lastKnownPage || hasMoreFromServer;

    // ‹ Previous
    pager.appendChild(
        makePagerBtn("‹", hasPrev, () => goToPage(currentPage - 1), "Previous page", "pager-arrow")
    );

    // Page number buttons for all pages in the current batch
    for (let p = batchStartPage; p <= lastKnownPage; p++) {
        const page = p; // capture for closure
        const btn  = makePagerBtn(String(page), true, () => goToPage(page));
        if (page === currentPage) {
            btn.classList.add("pager-active");
            btn.setAttribute("aria-current", "page");
        }
        pager.appendChild(btn);
    }

    // Ellipsis when server has more results beyond this batch
    if (hasMoreFromServer) {
        const ellipsis   = document.createElement("span");
        ellipsis.className  = "pager-ellipsis";
        ellipsis.textContent = "…";
        pager.appendChild(ellipsis);
    }

    // › Next
    pager.appendChild(
        makePagerBtn("›", hasNext, () => goToPage(currentPage + 1), "Next page", "pager-arrow")
    );
}

function makePagerBtn(text, enabled, onClick, ariaLabel, extraClass) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = text;
    if (ariaLabel)  btn.setAttribute("aria-label", ariaLabel);
    if (extraClass) btn.classList.add(extraClass);
    btn.disabled = !enabled;
    if (enabled) btn.addEventListener("click", onClick);
    return btn;
}

// ── Card rendering ────────────────────────────────────────────────────────────
function buildCard(item, position) {
    const fragment      = resultCardTemplate.content.cloneNode(true);
    const card          = fragment.querySelector("article");
    const title         = fragment.querySelector(".result-title");
    const acceptedBadge = fragment.querySelector(".badge.accepted");
    const answeredBadge = fragment.querySelector(".badge.answered");
    const meta          = fragment.querySelector(".meta");
    const snippet       = fragment.querySelector(".snippet");
    const tagsDiv       = fragment.querySelector(".tags");

    if (card && (Number.isInteger(item.questionId) || Number.isFinite(item.questionId))) {
        card.id = `q-${item.questionId}`;
    }

    title.textContent = item.title;
    title.href        = item.link;
    title.addEventListener("click", () => sendClickBeacon(activeQuery.q, item.link, position));

    meta.textContent    = `Score: ${item.questionScore}`;
    snippet.textContent = item.snippet || "No snippet available.";

    if (item.accepted) acceptedBadge.hidden = false;
    if (item.answered) answeredBadge.hidden = false;

    for (const tag of item.tags || []) {
        const chip      = document.createElement("span");
        chip.className  = "tag";
        chip.textContent = tag;
        tagsDiv.appendChild(chip);
    }

    return fragment;
}

// ── AI Overview ───────────────────────────────────────────────────────────────
async function fetchAiOverview(query) {
    try {
        const response = await fetch(`/api/ask?q=${encodeURIComponent(query)}`);
        if (!response.ok) { hideAiOverview(); return; }
        const payload = await response.json();
        renderAiOverview(payload);
    } catch {
        hideAiOverview();
    }
}

function showAiSkeleton() {
    aiOverview.hidden         = false;
    aiOverviewContent.innerHTML = '<div class="skeleton ai-skeleton"></div>';
    aiCitations.hidden        = true;
    aiCitations.innerHTML     = "";
}

function hideAiOverview() {
    aiOverview.hidden         = true;
    aiOverviewContent.innerHTML = "";
    aiCitations.hidden        = true;
    aiCitations.innerHTML     = "";
}

function escapeHtml(text) {
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}

function formatAiMarkdown(raw, citationMap) {
    const codeBlocks = [];

    const withPlaceholders = raw.replace(/```(\w*)\n([\s\S]*?)```/g, (_, lang, code) => {
        const idx      = codeBlocks.length;
        const langAttr = lang ? ` data-lang="${escapeHtml(lang)}"` : "";
        codeBlocks.push(
            `<pre class="ai-code-block"><div class="ai-code-header">${escapeHtml(lang || "code")}</div>` +
            `<code${langAttr}>${escapeHtml(code.replace(/\n$/, ""))}</code></pre>`
        );
        return `\x00CODEBLOCK_${idx}\x00`;
    });

    let html = escapeHtml(withPlaceholders);
    html = html.replace(/`([^`]+)`/g, '<code class="ai-inline-code">$1</code>');
    html = html.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
    html = html.replace(/\[SO-(\d+)\]/g, (_, num) => {
        const c = citationMap[Number.parseInt(num, 10)];
        if (!c) return "";
        const safeTitle = (c.title || "").replace(/"/g, "&quot;");
        const safeUrl   = (c.url   || "#").replace(/"/g, "&quot;");
        return `<a href="${safeUrl}" target="_blank" rel="noopener noreferrer" class="ai-ref" data-tooltip="${safeTitle}"><sup>${num}</sup></a>`;
    });
    html = html.replace(/\n/g, "<br>");
    html = html.replace(/\x00CODEBLOCK_(\d+)\x00/g, (_, idx) => codeBlocks[Number.parseInt(idx, 10)]);
    return html;
}

function renderAiOverview(payload) {
    if (!payload?.overview) { hideAiOverview(); return; }

    aiOverview.hidden = false;
    const citationMap = {};
    for (const c of payload.citations || []) citationMap[c.index] = c;
    aiOverviewContent.innerHTML = formatAiMarkdown(payload.overview, citationMap);
    aiCitations.hidden  = true;
    aiCitations.innerHTML = "";
}

// ── Document detail ───────────────────────────────────────────────────────────
async function loadDocumentDetail(questionId) {
    if (!questionId) return;

    docPanel.hidden        = false;
    docTitle.textContent   = "Loading document…";
    docMeta.textContent    = "";
    docQuestion.textContent = "Loading…";
    docAnswer.textContent  = "Loading…";
    docSourceLink.textContent = "Open on Stack Overflow";
    docSourceLink.href     = "#";
    removeRelatedQuestions();

    const docFetch     = fetch(`/api/doc/${encodeURIComponent(questionId)}`);
    const similarFetch = fetch(`/api/similar/${encodeURIComponent(questionId)}`).catch(() => null);

    let docOk = false;
    try {
        const response = await docFetch;
        const payload  = await response.json();
        if (!response.ok) throw new Error(extractErrorMessage(payload, response.status));

        docTitle.textContent       = payload.title || `Question #${questionId}`;
        docMeta.textContent        = `Question ID: ${payload.questionId} · Source: ${payload.source} · Tags: ${(payload.tags || []).join(", ") || "none"}`;
        docQuestion.textContent    = payload.questionText   || "Question text not enriched yet.";
        docAnswer.textContent      = payload.bestAnswerText || "Best answer text not enriched yet.";
        docSourceLink.textContent  = "Open on Stack Overflow";
        docSourceLink.href         = payload.url || "#";
        docOk = true;
    } catch (err) {
        docTitle.textContent = "Document detail unavailable";
        docMeta.textContent  = "";
        docQuestion.textContent = err.message || "Unable to load document detail.";
        docAnswer.textContent   = "";
        docSourceLink.textContent = "";
        docSourceLink.removeAttribute("href");
    }

    if (docOk) {
        try {
            const similarResp = await similarFetch;
            if (similarResp?.ok) {
                const similar = await similarResp.json();
                if (Array.isArray(similar) && similar.length > 0) {
                    renderRelatedQuestions(similar.slice(0, 5));
                }
            }
        } catch { /* optional — degrade gracefully */ }
    }
}

function renderRelatedQuestions(items) {
    const section   = document.createElement("section");
    section.className = "related-questions";

    const heading = document.createElement("h4");
    heading.textContent = "Related Questions";
    section.appendChild(heading);

    for (const q of items) {
        const card = document.createElement("div");
        card.className = "related-question-card";

        const link   = document.createElement("a");
        link.href    = q.link || "#";
        link.textContent = q.title || `Question #${q.questionId}`;
        link.target  = "_blank";
        link.rel     = "noopener noreferrer";
        card.appendChild(link);

        if (q.tags?.length) {
            const tagsDiv   = document.createElement("div");
            tagsDiv.className = "tags";
            for (const tag of q.tags) {
                const chip      = document.createElement("span");
                chip.className  = "tag";
                chip.textContent = tag;
                tagsDiv.appendChild(chip);
            }
            card.appendChild(tagsDiv);
        }

        section.appendChild(card);
    }

    docPanel.appendChild(section);
}

function removeRelatedQuestions() {
    docPanel.querySelector(".related-questions")?.remove();
}

// ── Utilities ─────────────────────────────────────────────────────────────────
function sendClickBeacon(query, url, position) {
    if (!navigator.sendBeacon) return;
    navigator.sendBeacon(
        "/api/events/click",
        new Blob([JSON.stringify({ query, url, position })], { type: "application/json" })
    );
}

function renderSkeletons() {
    resultsArea.innerHTML = "";
    for (let i = 0; i < 5; i++) {
        const div       = document.createElement("div");
        div.className   = "skeleton";
        resultsArea.appendChild(div);
    }
}

function setStatus(message, isError = false) {
    statusArea.textContent = message;
    statusArea.classList.toggle("error", isError);
}

function setLoading(active) {
    searchButton.disabled     = active;
    searchButton.textContent  = active ? "Searching…" : "Search";
}

function updateUrl() {
    const params = new URLSearchParams({
        q:    activeQuery.q,
        sort: activeQuery.sort,
        page: String(currentPage),
    });
    if (activeQuery.tags) params.set("tags", activeQuery.tags);
    window.history.replaceState({}, "", `${window.location.pathname}?${params}`);
}

function extractErrorMessage(payload, status) {
    if (payload && typeof payload === "object") {
        if (payload.message) return payload.message;
        if (payload.error)   return `${payload.error} (${status})`;
    }
    return `Search request failed (${status}).`;
}
