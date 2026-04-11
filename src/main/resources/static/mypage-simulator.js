(function () {
	const root = document.getElementById("mypageSimulator");
	if (!root) {
		return;
	}

	const endpoints = {
		dashboard: root.dataset.dashboardUrl,
		items: root.dataset.itemsUrl,
		simulator: root.dataset.simulatorUrl,
		recommend: root.dataset.recommendUrl,
		sync: root.dataset.syncUrl
	};

	const slotLayout = {
		weapon: "mypage-slot-weapon",
		helmet: "mypage-slot-helmet",
		armor: "mypage-slot-armor",
		gloves: "mypage-slot-gloves",
		pants: "mypage-slot-pants",
		boots: "mypage-slot-boots",
		ring: "mypage-slot-ring",
		shoulder: "mypage-slot-shoulder",
		necklace: "mypage-slot-necklace"
	};

	const state = {
		dashboard: null,
		catalog: { items: [] },
		currentSlot: "weapon",
		ownedOnly: false,
		keyword: "",
		selectedItemId: null,
		equippedItemIds: {},
		simulation: {
			equippedSlots: [],
			totalStats: emptyStats()
		},
		recommendations: [],
		showRecommendations: false,
		isCatalogLoading: false,
		isSimulationLoading: false,
		isRecommendationLoading: false
	};

	root.addEventListener("click", function (event) {
		const actionTarget = event.target.closest("[data-action]");
		if (!actionTarget) {
			return;
		}

		handleAction(actionTarget).catch(handleError);
	});

	const searchForm = root.querySelector('[data-role="search-form"]');
	searchForm.addEventListener("submit", function (event) {
		event.preventDefault();
		const keywordInput = root.querySelector('[data-role="keyword-input"]');
		state.keyword = keywordInput.value.trim();
		state.selectedItemId = null;
		loadCatalog().catch(handleError);
	});

	loadDashboard().catch(handleError);

	async function handleAction(target) {
		const action = target.dataset.action;

		if (action === "toggle-tab") {
			const ownedOnly = target.dataset.owned === "true";
			if (state.ownedOnly === ownedOnly) {
				return;
			}
			state.ownedOnly = ownedOnly;
			state.selectedItemId = null;
			await loadCatalog();
			return;
		}

		if (action === "select-slot") {
			const slotCode = target.dataset.slot;
			if (!slotCode || state.currentSlot === slotCode) {
				return;
			}
			state.currentSlot = slotCode;
			state.selectedItemId = null;
			await loadCatalog();
			return;
		}

		if (action === "select-item") {
			state.selectedItemId = Number(target.dataset.itemId);
			render();
			return;
		}

		if (action === "equip-selected") {
			const selected = getSelectedItem();
			if (!selected) {
				return;
			}
			state.equippedItemIds[selected.slotCode] = selected.id;
			await simulateLoadout();
			showStatus(selected.name + "을(를) " + selected.slotLabel + " 슬롯에 장착했습니다.");
			return;
		}

		if (action === "unequip-slot") {
			const slotCode = target.dataset.slot;
			if (!slotCode) {
				return;
			}
			delete state.equippedItemIds[slotCode];
			await simulateLoadout();
			showStatus("장착을 해제했습니다.");
			return;
		}

		if (action === "load-saved") {
			state.equippedItemIds = buildSavedEquipmentMap();
			await simulateLoadout();
			showStatus("DB에 저장된 장비 세팅을 불러왔습니다.");
			return;
		}

		if (action === "clear-all") {
			state.equippedItemIds = {};
			await simulateLoadout();
			showStatus("시뮬레이터 장착 상태를 모두 비웠습니다.");
			return;
		}

		if (action === "request-growth") {
			state.showRecommendations = true;
			await loadRecommendations();
			return;
		}

		if (action === "sync-catalog") {
			await syncCatalog();
		}
	}

	async function loadDashboard() {
		showStatus("장비 시뮬레이터 정보를 불러오는 중입니다.", true);
		state.dashboard = await fetchJson(endpoints.dashboard);
		state.currentSlot = state.dashboard.defaultSlotCode || firstSlotCode();
		root.querySelector('[data-role="keyword-input"]').value = state.keyword;

		const syncButton = root.querySelector('[data-role="sync-button"]');
		if (state.dashboard.admin) {
			syncButton.hidden = false;
		}

		render();
		await loadCatalog();
		await simulateLoadout();
		clearStatus();
	}

	async function loadCatalog() {
		state.isCatalogLoading = true;
		render();

		const params = new URLSearchParams({
			slotCode: state.currentSlot,
			ownedOnly: String(state.ownedOnly)
		});
		if (state.keyword) {
			params.set("keyword", state.keyword);
		}

		state.catalog = await fetchJson(endpoints.items + "?" + params.toString());
		state.isCatalogLoading = false;

		if (!(state.catalog.items || []).some(function (item) { return item.id === state.selectedItemId; })) {
			state.selectedItemId = null;
		}

		render();
	}

	async function simulateLoadout() {
		state.isSimulationLoading = true;
		render();

		state.simulation = await fetchJson(endpoints.simulator, {
			method: "POST",
			body: JSON.stringify({
				equippedItemIds: state.equippedItemIds
			})
		});

		state.isSimulationLoading = false;

		if (state.showRecommendations) {
			await loadRecommendations();
			return;
		}

		render();
	}

	async function loadRecommendations() {
		state.isRecommendationLoading = true;
		render();

		const response = await fetchJson(endpoints.recommend, {
			method: "POST",
			body: JSON.stringify({
				equippedItemIds: state.equippedItemIds
			})
		});

		state.recommendations = response.priorities || [];
		state.isRecommendationLoading = false;
		render();
	}

	async function syncCatalog() {
		showStatus("공식 아이템 DB를 동기화하고 있습니다.", true);
		await fetchJson(endpoints.sync, { method: "POST" });
		await loadDashboard();
		showStatus("아이템 DB 동기화가 완료되었습니다.");
	}

	function render() {
		if (!state.dashboard) {
			return;
		}

		renderHeroBadges();
		renderSlotTabs();
		renderCatalog();
		renderSlotBoard();
		renderSelectedItem();
		renderUserSummary();
		renderStats();
		renderRecommendations();
		renderPurchaseVault();
		updateActionButtons();
	}

	function renderHeroBadges() {
		const badgeRoot = root.querySelector('[data-role="hero-badges"]');
		const savedCount = (state.dashboard.savedEquipment || []).filter(function (slot) {
			return slot.equippedItem;
		}).length;
		const badges = [
			renderBadge("보유 골드", formatNumber(state.dashboard.user.gold) + "G"),
			renderBadge("동기화 아이템", formatNumber(state.dashboard.syncStatus.totalItems) + "개"),
			renderBadge("저장 장비", savedCount + "칸")
		];

		if (state.dashboard.syncStatus.lastSyncedAt) {
			badges.push(renderBadge("최근 동기화", formatDateTime(state.dashboard.syncStatus.lastSyncedAt)));
		}

		badgeRoot.innerHTML = badges.join("");
	}

	function renderSlotTabs() {
		const slotRoot = root.querySelector('[data-role="slot-tabs"]');
		slotRoot.innerHTML = (state.dashboard.slots || []).map(function (slot) {
			const count = state.ownedOnly ? slot.ownedItemCount : slot.totalItemCount;
			return "<button type=\"button\" class=\"mypage-slot-button" + (state.currentSlot === slot.slotCode ? " is-active" : "") + "\" data-action=\"select-slot\" data-slot=\"" + escapeHtml(slot.slotCode) + "\">"
				+ "<strong>" + escapeHtml(slot.slotLabel) + "</strong>"
				+ "<span>" + formatNumber(count) + "개</span>"
				+ "</button>";
		}).join("");
	}

	function renderCatalog() {
		const listRoot = root.querySelector('[data-role="catalog-list"]');
		const listTitle = root.querySelector('[data-role="list-title"]');
		const listMeta = root.querySelector('[data-role="list-meta"]');
		const slot = currentSlotMeta();

		listTitle.textContent = (state.ownedOnly ? "내 구매 아이템" : "전체 아이템") + " · " + (slot ? slot.slotLabel : "");

		if (state.isCatalogLoading) {
			listMeta.textContent = "목록을 불러오는 중...";
			listRoot.innerHTML = renderEmptyState("아이템 목록을 불러오는 중입니다.");
			return;
		}

		listMeta.textContent = formatNumber((state.catalog.items || []).length) + "개 표시";

		if (!state.catalog.items || state.catalog.items.length === 0) {
			listRoot.innerHTML = renderEmptyState(state.ownedOnly
				? "구매한 아이템이 없거나 현재 슬롯에 맞는 보유 아이템이 없습니다."
				: "조건에 맞는 아이템이 없습니다.");
			return;
		}

		listRoot.innerHTML = state.catalog.items.map(function (item) {
			const chips = [];
			if (item.owned) {
				chips.push("<span class=\"mypage-chip is-owned\">내 구매</span>");
			}
			(item.statChips || []).forEach(function (chip) {
				chips.push("<span class=\"mypage-chip\">" + escapeHtml(chip.label) + " " + escapeHtml(chip.value) + "</span>");
			});

			return "<article class=\"mypage-item-card" + (item.id === state.selectedItemId ? " is-selected" : "") + "\" data-action=\"select-item\" data-item-id=\"" + item.id + "\">"
				+ "<div class=\"mypage-item-head\">"
				+ "<div class=\"mypage-item-meta\">"
				+ renderThumb(item.icon, item.slotLabel || item.name, "mypage-item-thumb")
				+ "<div>"
				+ "<div class=\"mypage-item-name grade-" + escapeHtml(item.gradeKey || "common") + "\">" + escapeHtml(item.name) + "</div>"
				+ "<div class=\"mypage-item-sub\">" + escapeHtml(item.categoryName || item.slotLabel || "") + " · Lv." + escapeHtml(String(item.equipLevel || 1)) + "</div>"
				+ "</div>"
				+ "</div>"
				+ "<div class=\"mypage-item-power\">P " + formatNumber(item.powerScore) + "</div>"
				+ "</div>"
				+ "<div class=\"mypage-item-badges\">" + chips.join("") + "</div>"
				+ "<div class=\"mypage-item-description\">" + escapeHtml(item.description || "선택하면 가운데 장비 슬롯에 장착할 수 있습니다.") + "</div>"
				+ "</article>";
		}).join("");
	}

	function renderSlotBoard() {
		const boardRoot = root.querySelector('[data-role="slot-board"]');
		const equippedMap = simulationSlotMap();
		const cards = (state.dashboard.slots || []).map(function (slot) {
			const item = equippedMap[slot.slotCode] || null;
			const cardBody = item
				? "<div class=\"mypage-slot-thumb\">" + renderImage(item.icon, item.slotLabel || item.name) + "</div>"
					+ "<div class=\"mypage-item-sub\">" + escapeHtml(item.name) + "</div>"
					+ "<div class=\"mypage-slot-power\">P " + formatNumber(item.powerScore) + "</div>"
					+ "<button type=\"button\" data-action=\"unequip-slot\" data-slot=\"" + escapeHtml(slot.slotCode) + "\">장착 해제</button>"
				: "<div class=\"mypage-slot-thumb\"><span class=\"mypage-empty-copy\">" + escapeHtml(slot.slotLabel) + "</span></div>"
					+ "<div class=\"mypage-empty-copy\">비어 있음</div>"
					+ "<div class=\"mypage-item-sub\">왼쪽 목록에서 선택해 장착하세요.</div>";

			return "<article class=\"mypage-slot-card " + escapeHtml(slotLayout[slot.slotCode] || "") + (state.currentSlot === slot.slotCode ? " is-active" : "") + (item ? " is-filled" : "") + "\" data-action=\"select-slot\" data-slot=\"" + escapeHtml(slot.slotCode) + "\">"
				+ "<div class=\"mypage-slot-header\">"
				+ "<div>"
				+ "<div class=\"mypage-slot-label\">" + escapeHtml(slot.slotLabel) + "</div>"
				+ "<div class=\"mypage-slot-name\">" + (item ? escapeHtml(item.name) : "빈 슬롯") + "</div>"
				+ "</div>"
				+ "</div>"
				+ cardBody
				+ "</article>";
		}).join("");

		boardRoot.innerHTML = "<div class=\"mypage-board-center\" aria-hidden=\"true\"></div>" + cards;
	}

	function renderSelectedItem() {
		const selectedRoot = root.querySelector('[data-role="selected-item"]');
		const selected = getSelectedItem();

		if (!selected) {
			selectedRoot.innerHTML = renderEmptyState("왼쪽 아이템 목록에서 장비를 선택하면 여기에서 상세 정보와 장착 버튼을 확인할 수 있습니다.");
			return;
		}

		const chips = [];
		if (selected.owned) {
			chips.push("<span class=\"mypage-chip is-owned\">내 구매 아이템</span>");
		}
		(selected.statChips || []).forEach(function (chip) {
			chips.push("<span class=\"mypage-chip\">" + escapeHtml(chip.label) + " " + escapeHtml(chip.value) + "</span>");
		});

		selectedRoot.innerHTML = "<div class=\"mypage-selected-card\">"
			+ "<div class=\"mypage-preview-head\">"
			+ "<div class=\"mypage-preview-copy\">"
			+ renderThumb(selected.icon, selected.slotLabel || selected.name, "mypage-item-thumb")
			+ "<div>"
			+ "<div class=\"mypage-preview-name grade-" + escapeHtml(selected.gradeKey || "common") + "\">" + escapeHtml(selected.name) + "</div>"
			+ "<div class=\"mypage-preview-sub\">" + escapeHtml(selected.slotLabel) + " · " + escapeHtml(selected.categoryName || "") + " · Lv." + escapeHtml(String(selected.equipLevel || 1)) + "</div>"
			+ "</div>"
			+ "</div>"
			+ "<div class=\"mypage-item-power\">P " + formatNumber(selected.powerScore) + "</div>"
			+ "</div>"
			+ "<div class=\"mypage-preview-chips\">" + chips.join("") + "</div>"
			+ "<div class=\"mypage-item-description\">" + escapeHtml(selected.description || "선택한 아이템을 장착하면 오른쪽 통합 스탯이 즉시 다시 계산됩니다.") + "</div>"
			+ "</div>";
	}

	function renderUserSummary() {
		const summaryRoot = root.querySelector('[data-role="user-summary"]');
		const savedCount = Object.keys(buildSavedEquipmentMap()).length;
		const equippedCount = Object.keys(state.equippedItemIds).length;

		summaryRoot.innerHTML = "<div class=\"mypage-user-card\">"
			+ renderUserRow("보유 골드", "<span class=\"mypage-gold\">" + formatNumber(state.dashboard.user.gold) + "G</span>")
			+ renderUserRow("시뮬레이터 장착", formatNumber(equippedCount) + "칸")
			+ renderUserRow("DB 저장 장비", formatNumber(savedCount) + "칸")
			+ renderUserRow("내 구매 종류", formatNumber((state.dashboard.purchaseVault || []).length) + "개")
			+ "</div>";
	}

	function renderStats() {
		const statsRoot = root.querySelector('[data-role="stats-grid"]');
		const stats = state.simulation.totalStats || emptyStats();

		statsRoot.innerHTML = [
			statCard("공격력", formatAttack(stats.totalAttackMin, stats.totalAttackMax)),
			statCard("방어력", formatNumber(stats.totalDefense)),
			statCard("명중", formatNumber(stats.totalAccuracy)),
			statCard("치명타", formatNumber(stats.totalCritical)),
			statCard("생명력", formatNumber(stats.totalHealth)),
			statCard("마법 증폭", formatNumber(stats.totalMagicBoost)),
			statCard("마법 적중", formatNumber(stats.totalMagicAccuracy)),
			statCard("전투력", formatNumber(stats.powerScore))
		].join("");
	}

	function renderRecommendations() {
		const recommendRoot = root.querySelector('[data-role="recommendations"]');

		if (state.isRecommendationLoading) {
			recommendRoot.innerHTML = renderEmptyState("현재 장착 상태를 분석해 성장 우선순위를 계산하는 중입니다.");
			return;
		}

		if (!state.showRecommendations) {
			recommendRoot.innerHTML = renderEmptyState("성장 추천 버튼을 누르면 현재 가장 약한 부위부터 같은 종류 상위 장비로 교체하는 순서를 보여드립니다.");
			return;
		}

		if (!state.recommendations.length) {
			recommendRoot.innerHTML = renderEmptyState("추천할 성장 우선순위를 찾지 못했습니다.");
			return;
		}

		recommendRoot.innerHTML = state.recommendations.map(function (priority) {
			return "<article class=\"mypage-recommend-card\">"
				+ "<div class=\"mypage-recommend-meta\">"
				+ "<span class=\"mypage-recommend-tag\">" + escapeHtml(priority.targetScope || priority.priorityGroup) + "</span>"
				+ "<span class=\"mypage-recommend-score\">현재 P " + formatNumber(priority.currentPowerScore) + "</span>"
				+ "</div>"
				+ "<strong>" + escapeHtml(priority.headline) + "</strong>"
				+ "<div class=\"mypage-item-sub\">" + escapeHtml(priority.currentItemName || "빈 슬롯") + " -> " + escapeHtml(priority.targetItemName || "대상 장비 없음") + "</div>"
				+ "<div class=\"mypage-item-sub\">목표 P " + formatNumber(priority.benchmarkPowerScore) + " / 차이 +" + formatNumber(priority.gapPowerScore) + "</div>"
				+ "<div class=\"mypage-item-description\">" + escapeHtml(priority.description) + "</div>"
				+ "</article>";
		}).join("");
	}

	function renderPurchaseVault() {
		const vaultRoot = root.querySelector('[data-role="purchase-vault"]');
		const purchaseVault = state.dashboard.purchaseVault || [];

		if (!purchaseVault.length) {
			vaultRoot.innerHTML = renderEmptyState("아직 구매한 아이템이 없습니다.");
			return;
		}

		vaultRoot.innerHTML = purchaseVault.map(function (item) {
			return "<article class=\"mypage-vault-card\">"
				+ "<div class=\"mypage-vault-head\">"
				+ "<div class=\"mypage-vault-copy\">"
				+ renderThumb(item.icon, item.categoryLabel || item.name, "mypage-vault-thumb")
				+ "<div>"
				+ "<div class=\"mypage-vault-name\">" + escapeHtml(item.name) + "</div>"
				+ "<div class=\"mypage-vault-sub\">" + escapeHtml(item.categoryLabel || "구매 아이템") + "</div>"
				+ "</div>"
				+ "</div>"
				+ "<div class=\"mypage-item-sub\">" + formatDateTime(item.purchasedAt) + "</div>"
				+ "</div>"
				+ "</article>";
		}).join("");
	}

	function updateActionButtons() {
		const equipButton = root.querySelector('[data-action="equip-selected"]');
		const selected = getSelectedItem();
		equipButton.disabled = !selected;
		equipButton.textContent = selected ? selected.slotLabel + " 장착하기" : "장착하기";

		root.querySelectorAll(".mypage-tab-button").forEach(function (button) {
			button.classList.toggle("is-active", button.dataset.owned === String(state.ownedOnly));
		});
	}

	function currentSlotMeta() {
		return (state.dashboard.slots || []).find(function (slot) {
			return slot.slotCode === state.currentSlot;
		}) || null;
	}

	function firstSlotCode() {
		return state.dashboard && state.dashboard.slots && state.dashboard.slots.length
			? state.dashboard.slots[0].slotCode
			: "weapon";
	}

	function simulationSlotMap() {
		const map = {};
		(state.simulation.equippedSlots || []).forEach(function (slot) {
			map[slot.slotCode] = slot.equippedItem;
		});
		return map;
	}

	function buildSavedEquipmentMap() {
		const map = {};
		(state.dashboard.savedEquipment || []).forEach(function (slot) {
			if (slot.equippedItem && slot.equippedItem.id) {
				map[slot.slotCode] = slot.equippedItem.id;
			}
		});
		return map;
	}

	function getSelectedItem() {
		return (state.catalog.items || []).find(function (item) {
			return item.id === state.selectedItemId;
		}) || null;
	}

	function fetchJson(url, options) {
		const requestOptions = options || {};
		const headers = new Headers(requestOptions.headers || {});
		const csrfToken = document.querySelector('meta[name="_csrf"]');
		const csrfHeader = document.querySelector('meta[name="_csrf_header"]');

		if (!headers.has("Accept")) {
			headers.set("Accept", "application/json");
		}

		if (requestOptions.body && !headers.has("Content-Type")) {
			headers.set("Content-Type", "application/json");
		}

		if (csrfToken && csrfHeader && requestOptions.method && requestOptions.method !== "GET") {
			headers.set(csrfHeader.content, csrfToken.content);
		}

		return fetch(url, Object.assign({}, requestOptions, { headers: headers }))
			.then(function (response) {
				if (!response.ok) {
					return response.text().then(function (message) {
						throw new Error(message || "요청 처리 중 오류가 발생했습니다.");
					});
				}
				return response.json();
			});
	}

	function renderBadge(label, value) {
		return "<div class=\"mypage-badge\"><strong>" + escapeHtml(label) + "</strong><span>" + escapeHtml(value) + "</span></div>";
	}

	function renderUserRow(label, valueHtml) {
		return "<div class=\"mypage-user-row\"><span>" + escapeHtml(label) + "</span><span>" + valueHtml + "</span></div>";
	}

	function statCard(label, value) {
		return "<div class=\"mypage-stat-card\">"
			+ "<span class=\"mypage-stat-label\">" + escapeHtml(label) + "</span>"
			+ "<span class=\"mypage-stat-value\">" + escapeHtml(value) + "</span>"
			+ "</div>";
	}

	function renderThumb(icon, fallback, className) {
		return "<div class=\"" + className + "\">" + renderImage(icon, fallback) + "</div>";
	}

	function renderImage(icon, fallback) {
		if (icon) {
			return "<img src=\"" + escapeHtml(icon) + "\" alt=\"" + escapeHtml(fallback || "item") + "\">";
		}
		return "<span class=\"mypage-empty-copy\">" + escapeHtml((fallback || "?").slice(0, 2)) + "</span>";
	}

	function renderEmptyState(message) {
		return "<div class=\"mypage-empty-state\"><div class=\"mypage-empty-message\">" + escapeHtml(message) + "</div></div>";
	}

	function showStatus(message, persist) {
		const status = root.querySelector('[data-role="status-message"]');
		status.classList.add("is-visible");
		status.textContent = message;

		if (persist) {
			return;
		}

		window.clearTimeout(showStatus.timer);
		showStatus.timer = window.setTimeout(clearStatus, 2600);
	}

	function clearStatus() {
		const status = root.querySelector('[data-role="status-message"]');
		status.classList.remove("is-visible");
		status.textContent = "";
	}

	function handleError(error) {
		state.isCatalogLoading = false;
		state.isSimulationLoading = false;
		state.isRecommendationLoading = false;
		showStatus(error && error.message ? error.message : "처리 중 오류가 발생했습니다.", true);
		render();
	}

	function emptyStats() {
		return {
			totalAttackMin: 0,
			totalAttackMax: 0,
			totalDefense: 0,
			totalAccuracy: 0,
			totalCritical: 0,
			totalHealth: 0,
			totalMagicBoost: 0,
			totalMagicAccuracy: 0,
			totalPveAttack: 0,
			totalHealingBoost: 0,
			powerScore: 0
		};
	}

	function formatNumber(value) {
		return new Intl.NumberFormat("ko-KR", {
			maximumFractionDigits: 0
		}).format(Number(value || 0));
	}

	function formatAttack(min, max) {
		if (Number(min || 0) === 0 && Number(max || 0) === 0) {
			return "0";
		}
		return formatNumber(min) + " ~ " + formatNumber(max);
	}

	function formatDateTime(value) {
		if (!value) {
			return "-";
		}
		return new Intl.DateTimeFormat("ko-KR", {
			month: "2-digit",
			day: "2-digit",
			hour: "2-digit",
			minute: "2-digit"
		}).format(new Date(value));
	}

	function escapeHtml(value) {
		return String(value == null ? "" : value)
			.replace(/&/g, "&amp;")
			.replace(/</g, "&lt;")
			.replace(/>/g, "&gt;")
			.replace(/"/g, "&quot;")
			.replace(/'/g, "&#39;");
	}
})();
