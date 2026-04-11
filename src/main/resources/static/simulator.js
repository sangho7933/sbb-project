(function () {
	const root = document.getElementById("simulatorPage");
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

	const slotCategoryDefinitions = [
		{
			key: "weapon",
			title: "무기",
			description: "무기 전용",
			slotCodes: ["weapon"]
		},
		{
			key: "guarder",
			title: "가더",
			description: "가더 전용",
			slotCodes: ["guarder"]
		},
		{
			key: "accessory",
			title: "장신구",
			description: "반지 / 귀걸이 / 목걸이",
			slotCodes: ["ring", "earring", "necklace"]
		},
		{
			key: "armor",
			title: "방어구",
			description: "투구 / 견갑 / 상의 / 장갑 / 하의 / 신발",
			slotCodes: ["helmet", "shoulder", "armor", "gloves", "pants", "boots"]
		}
	];

	const slotBoardRowDefinitions = [
		{
			key: "front",
			badge: "1줄",
			title: "무기 / 가더",
			description: "무기 / 가더",
			slotCodes: ["weapon", "guarder"]
		},
		{
			key: "accessory",
			badge: "2줄",
			title: "장신구",
			description: "장신구 3종",
			slotCodes: ["ring", "earring", "necklace"]
		},
		{
			key: "armor",
			badge: "3줄",
			title: "방어구",
			description: "방어구 6종",
			slotCodes: ["helmet", "shoulder", "armor", "gloves", "pants", "boots"]
		}
	];

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
		activeRecommendationIndex: 0,
		showRecommendations: false,
		isRecommendationModalOpen: false,
		draggingItemId: null,
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

	root.addEventListener("dragstart", handleDragStart);
	root.addEventListener("dragend", handleDragEnd);
	root.addEventListener("dragover", handleDragOver);
	root.addEventListener("dragleave", handleDragLeave);
	root.addEventListener("drop", function (event) {
		handleDrop(event).catch(handleError);
	});
	document.addEventListener("keydown", handleGlobalKeydown);

	const searchForm = root.querySelector('[data-role="search-form"]');
	if (searchForm) {
		searchForm.addEventListener("submit", function (event) {
			event.preventDefault();
			const keywordInput = root.querySelector('[data-role="keyword-input"]');
			state.keyword = keywordInput ? keywordInput.value.trim() : "";
			state.selectedItemId = null;
			loadCatalog().catch(handleError);
		});
	}

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
			await equipItem(selected, selected.slotCode);
			return;
		}

		if (action === "unequip-slot") {
			const slotCode = target.dataset.slot;
			if (!slotCode) {
				return;
			}
			delete state.equippedItemIds[slotCode];
			await simulateLoadout();
			showStatus("장비를 해제했습니다.");
			return;
		}

		if (action === "load-saved") {
			state.equippedItemIds = buildSavedEquipmentMap();
			await simulateLoadout();
			showStatus("저장된 장비를 불러왔습니다.");
			return;
		}

		if (action === "clear-all") {
			state.equippedItemIds = {};
			await simulateLoadout();
			showStatus("모든 장비를 해제했습니다.");
			return;
		}

		if (action === "request-growth") {
			state.showRecommendations = true;
			state.activeRecommendationIndex = 0;
			state.isRecommendationLoading = true;
			openRecommendationModal();
			await loadRecommendations();
			return;
		}

		if (action === "sync-catalog") {
			await syncCatalog();
			return;
		}

		if (action === "close-recommend-modal") {
			closeRecommendationModal();
			return;
		}

		if (action === "next-recommendation") {
			showNextRecommendation();
			return;
		}

		if (action === "select-recommendation-step") {
			state.activeRecommendationIndex = clampRecommendationIndex(Number(target.dataset.index));
			render();
		}
	}

	async function loadDashboard() {
		showStatus("시뮬레이터 정보를 불러오는 중입니다.", true);
		state.dashboard = await fetchJson(endpoints.dashboard);
		state.currentSlot = state.dashboard.defaultSlotCode || firstSlotCode();
		state.equippedItemIds = buildSavedEquipmentMap();

		const keywordInput = root.querySelector('[data-role="keyword-input"]');
		if (keywordInput) {
			keywordInput.value = state.keyword;
		}

		const syncButton = root.querySelector('[data-role="sync-button"]');
		if (syncButton) {
			syncButton.hidden = !state.dashboard.admin;
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
		state.activeRecommendationIndex = clampRecommendationIndex(state.activeRecommendationIndex);
		state.isRecommendationLoading = false;
		render();
	}

	async function syncCatalog() {
		showStatus("공식 아이템 DB를 동기화하는 중입니다.", true);
		await fetchJson(endpoints.sync, { method: "POST" });
		await loadDashboard();
		showStatus("아이템 DB 동기화가 완료되었습니다.");
	}

	async function equipItem(item, targetSlotCode) {
		if (!item || !targetSlotCode) {
			return;
		}

		if (item.slotCode !== targetSlotCode) {
			showStatus("장착 가능한 부위가 맞지 않습니다.", true);
			return;
		}

		state.equippedItemIds[targetSlotCode] = item.id;
		state.selectedItemId = item.id;
		await simulateLoadout();
		showStatus(item.name + " 장비를 " + item.slotLabel + " 슬롯에 장착했습니다.");
	}

	function handleDragStart(event) {
		const itemCard = event.target.closest(".mypage-item-card");
		if (!itemCard) {
			return;
		}

		const item = findCatalogItem(Number(itemCard.dataset.itemId));
		if (!item) {
			return;
		}

		state.draggingItemId = item.id;
		itemCard.classList.add("is-dragging");
		if (event.dataTransfer) {
			event.dataTransfer.effectAllowed = "move";
			event.dataTransfer.setData("text/plain", String(item.id));
		}
		highlightDropTargets(item.slotCode);
	}

	function handleDragEnd() {
		clearDropTargets();
	}

	function handleDragOver(event) {
		const slotCard = event.target.closest(".mypage-slot-card");
		const draggingItem = getDraggingItem();
		if (!slotCard || !draggingItem) {
			return;
		}

		if (slotCard.dataset.slot === draggingItem.slotCode) {
			event.preventDefault();
			if (event.dataTransfer) {
				event.dataTransfer.dropEffect = "move";
			}
			slotCard.classList.add("is-drop-over");
		}
	}

	function handleDragLeave(event) {
		const slotCard = event.target.closest(".mypage-slot-card");
		if (!slotCard) {
			return;
		}
		slotCard.classList.remove("is-drop-over");
	}

	async function handleDrop(event) {
		const slotCard = event.target.closest(".mypage-slot-card");
		const draggingItem = getDraggingItem();
		if (!slotCard || !draggingItem) {
			return;
		}

		event.preventDefault();
		slotCard.classList.remove("is-drop-over");
		await equipItem(draggingItem, slotCard.dataset.slot);
		clearDropTargets();
	}

	function highlightDropTargets(slotCode) {
		root.querySelectorAll(".mypage-slot-card").forEach(function (slotCard) {
			slotCard.classList.remove("is-drop-allowed", "is-drop-denied", "is-drop-over");
			if (slotCard.dataset.slot === slotCode) {
				slotCard.classList.add("is-drop-allowed");
			} else {
				slotCard.classList.add("is-drop-denied");
			}
		});
	}

	function clearDropTargets() {
		state.draggingItemId = null;
		root.querySelectorAll(".mypage-slot-card").forEach(function (slotCard) {
			slotCard.classList.remove("is-drop-allowed", "is-drop-denied", "is-drop-over");
		});
		root.querySelectorAll(".mypage-item-card.is-dragging").forEach(function (itemCard) {
			itemCard.classList.remove("is-dragging");
		});
	}

	function handleGlobalKeydown(event) {
		if (event.key === "Escape" && state.isRecommendationModalOpen) {
			closeRecommendationModal();
		}
	}

	function openRecommendationModal() {
		state.isRecommendationModalOpen = true;
		render();

		window.setTimeout(function () {
			const dialog = root.querySelector(".mypage-modal-dialog");
			if (dialog && state.isRecommendationModalOpen) {
				dialog.focus();
			}
		}, 0);
	}

	function closeRecommendationModal() {
		state.isRecommendationModalOpen = false;
		render();
	}

	function showNextRecommendation() {
		if (!state.recommendations.length) {
			return;
		}
		state.activeRecommendationIndex = (state.activeRecommendationIndex + 1) % state.recommendations.length;
		render();
	}

	function clampRecommendationIndex(index) {
		const maxIndex = Math.max(0, state.recommendations.length - 1);
		const numericIndex = Number.isFinite(index) ? index : 0;
		return Math.max(0, Math.min(maxIndex, numericIndex));
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
		if (!badgeRoot) {
			return;
		}

		const savedCount = (state.dashboard.savedEquipment || []).filter(function (slot) {
			return slot.equippedItem;
		}).length;

		const badges = [
			renderBadge("내 종족", state.dashboard.user.race || "마족"),
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
		if (!slotRoot) {
			return;
		}

		const slotByCode = {};
		(state.dashboard.slots || []).forEach(function (slot) {
			slotByCode[slot.slotCode] = slot;
		});

		slotRoot.innerHTML = slotCategoryDefinitions.map(function (group) {
			const groupSlots = group.slotCodes.map(function (slotCode) {
				return slotByCode[slotCode];
			}).filter(Boolean);

			if (!groupSlots.length) {
				return "";
			}

			const buttons = groupSlots.map(function (slot) {
				const count = state.ownedOnly ? slot.ownedItemCount : slot.totalItemCount;
				return "<button type=\"button\" class=\"mypage-slot-button" + (state.currentSlot === slot.slotCode ? " is-active" : "") + "\" data-action=\"select-slot\" data-slot=\"" + escapeHtml(slot.slotCode) + "\">"
					+ "<strong>" + escapeHtml(slot.slotLabel) + "</strong>"
					+ "<span>" + formatNumber(count) + "개</span>"
					+ "</button>";
			}).join("");

			return "<section class=\"mypage-slot-filter-group\">"
				+ "<div class=\"mypage-slot-filter-head\">"
				+ "<div>"
				+ "<div class=\"mypage-slot-filter-title\">" + escapeHtml(group.title) + "</div>"
				+ "<div class=\"mypage-slot-filter-sub\">" + escapeHtml(group.description) + "</div>"
				+ "</div>"
				+ "<span class=\"mypage-slot-filter-count\">" + formatNumber(groupSlots.length) + " 슬롯</span>"
				+ "</div>"
				+ "<div class=\"mypage-slot-filter-grid\">"
				+ buttons
				+ "</div>"
				+ "</section>";
		}).join("");
	}

	function renderCatalog() {
		const listRoot = root.querySelector('[data-role="catalog-list"]');
		const listTitle = root.querySelector('[data-role="list-title"]');
		const listMeta = root.querySelector('[data-role="list-meta"]');
		const slot = currentSlotMeta();

		if (!listRoot || !listTitle || !listMeta) {
			return;
		}

		listTitle.textContent = (state.ownedOnly ? "내 구매 아이템" : "전체 아이템") + " · " + (slot ? slot.slotLabel : "");

		if (state.isCatalogLoading) {
			listMeta.textContent = "목록을 불러오는 중...";
			listRoot.innerHTML = renderEmptyState("아이템 목록을 불러오는 중입니다.");
			return;
		}

		listMeta.textContent = formatNumber((state.catalog.items || []).length) + "개 표시";

		if (!state.catalog.items || state.catalog.items.length === 0) {
			listRoot.innerHTML = renderEmptyState(
				state.catalog.emptyMessage
				|| (state.ownedOnly ? "구매한 아이템이 없습니다." : "조건에 맞는 아이템이 없습니다.")
			);
			return;
		}

		listRoot.innerHTML = state.catalog.items.map(function (item) {
			const chips = [];
			if (item.owned) {
				chips.push("<span class=\"mypage-chip is-owned\">내 구매</span>");
			}
			if (item.raceName && item.raceName !== "전체") {
				chips.push("<span class=\"mypage-chip\">" + escapeHtml(item.raceName) + "</span>");
			}
			(item.statChips || []).forEach(function (chip) {
				chips.push("<span class=\"mypage-chip\">" + escapeHtml(chip.label) + " " + escapeHtml(chip.value) + "</span>");
			});

			return "<article class=\"mypage-item-card" + (item.id === state.selectedItemId ? " is-selected" : "") + "\" data-action=\"select-item\" data-item-id=\"" + item.id + "\" draggable=\"true\" data-slot-code=\"" + escapeHtml(item.slotCode) + "\">"
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
				+ "<div class=\"mypage-item-description\">" + escapeHtml(item.description || "선택 후 장착 버튼 또는 드래그로 장착할 수 있습니다.") + "</div>"
				+ "</article>";
		}).join("");
	}

	function renderSlotBoard() {
		const boardRoot = root.querySelector('[data-role="slot-board"]');
		if (!boardRoot) {
			return;
		}

		const equippedMap = simulationSlotMap();
		const slotRows = buildSlotRows(state.dashboard.slots || []);
		boardRoot.innerHTML = slotRows.map(function (row, index) {
			return renderSlotRow(row, equippedMap, index);
		}).join("");
	}

	function buildSlotRows(slots) {
		const slotByCode = {};
		const assignedCodes = {};

		slots.forEach(function (slot) {
			slotByCode[slot.slotCode] = slot;
		});

		const rows = slotBoardRowDefinitions.map(function (definition) {
			const groupSlots = definition.slotCodes.map(function (slotCode) {
				const slot = slotByCode[slotCode];
				if (slot) {
					assignedCodes[slotCode] = true;
				}
				return slot;
			}).filter(Boolean);

			if (!groupSlots.length) {
				return null;
			}

			return {
				key: definition.key,
				badge: definition.badge,
				title: definition.title,
				description: definition.description,
				slots: groupSlots
			};
		}).filter(Boolean);

		const extraSlots = slots.filter(function (slot) {
			return !assignedCodes[slot.slotCode];
		});

		if (extraSlots.length) {
			rows.push({
				key: "extra",
				badge: "추가",
				title: "추가 장비",
				description: extraSlots.map(function (slot) {
					return slot.slotLabel;
				}).join(" / "),
				slots: extraSlots
			});
		}

		return rows;
	}

	function renderSlotRow(row, equippedMap, index) {
		const slotCards = row.slots.map(function (slot) {
			return renderSlotCard(slot, equippedMap[slot.slotCode] || null);
		}).join("");

		return "<section class=\"mypage-slot-row mypage-slot-row-" + escapeHtml(row.key) + "\">"
			+ "<div class=\"mypage-slot-row-head\">"
			+ "<div>"
			+ "<div class=\"mypage-slot-row-title-row\">"
			+ "<span class=\"mypage-slot-row-order\">" + escapeHtml(row.badge || (String(index + 1) + "줄")) + "</span>"
			+ "<h3 class=\"mypage-slot-row-title\">" + escapeHtml(row.title) + "</h3>"
			+ "</div>"
			+ "<p class=\"mypage-slot-row-sub\">" + escapeHtml(row.description || row.slots.map(function (slot) {
				return slot.slotLabel;
			}).join(" / ")) + "</p>"
			+ "</div>"
			+ "<span class=\"mypage-slot-row-count\">" + formatNumber(row.slots.length) + " 슬롯</span>"
			+ "</div>"
			+ "<div class=\"mypage-slot-row-grid\">"
			+ slotCards
			+ "</div>"
			+ "</section>";
	}

	function renderSlotCard(slot, item) {
		const body = item
			? "<div class=\"mypage-slot-body\">"
				+ "<div class=\"mypage-slot-thumb\">" + renderImage(item.icon, item.slotLabel || item.name) + "</div>"
				+ "<div class=\"mypage-slot-status\">장착 중</div>"
				+ "<div class=\"mypage-slot-power\">P " + formatNumber(item.powerScore) + "</div>"
				+ "<button type=\"button\" data-action=\"unequip-slot\" data-slot=\"" + escapeHtml(slot.slotCode) + "\">장착 해제</button>"
				+ "</div>"
			: "<div class=\"mypage-slot-body\">"
				+ "<div class=\"mypage-slot-thumb\"><span class=\"mypage-empty-copy\">" + escapeHtml(slot.slotLabel) + "</span></div>"
				+ "<div class=\"mypage-slot-status\">비어 있음</div>"
				+ "<div class=\"mypage-item-sub\">클릭 또는 드래그로 장착</div>"
				+ "</div>";

		return "<article class=\"mypage-slot-card" + (state.currentSlot === slot.slotCode ? " is-active" : "") + (item ? " is-filled" : "") + "\" data-action=\"select-slot\" data-slot=\"" + escapeHtml(slot.slotCode) + "\">"
			+ "<div class=\"mypage-slot-header\">"
			+ "<div>"
			+ "<div class=\"mypage-slot-label\">" + escapeHtml(slot.slotLabel) + "</div>"
			+ "<div class=\"mypage-slot-name\">" + (item ? escapeHtml(item.name) : "장착 없음") + "</div>"
			+ "</div>"
			+ "</div>"
			+ body
			+ "</article>";
	}

	function renderSelectedItem() {
		const selectedRoot = root.querySelector('[data-role="selected-item"]');
		const selected = getSelectedItem();

		if (!selectedRoot) {
			return;
		}

		if (!selected) {
			selectedRoot.innerHTML = renderEmptyState("왼쪽 아이템 목록에서 장비를 선택하면 이곳에서 상세 정보와 장착 버튼을 확인할 수 있습니다.");
			return;
		}

		const chips = [];
		if (selected.owned) {
			chips.push("<span class=\"mypage-chip is-owned\">내 구매 아이템</span>");
		}
		if (selected.raceName && selected.raceName !== "전체") {
			chips.push("<span class=\"mypage-chip\">" + escapeHtml(selected.raceName) + "</span>");
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
			+ "<div class=\"mypage-item-description\">" + escapeHtml(selected.description || "장착하면 오른쪽 결과 패널에서 스탯이 즉시 다시 계산됩니다.") + "</div>"
			+ "</div>";
	}

	function renderUserSummary() {
		const summaryRoot = root.querySelector('[data-role="user-summary"]');
		if (!summaryRoot) {
			return;
		}

		const savedCount = Object.keys(buildSavedEquipmentMap()).length;
		const equippedCount = Object.keys(state.equippedItemIds).length;

		summaryRoot.innerHTML = "<div class=\"mypage-user-card\">"
			+ renderUserRow("종족", escapeHtml(state.dashboard.user.race || "마족"))
			+ renderUserRow("보유 골드", "<span class=\"mypage-gold\">" + formatNumber(state.dashboard.user.gold) + "G</span>")
			+ renderUserRow("시뮬레이터 장착", formatNumber(equippedCount) + "칸")
			+ renderUserRow("DB 저장 장비", formatNumber(savedCount) + "칸")
			+ renderUserRow("내 구매 종류", formatNumber((state.dashboard.purchaseVault || []).length) + "개")
			+ "</div>";
	}

	function renderStats() {
		const statsRoot = root.querySelector('[data-role="stats-grid"]');
		const stats = state.simulation.totalStats || emptyStats();

		if (!statsRoot) {
			return;
		}

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
		if (!recommendRoot) {
			return;
		}

		if (state.isRecommendationLoading) {
			recommendRoot.innerHTML = renderEmptyState("현재 장착 상태를 분석해서 성장 우선순위를 계산하는 중입니다.");
			return;
		}

		if (!state.showRecommendations) {
			recommendRoot.innerHTML = renderEmptyState("성장 추천 받기를 누르면 현재 장비 기준으로 가장 효율 좋은 교체 순서를 보여줍니다.");
			return;
		}

		if (!state.recommendations.length) {
			recommendRoot.innerHTML = renderEmptyState("추천할 만한 성장 우선순위를 찾지 못했습니다.");
			return;
		}

		recommendRoot.innerHTML = state.recommendations.map(function (priority) {
			const highlightChips = (priority.deltaHighlights || []).map(function (chip) {
				return "<span class=\"mypage-chip\">" + escapeHtml(chip.label) + " " + escapeHtml(chip.value) + "</span>";
			}).join("");

			return "<article class=\"mypage-recommend-card\">"
				+ "<div class=\"mypage-recommend-meta\">"
				+ "<span class=\"mypage-recommend-tag\">" + escapeHtml(priority.targetScope || priority.priorityGroup) + "</span>"
				+ "<span class=\"mypage-recommend-score\">현재 P " + formatNumber(priority.currentPowerScore) + "</span>"
				+ "</div>"
				+ "<strong>" + escapeHtml(priority.headline) + "</strong>"
				+ "<div class=\"mypage-item-sub\">" + escapeHtml(priority.currentItemName || "빈 슬롯") + " → " + escapeHtml(priority.targetItemName || "대상 장비 없음") + "</div>"
				+ "<div class=\"mypage-item-sub\">목표 P " + formatNumber(priority.benchmarkPowerScore) + " / 차이 +" + formatNumber(priority.gapPowerScore) + "</div>"
				+ "<div class=\"mypage-item-description\">" + escapeHtml(priority.description) + "</div>"
				+ "<div class=\"mypage-item-badges\">" + highlightChips + "</div>"
				+ "<div class=\"mypage-item-sub\">추천 점수 " + formatNumber(priority.recommendationScore) + "</div>"
				+ "</article>";
		}).join("");
	}

	

	

	function renderPurchaseVault() {
		const vaultRoot = root.querySelector('[data-role="purchase-vault"]');
		if (!vaultRoot) {
			return;
		}

		const purchaseVault = state.dashboard.purchaseVault || [];
		if (!purchaseVault.length) {
			vaultRoot.innerHTML = renderEmptyState("아직 구매한 아이템이 없습니다.");
			return;
		}

		vaultRoot.innerHTML = purchaseVault.map(function (item) {
			const raceChip = item.raceName && item.raceName !== "전체"
				? "<span class=\"mypage-chip\">" + escapeHtml(item.raceName) + "</span>"
				: "";

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
				+ "<div class=\"mypage-item-badges\">" + raceChip + "</div>"
				+ "</article>";
		}).join("");
	}

	function updateActionButtons() {
		const equipButton = root.querySelector('[data-action="equip-selected"]');
		if (!equipButton) {
			return;
		}

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

	function findCatalogItem(itemId) {
		return (state.catalog.items || []).find(function (item) {
			return item.id === itemId;
		}) || null;
	}

	function getDraggingItem() {
		return findCatalogItem(state.draggingItemId);
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
		if (!status) {
			return;
		}

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
		if (!status) {
			return;
		}

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

	function formatSignedNumber(value) {
		const numericValue = Number(value || 0);
		if (numericValue > 0) {
			return "+" + formatNumber(numericValue);
		}
		if (numericValue < 0) {
			return "-" + formatNumber(Math.abs(numericValue));
		}
		return formatNumber(0);
	}

	function renderRecommendations() {
		const recommendRoot = root.querySelector('[data-role="recommendations"]');
		const modal = root.querySelector('[data-role="recommend-modal"]');
		if (!recommendRoot || !modal) {
			return;
		}

		syncRecommendationModalState(modal);

		if (state.isRecommendationLoading) {
			recommendRoot.innerHTML = renderEmptyState("현재 장착 상태를 분석해서 추천 결과를 정리하는 중입니다.");
			return;
		}

		if (!state.showRecommendations) {
			recommendRoot.innerHTML = renderEmptyState("성장 추천 받기를 누르면 가장 약한 부위부터 단계적으로 추천 결과를 보여줍니다.");
			return;
		}

		if (!state.recommendations.length) {
			recommendRoot.innerHTML = renderEmptyState("현재 장착 상태에서 추천할 다음 단계 장비를 찾지 못했습니다.");
			return;
		}

		recommendRoot.innerHTML = "<div class=\"mypage-modal-step-list\">"
			+ state.recommendations.slice(0, 3).map(renderRecommendationStepCard).join("")
			+ "</div>";
	}

	function syncRecommendationModalState(modal) {
		modal.hidden = !state.isRecommendationModalOpen;
		modal.setAttribute("aria-hidden", state.isRecommendationModalOpen ? "false" : "true");
		document.body.classList.toggle("has-simulator-modal", state.isRecommendationModalOpen);
	}

	function renderRecommendationStepCard(priority, index) {
		const currentName = priority.currentItemName || ((priority.slotLabel || "슬롯") + " 장비 없음");
		const targetName = priority.targetItemName || "추천 장비 없음";
		const chips = (priority.deltaHighlights || []).slice(0, 4).map(function (chip) {
			return "<span class=\"mypage-chip is-delta\">" + escapeHtml(chip.label) + " " + escapeHtml(chip.value) + "</span>";
		}).join("");
		const tradeSearchUrl = buildTradeSearchUrl(targetName);

		return "<article class=\"mypage-modal-step-card\">"
			+ "<div class=\"mypage-modal-step-head\">"
			+ "<span class=\"mypage-modal-step-badge\">" + escapeHtml(String(index + 1)) + "단계</span>"
			+ "<strong class=\"mypage-modal-step-slot\">" + escapeHtml(priority.slotLabel || "장비") + "</strong>"
			+ "</div>"
			+ "<div class=\"mypage-modal-step-line\">"
			+ "<div class=\"mypage-modal-step-item\">"
			+ "<span class=\"mypage-modal-step-name\">" + escapeHtml(currentName) + "</span>"
			+ renderThumb(priority.currentItemIcon, currentName, "mypage-modal-thumb")
			+ "</div>"
			+ "<div class=\"mypage-modal-step-center\">"
			+ "<span class=\"mypage-modal-step-arrow\">&rarr;</span>"
			+ "<span class=\"mypage-modal-step-powerline\">P " + escapeHtml(formatNumber(priority.currentPowerScore)) + " &rarr; P " + escapeHtml(formatNumber(priority.benchmarkPowerScore)) + "</span>"
			+ "<span class=\"mypage-modal-step-power\">" + escapeHtml(formatSignedNumber(priority.gapPowerScore)) + "</span>"
			+ "</div>"
			+ "<div class=\"mypage-modal-step-item is-target\">"
			+ "<span class=\"mypage-modal-step-name is-target\">" + escapeHtml(targetName) + "</span>"
			+ renderThumb(priority.targetItemIcon, targetName, "mypage-modal-thumb")
			+ "</div>"
			+ "</div>"
			+ "<div class=\"mypage-modal-step-footer\">"
			+ "<div class=\"mypage-item-badges\">" + chips + "</div>"
			+ "<a class=\"mypage-ghost-button mypage-modal-trade-link\" href=\"" + escapeHtml(tradeSearchUrl) + "\">구매하러가기</a>"
			+ "</div>"
			+ "</article>";
	}

	function buildTradeSearchUrl(itemName) {
		return "/trade/items?kw=" + encodeURIComponent(itemName || "");
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
