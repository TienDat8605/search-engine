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
const aiOverview = document.getElementById("ai-overview");
const aiOverviewContent = document.getElementById("ai-overview-content");
const aiCitations = document.getElementById("ai-citations");
const docPanel = document.getElementById("docPanel");
const docTitle = document.getElementById("docTitle");
const docMeta = document.getElementById("docMeta");
const docQuestion = document.getElementById("docQuestion");
const docAnswer = document.getElementById("docAnswer");
const docSourceLink = document.getElementById("docSourceLink");
const docCloseButton = document.getElementById("docCloseButton");

let currentOffset = 0;

initializeFromUrl();
if (queryInput.value.trim()) {
    runSearch();
}

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
    if (queryInput.value.trim()) {
        runSearch();
    }
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
    showAiSkeleton();

    const searchFetch = fetch(`/api/search?${params.toString()}`);
    const aiFetch = fetchAiOverview(query);

    try {
        const response = await searchFetch;
        const payload = await response.json();

        if (!response.ok) {
            throw new Error(extractErrorMessage(payload, response.status));
        }

        renderResults(payload);
    } catch (error) {
        toolbar.hidden = true;
        resultsArea.innerHTML = "";
        setStatus(error.message || "Something went wrong.", true);
    } finally {
        setLoading(false);
    }

    await aiFetch;
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
    const providerHasMore = typeof payload.providerHasMore === "boolean" ? payload.providerHasMore : payload.hasMore;
    nextButton.disabled = !providerHasMore;

    let position = 0;
    for (const item of items) {
        position += 1;
        const fragment = resultCardTemplate.content.cloneNode(true);
        const card = fragment.querySelector("article");
        if (card && (Number.isInteger(item.questionId) || Number.isFinite(item.questionId))) {
            card.id = `q-${item.questionId}`;
        }
        const title = fragment.querySelector(".result-title");
        const acceptedBadge = fragment.querySelector(".badge.accepted");
        const answeredBadge = fragment.querySelector(".badge.answered");
        const semanticBadge = fragment.querySelector(".badge.semantic");
        const meta = fragment.querySelector(".meta");
        const snippet = fragment.querySelector(".snippet");
        const tags = fragment.querySelector(".tags");
        const detailButton = fragment.querySelector(".detail-button");

        title.textContent = item.title;
        title.href = item.link;
        title.addEventListener("click", () => sendClickBeacon(payload.query, item.link, position));

        const tagText = (item.tags || []).slice(0, 8).map((tag) => `#${tag}`).join(" ");
        meta.textContent = `Score: ${item.questionScore} • Rank: ${item.score.toFixed(2)} • Source: ${item.source}${tagText ? ` • ${tagText}` : ""}`;

        snippet.textContent = item.snippet || "No snippet available.";

        if (item.accepted) {
            acceptedBadge.hidden = false;
        }
        if (item.answered) {
            answeredBadge.hidden = false;
        }
        if (payload.semanticMode === true) {
            semanticBadge.hidden = false;
        }

        for (const tag of item.tags || []) {
            const chip = document.createElement("span");
            chip.className = "tag";
            chip.textContent = tag;
            tags.appendChild(chip);
        }

        if (Number.isInteger(item.questionId) || Number.isFinite(item.questionId)) {
            detailButton.addEventListener("click", () => {
                sendClickBeacon(payload.query, item.link, 0);
                loadDocumentDetail(item.questionId);
            });
        } else {
            detailButton.disabled = true;
            detailButton.textContent = "Detail unavailable";
        }

        resultsArea.appendChild(fragment);
    }
}

async function fetchAiOverview(query) {
    try {
        const response = await fetch(`/api/ask?q=${encodeURIComponent(query)}`);
        if (!response.ok) {
            hideAiOverview();
            return;
        }
        const payload = await response.json();
        renderAiOverview(payload);
    } catch {
        hideAiOverview();
    }
}

function showAiSkeleton() {
    aiOverview.hidden = false;
    aiOverviewContent.innerHTML = '<div class="skeleton ai-skeleton"></div>';
    aiCitations.hidden = true;
    aiCitations.innerHTML = "";
}

function hideAiOverview() {
    aiOverview.hidden = true;
    aiOverviewContent.innerHTML = "";
    aiCitations.hidden = true;
    aiCitations.innerHTML = "";
}

function escapeHtml(text) {
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}

function formatAiMarkdown(raw, citationMap) {
    // Extract fenced code blocks first and replace with placeholders
    const codeBlocks = [];
    const withPlaceholders = raw.replace(/```(\w*)\n([\s\S]*?)```/g, (_, lang, code) => {
        const idx = codeBlocks.length;
        const langAttr = lang ? ` data-lang="${escapeHtml(lang)}"` : "";
        codeBlocks.push(
            `<pre class="ai-code-block"><div class="ai-code-header">${escapeHtml(lang || "code")}</div><code${langAttr}>${escapeHtml(code.replace(/\n$/, ""))}</code></pre>`
        );
        return `\x00CODEBLOCK_${idx}\x00`;
    });

    // Escape HTML in the remaining text
    let html = escapeHtml(withPlaceholders);

    // Inline code: `code`
    html = html.replace(/`([^`]+)`/g, '<code class="ai-inline-code">$1</code>');

    // Bold: **text**
    html = html.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");

    // Citation markers: [SO-n]
    html = html.replace(/\[SO-(\d+)\]/g, (match, num) => {
        const c = citationMap[Number.parseInt(num, 10)];
        if (!c) return "";
        const safeTitle = (c.title || "").replace(/"/g, "&quot;");
        const safeUrl = (c.url || "#").replace(/"/g, "&quot;");
        return `<a href="${safeUrl}" target="_blank" rel="noopener noreferrer" class="ai-ref" data-tooltip="${safeTitle}"><sup>${num}</sup></a>`;
    });

    // Newlines to <br> (but not inside code block placeholders)
    html = html.replace(/\n/g, "<br>");

    // Restore code block placeholders
    html = html.replace(/\x00CODEBLOCK_(\d+)\x00/g, (_, idx) => codeBlocks[Number.parseInt(idx, 10)]);

    return html;
}

function renderAiOverview(payload) {
    if (!payload || !payload.overview) {
        hideAiOverview();
        return;
    }

    aiOverview.hidden = false;
    const citations = payload.citations || [];
    const citationMap = {};
    for (const c of citations) {
        citationMap[c.index] = c;
    }

    aiOverviewContent.innerHTML = formatAiMarkdown(payload.overview, citationMap);

    aiCitations.hidden = true;
    aiCitations.innerHTML = "";
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
    removeRelatedQuestions();

    const docFetch = fetch(`/api/doc/${encodeURIComponent(questionId)}`);
    const similarFetch = fetch(`/api/similar/${encodeURIComponent(questionId)}`).catch(() => null);

    let docOk = false;
    try {
        const response = await docFetch;
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
        docOk = true;
    } catch (error) {
        docTitle.textContent = "Document detail unavailable";
        docMeta.textContent = "";
        docQuestion.textContent = error.message || "Unable to load document detail.";
        docAnswer.textContent = "";
        docSourceLink.textContent = "";
        docSourceLink.removeAttribute("href");
    }

    if (docOk) {
        try {
            const similarResp = await similarFetch;
            if (similarResp && similarResp.ok) {
                const similar = await similarResp.json();
                if (Array.isArray(similar) && similar.length > 0) {
                    renderRelatedQuestions(similar.slice(0, 5));
                }
            }
        } catch {
            // Degrade gracefully — related questions are optional.
        }
    }
}

function renderRelatedQuestions(items) {
    const section = document.createElement("section");
    section.className = "related-questions";

    const heading = document.createElement("h4");
    heading.textContent = "Related Questions";
    section.appendChild(heading);

    for (const q of items) {
        const card = document.createElement("div");
        card.className = "related-question-card";

        const link = document.createElement("a");
        link.href = q.link || "#";
        link.textContent = q.title || `Question #${q.questionId}`;
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        card.appendChild(link);

        if (q.tags && q.tags.length > 0) {
            const tagsDiv = document.createElement("div");
            tagsDiv.className = "tags";
            for (const tag of q.tags) {
                const chip = document.createElement("span");
                chip.className = "tag";
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
    const existing = docPanel.querySelector(".related-questions");
    if (existing) {
        existing.remove();
    }
}

function sendClickBeacon(query, url, position) {
    if (!navigator.sendBeacon) {
        return;
    }
    navigator.sendBeacon(
        "/api/events/click",
        new Blob([JSON.stringify({ query, url, position })], { type: "application/json" })
    );
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
    const query = params.get("q") || "";
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

toolbar.hidden = true;
resultsArea.innerHTML = "";
setStatus("Type a query and press Search.");
