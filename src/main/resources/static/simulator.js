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
		battle: emptyBattleState(),
		draggingItemId: null,
		isCatalogLoading: false,
		isSimulationLoading: false,
		isRecommendationLoading: false
	};
	let battleNarrationTimer = 0;
	let battleSequenceToken = 0;

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
			await openGrowthRecommendations();
			return;
		}

		if (action === "open-battle-modal") {
			openBattleModal();
			return;
		}

		if (action === "close-battle-modal") {
			closeBattleModal();
			return;
		}

		if (action === "regenerate-battle-opponent") {
			regenerateBattleOpponent();
			return;
		}

		if (action === "start-battle") {
			startBattle();
			return;
		}

		if (action === "show-battle-recommendations") {
			await openGrowthRecommendations({ closeBattleFirst: true });
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

	async function openGrowthRecommendations(options) {
		const config = options || {};
		if (config.closeBattleFirst) {
			closeBattleModal();
		}

		state.showRecommendations = true;
		state.activeRecommendationIndex = 0;
		state.isRecommendationLoading = true;
		openRecommendationModal();
		await loadRecommendations();
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
		if (event.key !== "Escape") {
			return;
		}

		if (state.battle.isModalOpen) {
			closeBattleModal();
			return;
		}

		if (state.isRecommendationModalOpen) {
			closeRecommendationModal();
		}
	}

	function openRecommendationModal() {
		state.isRecommendationModalOpen = true;
		render();
		focusModalDialog('[data-role="recommend-modal"] .mypage-modal-dialog', function () {
			return state.isRecommendationModalOpen;
		});
	}

	function closeRecommendationModal() {
		state.isRecommendationModalOpen = false;
		render();
	}

	function openBattleModal() {
		resetBattleState(true);
		state.isRecommendationModalOpen = false;
		state.battle.opponent = buildBattleOpponent();
		state.battle.progressMessage = "성장 목표형 AI를 생성했습니다. 전투 시작을 눌러 보세요.";
		render();
		focusModalDialog('[data-role="battle-modal-dialog"]', function () {
			return state.battle.isModalOpen;
		});
	}

	function closeBattleModal() {
		resetBattleState(false);
		render();
	}

	function regenerateBattleOpponent() {
		if (state.battle.isRunning) {
			return;
		}

		resetBattleState(true);
		state.battle.opponent = buildBattleOpponent();
		state.battle.progressMessage = "새로운 AI 상대를 생성했습니다. 다시 전투를 시작할 수 있습니다.";
		render();
	}

	function startBattle() {
		if (!state.battle.isModalOpen || state.battle.isRunning) {
			return;
		}

		if (!state.battle.opponent) {
			state.battle.opponent = buildBattleOpponent();
		}

		stopBattleSequence();
		state.battle.isRunning = true;
		state.battle.resultText = "";
		state.battle.analysis = null;
		state.battle.roundsPlayed += 1;
		render();

		const resolution = resolveBattleOutcome(getPlayerBattleProfile(), state.battle.opponent);
		runBattleNarration(buildBattleNarration(state.battle.opponent, resolution), resolution);
	}

	function runBattleNarration(lines, resolution) {
		const sequenceToken = ++battleSequenceToken;
		let index = 0;

		function advance() {
			if (sequenceToken !== battleSequenceToken) {
				return;
			}

			if (index < lines.length) {
				state.battle.progressMessage = lines[index];
				index += 1;
				render();
				battleNarrationTimer = window.setTimeout(advance, index === lines.length ? 820 : 760);
				return;
			}

			battleNarrationTimer = 0;
			state.battle.isRunning = false;
			state.battle.resultText = resolution.resultText;
			state.battle.progressMessage = "결과: " + resolution.resultText;
			state.battle.analysis = buildBattleAnalysis(resolution);
			render();
		}

		advance();
	}

	function resetBattleState(keepModalOpen) {
		stopBattleSequence();
		state.battle = emptyBattleState();
		state.battle.isModalOpen = !!keepModalOpen;
	}

	function stopBattleSequence() {
		battleSequenceToken += 1;
		if (battleNarrationTimer) {
			window.clearTimeout(battleNarrationTimer);
			battleNarrationTimer = 0;
		}
	}

	function focusModalDialog(selector, isOpen) {
		window.setTimeout(function () {
			const dialog = root.querySelector(selector);
			if (dialog && isOpen()) {
				dialog.focus();
			}
		}, 0);
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
		renderBattleModal();
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
		syncBodyModalState();
	}

	function syncBattleModalState(modal) {
		modal.hidden = !state.battle.isModalOpen;
		modal.setAttribute("aria-hidden", state.battle.isModalOpen ? "false" : "true");
		syncBodyModalState();
	}

	function syncBodyModalState() {
		document.body.classList.toggle("has-simulator-modal", state.isRecommendationModalOpen || state.battle.isModalOpen);
	}

	function renderBattleModal() {
		const modal = root.querySelector('[data-role="battle-modal"]');
		if (!modal) {
			return;
		}

		syncBattleModalState(modal);

		const playerRoot = modal.querySelector('[data-role="battle-player"]');
		const opponentRoot = modal.querySelector('[data-role="battle-opponent"]');
		const progressRoot = modal.querySelector('[data-role="battle-progress"]');
		const analysisRoot = modal.querySelector('[data-role="battle-analysis"]');
		const startButton = modal.querySelector('[data-action="start-battle"]');
		const regenerateButton = modal.querySelector('[data-action="regenerate-battle-opponent"]');
		const playerProfile = getPlayerBattleProfile();

		if (playerRoot) {
			playerRoot.innerHTML = renderBattleSideCard(playerProfile, {
				kicker: "PLAYER",
				subtitle: (playerProfile.race || "기본 종족") + " / 현재 시뮬레이션",
				statusLabel: "상태",
				statusText: "현재 세팅 기준"
			});
		}

		if (opponentRoot) {
			opponentRoot.innerHTML = state.battle.opponent
				? renderBattleSideCard(state.battle.opponent, {
					kicker: "AI ENEMY",
					subtitle: state.battle.opponent.typeLabel,
					statusLabel: "상태",
					statusText: state.battle.opponent.statusText
				})
				: renderEmptyState("AI 상대를 생성하면 이 영역에 전투 목표가 표시됩니다.");
		}

		if (progressRoot) {
			progressRoot.innerHTML = renderBattleProgress();
		}

		if (analysisRoot) {
			analysisRoot.innerHTML = renderBattleAnalysisContent();
		}

		if (startButton) {
			startButton.disabled = state.battle.isRunning || !state.battle.opponent;
			startButton.textContent = state.battle.isRunning
				? "전투 진행 중..."
				: (state.battle.analysis ? "다시 전투 시작" : "전투 시작");
		}

		if (regenerateButton) {
			regenerateButton.disabled = state.battle.isRunning;
		}
	}

	function renderBattleSideCard(profile, options) {
		const config = options || {};
		const statRows = [
			renderBattleStatRow("공격", formatAttack(profile.attackMin, profile.attackMax)),
			renderBattleStatRow("방어", formatNumber(profile.defense)),
			renderBattleStatRow("체력", formatNumber(profile.health)),
			renderBattleStatRow("명중", formatNumber(profile.accuracy))
		].join("");

		const hintLine = profile.hintText
			? "<div class=\"mypage-battle-card-hint\">" + escapeHtml(profile.hintText) + "</div>"
			: "";

		return "<div class=\"mypage-battle-card-head\">"
			+ "<div>"
			+ "<span class=\"mypage-kicker\">" + escapeHtml(config.kicker || "BATTLE") + "</span>"
			+ "<strong class=\"mypage-battle-card-name\">" + escapeHtml(profile.displayName) + "</strong>"
			+ "<div class=\"mypage-battle-card-sub\">" + escapeHtml(config.subtitle || "") + "</div>"
			+ "</div>"
			+ "<div class=\"mypage-battle-card-power\">P " + formatNumber(profile.powerDisplay) + "</div>"
			+ "</div>"
			+ "<div class=\"mypage-battle-card-status-row\">"
			+ "<span class=\"mypage-battle-card-status-label\">" + escapeHtml(config.statusLabel || "상태") + "</span>"
			+ "<span class=\"mypage-battle-card-status\">" + escapeHtml(config.statusText || "") + "</span>"
			+ "</div>"
			+ "<div class=\"mypage-battle-card-stats\">"
			+ statRows
			+ "</div>"
			+ hintLine;
	}

	function renderBattleStatRow(label, value) {
		return "<div class=\"mypage-battle-stat-row\">"
			+ "<span>" + escapeHtml(label) + "</span>"
			+ "<strong>" + escapeHtml(value) + "</strong>"
			+ "</div>";
	}

	function renderBattleProgress() {
		if (!state.battle.progressMessage) {
			return renderEmptyState("AI 상대를 생성하면 전투 진행 문구가 이곳에 순차적으로 표시됩니다.");
		}

		return "<div class=\"mypage-battle-progress-line" + (state.battle.isRunning ? " is-running" : "") + "\">"
			+ escapeHtml(state.battle.progressMessage)
			+ "</div>";
	}

	function renderBattleAnalysisContent() {
		if (!state.battle.analysis) {
			return renderEmptyState("전투가 끝나면 승패와 함께 부족한 지점, 성장 방향, 추천 장비 연결을 보여드립니다.");
		}

		const analysis = state.battle.analysis;
		const comparisonRows = analysis.comparisons.map(function (item) {
			return "<div class=\"mypage-battle-compare-row\">"
				+ "<span>" + escapeHtml(item.label) + "</span>"
				+ "<strong class=\"is-" + escapeHtml(item.tone) + "\">" + escapeHtml(item.verdict) + "</strong>"
				+ "</div>";
		}).join("");
		const recommendationLine = analysis.recommendationHeadline
			? "<div class=\"mypage-battle-recommend-copy\">" + escapeHtml(analysis.recommendationHeadline) + "</div>"
			: "";

		return "<div class=\"mypage-battle-analysis-stack\">"
			+ "<div class=\"mypage-battle-result-row\">"
			+ "<span class=\"mypage-battle-result-label\">결과</span>"
			+ "<strong class=\"mypage-battle-result-badge is-" + escapeHtml(analysis.resultTone) + "\">" + escapeHtml(analysis.resultKind) + "</strong>"
			+ "<span class=\"mypage-battle-result-text\">" + escapeHtml(analysis.resultText) + "</span>"
			+ "</div>"
			+ "<div class=\"mypage-battle-compare-list\">"
			+ comparisonRows
			+ "</div>"
			+ "<div class=\"mypage-battle-summary\">"
			+ escapeHtml(analysis.summary)
			+ "</div>"
			+ "<div class=\"mypage-battle-recommend-box\">"
			+ "<div class=\"mypage-battle-recommend-title\">추천 연결</div>"
			+ "<div class=\"mypage-battle-recommend-copy\">" + escapeHtml(analysis.recommendationText) + "</div>"
			+ recommendationLine
			+ "<button type=\"button\" class=\"mypage-ghost-button mypage-battle-link-button\" data-action=\"show-battle-recommendations\">추천 장비 보기</button>"
			+ "</div>"
			+ "</div>";
	}

	function getPlayerBattleProfile() {
		const user = state.dashboard && state.dashboard.user ? state.dashboard.user : {};
		const statSnapshot = buildBattleStatSnapshot(state.simulation ? state.simulation.totalStats : emptyStats(), 320);
		const weakness = resolveBattleWeakness(statSnapshot);

		return Object.assign({}, statSnapshot, {
			displayName: user.username || "도전자",
			race: user.race || "기본 종족",
			hintText: "현재 약점 포인트: " + weakness.label
		});
	}

	function buildBattleOpponent() {
		const player = getPlayerBattleProfile();
		const recommendation = getPrimaryRecommendation();
		const weakness = resolveBattleWeakness(player);
		const typeKey = selectBattleType(weakness, recommendation);
		const profile = battleProfileFor(typeKey);
		const multiplier = randomBetween(1.05, 1.15);
		const statScale = 1 + ((multiplier - 1) * 0.55);
		const basePower = Math.max(player.powerForCalc, 320);
		const attackAverage = Math.round(player.attackAverage * profile.attack * statScale * randomBetween(0.99, 1.04));
		const attackSpread = Math.max(12, Math.round(attackAverage * 0.06));
		const slotHint = recommendation && recommendation.slotLabel
			? recommendation.slotLabel + " 보강이 특히 중요합니다."
			: weakness.label + " 대응형 AI입니다.";

		return {
			typeKey: typeKey,
			typeLabel: profile.label,
			displayName: profile.namePrefix + "-" + String(randomInt(8, 31)).padStart(2, "0"),
			powerDisplay: Math.round(Math.max(player.powerDisplay || 0, basePower * 0.88) * multiplier),
			powerForCalc: basePower * multiplier,
			attackAverage: attackAverage,
			attackMin: Math.max(0, attackAverage - attackSpread),
			attackMax: attackAverage + attackSpread,
			defense: Math.round(player.defense * profile.defense * statScale * randomBetween(0.99, 1.04)),
			health: Math.round(player.health * profile.health * statScale * randomBetween(1.00, 1.05)),
			accuracy: Math.round(player.accuracy * profile.accuracy * statScale * randomBetween(0.99, 1.04)),
			critical: Math.round(player.critical * profile.critical * statScale * randomBetween(0.99, 1.04)),
			statusText: "도전 가능한 상대",
			hintText: profile.nature + " / " + slotHint
		};
	}

	function buildBattleStatSnapshot(stats, fallbackPower) {
		const numericPower = Number(stats && stats.powerScore || 0);
		const powerForCalc = Math.max(numericPower, fallbackPower || 0);
		const attackMin = Number(stats && stats.totalAttackMin || 0);
		const attackMax = Number(stats && stats.totalAttackMax || 0);
		const attackAverage = averageRange(attackMin, attackMax) || Math.max(120, Math.round(powerForCalc * 0.28));
		const defense = Number(stats && stats.totalDefense || 0) || Math.max(90, Math.round(powerForCalc * 0.22));
		const health = Number(stats && stats.totalHealth || 0) || Math.max(500, Math.round(powerForCalc * 1.08));
		const accuracy = Number(stats && stats.totalAccuracy || 0) || Math.max(70, Math.round(powerForCalc * 0.11));
		const critical = Number(stats && stats.totalCritical || 0) || Math.max(45, Math.round(powerForCalc * 0.08));

		return {
			powerDisplay: Math.round(numericPower),
			powerForCalc: powerForCalc,
			attackMin: attackMin || Math.max(0, Math.round(attackAverage * 0.94)),
			attackMax: attackMax || Math.round(attackAverage * 1.06),
			attackAverage: attackAverage,
			defense: defense,
			health: health,
			accuracy: accuracy,
			critical: critical
		};
	}

	function resolveBattleWeakness(profile) {
		const basePower = Math.max(profile.powerForCalc, 1);
		const ratios = [
			{ key: "attack", label: "공격", score: profile.attackAverage / (basePower * 0.28) },
			{ key: "defense", label: "방어", score: profile.defense / (basePower * 0.22) },
			{ key: "health", label: "체력", score: profile.health / (basePower * 1.08) },
			{ key: "accuracy", label: "명중", score: profile.accuracy / (basePower * 0.11) },
			{ key: "critical", label: "치명", score: profile.critical / (basePower * 0.08) }
		];

		ratios.sort(function (a, b) {
			return a.score - b.score;
		});

		return ratios[0];
	}

	function selectBattleType(weakness, recommendation) {
		const slotCode = recommendation && recommendation.slotCode ? recommendation.slotCode : "";
		if (slotCode === "weapon") {
			return "defense";
		}
		if (["guarder", "helmet", "shoulder", "armor", "gloves", "pants", "boots"].indexOf(slotCode) !== -1) {
			return "pressure";
		}
		if (["ring", "earring", "necklace"].indexOf(slotCode) !== -1) {
			return weakness.key === "attack" ? "defense" : "agile";
		}

		if (weakness.key === "attack") {
			return "defense";
		}
		if (weakness.key === "accuracy" || weakness.key === "critical") {
			return "agile";
		}
		if (weakness.key === "defense" || weakness.key === "health") {
			return "pressure";
		}
		return "balance";
	}

	function battleProfileFor(typeKey) {
		const profiles = {
			attack: {
				label: "공격형 AI",
				namePrefix: "Ares",
				nature: "짧은 교전에서 화력을 끌어올립니다.",
				attack: 1.15,
				defense: 1.05,
				health: 1.08,
				accuracy: 1.08,
				critical: 1.12
			},
			defense: {
				label: "방어형 AI",
				namePrefix: "Raven",
				nature: "방어 우위를 바탕으로 빈틈을 기다립니다.",
				attack: 1.07,
				defense: 1.16,
				health: 1.13,
				accuracy: 1.05,
				critical: 1.05
			},
			balance: {
				label: "균형형 AI",
				namePrefix: "Nova",
				nature: "공방 밸런스를 유지하며 압박합니다.",
				attack: 1.10,
				defense: 1.10,
				health: 1.10,
				accuracy: 1.08,
				critical: 1.08
			},
			agile: {
				label: "민첩형 AI",
				namePrefix: "Shadow",
				nature: "정확도와 속도로 흐름을 흔듭니다.",
				attack: 1.09,
				defense: 1.04,
				health: 1.07,
				accuracy: 1.16,
				critical: 1.13
			},
			pressure: {
				label: "압박형 AI",
				namePrefix: "Delta",
				nature: "지속 압박으로 약점을 드러내게 합니다.",
				attack: 1.13,
				defense: 1.08,
				health: 1.12,
				accuracy: 1.10,
				critical: 1.08
			}
		};

		return profiles[typeKey] || profiles.balance;
	}

	function resolveBattleOutcome(player, opponent) {
		const playerInitiative = Math.random() >= 0.46;
		const playerScore = calculateBattleScore(player) * randomBetween(0.97, 1.04) * (playerInitiative ? 1.02 : 0.99);
		const opponentScore = calculateBattleScore(opponent) * randomBetween(0.98, 1.05);
		const didPlayerWin = playerScore >= opponentScore;
		const marginRatio = Math.abs(playerScore - opponentScore) / Math.max(playerScore, opponentScore, 1);
		const isClose = marginRatio < 0.05;

		return {
			player: player,
			opponent: opponent,
			playerInitiative: playerInitiative,
			didPlayerWin: didPlayerWin,
			isClose: isClose,
			resultKind: isClose ? "박빙" : (didPlayerWin ? "승리" : "패배"),
			resultTone: isClose ? "close" : (didPlayerWin ? "win" : "loss"),
			resultText: isClose
				? (didPlayerWin ? "박빙 끝에 승리" : "박빙 끝에 패배")
				: (didPlayerWin ? "승리" : "패배"),
			weakness: resolveBattleWeakness(player),
			recommendation: getPrimaryRecommendation()
		};
	}

	function calculateBattleScore(profile) {
		return (profile.attackAverage * 0.3)
			+ (profile.defense * 0.24)
			+ (profile.health * 0.18)
			+ (profile.accuracy * 0.14)
			+ (profile.critical * 0.08)
			+ (profile.powerForCalc * 0.06);
	}

	function buildBattleNarration(opponent, resolution) {
		const openingLine = resolution.playerInitiative
			? "내가 먼저 움직인다"
			: opponent.displayName + "가 먼저 압박해 온다";
		const counterLine = resolution.playerInitiative
			? opponent.displayName + "가 바로 반격한다"
			: "내가 호흡을 가다듬고 응수한다";
		const pressureLine = battleNarrationLineForType(opponent);
		const momentumLine = resolution.didPlayerWin
			? "내가 다시 몰아붙인다"
			: opponent.displayName + "의 압박이 더 거세진다";
		const finishLine = resolution.didPlayerWin
			? opponent.displayName + "가 흔들리기 시작한다"
			: "마지막 공방에서 내가 버티며 기회를 노린다";

		return [
			"전투가 시작된다",
			openingLine,
			counterLine,
			"치열한 공방이 이어진다",
			pressureLine,
			"잠시 거리를 벌리며 자세를 가다듬는다",
			momentumLine,
			finishLine,
			"승부가 결정된다"
		];
	}

	function battleNarrationLineForType(opponent) {
		if (opponent.typeKey === "attack") {
			return opponent.displayName + "가 강한 공격 흐름을 만든다";
		}
		if (opponent.typeKey === "defense") {
			return opponent.displayName + "가 견고한 자세로 템포를 끊는다";
		}
		if (opponent.typeKey === "agile") {
			return opponent.displayName + "가 빠르게 측면을 파고든다";
		}
		if (opponent.typeKey === "pressure") {
			return opponent.displayName + "가 거리를 좁히며 압박한다";
		}
		return opponent.displayName + "가 균형 있게 공방을 조율한다";
	}

	function buildBattleAnalysis(resolution) {
		const comparisons = [
			buildBattleComparison("공격력", resolution.player.attackAverage, resolution.opponent.attackAverage),
			buildBattleComparison("방어력", resolution.player.defense, resolution.opponent.defense),
			buildBattleComparison("체력", resolution.player.health, resolution.opponent.health)
		];
		const summary = buildBattleSummary(resolution);
		const recommendationHeadline = resolution.recommendation && resolution.recommendation.headline
			? resolution.recommendation.headline
			: "";
		const recommendationText = resolution.recommendation && resolution.recommendation.slotLabel
			? resolution.recommendation.slotLabel + " 부위를 먼저 보강하면 다음 전투의 안정감이 좋아집니다."
			: "성장 추천 결과를 열어 현재 가장 효율 좋은 보강 부위를 확인해 보세요.";

		return {
			resultKind: resolution.resultKind,
			resultTone: resolution.resultTone,
			resultText: resolution.resultText,
			comparisons: comparisons,
			summary: summary,
			recommendationText: recommendationText,
			recommendationHeadline: recommendationHeadline
		};
	}

	function buildBattleComparison(label, playerValue, opponentValue) {
		const baseline = Math.max(playerValue, opponentValue, 1);
		const gapRatio = Math.abs(playerValue - opponentValue) / baseline;
		if (gapRatio < 0.06) {
			return { label: label, verdict: "비슷", tone: "even" };
		}
		if (playerValue > opponentValue) {
			return { label: label, verdict: "내가 우세", tone: "ahead" };
		}
		return { label: label, verdict: "상대 우세", tone: "behind" };
	}

	function buildBattleSummary(resolution) {
		const weakness = resolution.weakness;
		const weaknessMessages = {
			attack: "공격력은 아직 상위 목표를 밀어붙이기에는 조금 부족합니다.",
			defense: "방어력이 낮아 상대의 압박이 들어오면 흐름을 오래 버티기 어렵습니다.",
			health: "체력 여유가 적어 장기전으로 갈수록 불리해지는 구조입니다.",
			accuracy: "명중 안정성이 부족해 선공을 잡아도 흐름이 끊기기 쉽습니다.",
			critical: "마무리 화력이 아쉬워 결정적인 순간의 압박이 부족합니다."
		};
		const baseMessage = weaknessMessages[weakness.key] || "현재 스탯 밸런스를 조금만 다듬어도 승률이 더 안정될 수 있습니다.";

		if (resolution.didPlayerWin && resolution.isClose) {
			return "이번 전투는 버텨냈지만 " + baseMessage.replace("부족합니다.", "부족한 편입니다.");
		}
		if (resolution.didPlayerWin) {
			return "이번 세팅으로도 승리할 수 있지만 " + baseMessage;
		}
		if (resolution.isClose) {
			return "패배하긴 했지만 차이는 크지 않았습니다. " + baseMessage;
		}
		return baseMessage + " 일부 장비 보강 시 승률 향상이 예상됩니다.";
	}

	function getPrimaryRecommendation() {
		return state.recommendations && state.recommendations.length ? state.recommendations[0] : null;
	}

	function emptyBattleState() {
		return {
			isModalOpen: false,
			isRunning: false,
			opponent: null,
			progressMessage: "",
			resultText: "",
			analysis: null,
			roundsPlayed: 0
		};
	}

	function averageRange(min, max) {
		const numericMin = Number(min || 0);
		const numericMax = Number(max || 0);
		if (!numericMin && !numericMax) {
			return 0;
		}
		return (numericMin + numericMax) / 2;
	}

	function randomBetween(min, max) {
		return min + (Math.random() * (max - min));
	}

	function randomInt(min, max) {
		return Math.floor(randomBetween(min, max + 1));
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
