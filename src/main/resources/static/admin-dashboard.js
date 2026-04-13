document.addEventListener("DOMContentLoaded", () => {
	const quickNav = document.querySelector("[data-admin-quick-nav]");

	if (!quickNav) {
		return;
	}

	const links = Array.from(quickNav.querySelectorAll("[data-scroll-target]"));
	const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
	let highlightedSection = null;
	let highlightTimerId = null;

	const setActiveLink = (targetId) => {
		links.forEach((link) => {
			const isActive = link.dataset.scrollTarget === targetId;
			link.classList.toggle("is-active", isActive);

			if (isActive) {
				link.setAttribute("aria-current", "true");
				return;
			}

			link.removeAttribute("aria-current");
		});
	};

	const clearHighlight = () => {
		if (highlightedSection) {
			highlightedSection.classList.remove("is-targeted");
			highlightedSection = null;
		}

		if (highlightTimerId) {
			window.clearTimeout(highlightTimerId);
			highlightTimerId = null;
		}
	};

	const highlightTarget = (target) => {
		if (!target || target.id === "admin-top") {
			clearHighlight();
			return;
		}

		const sectionToHighlight = target.closest(".admin-scroll-section");

		if (!sectionToHighlight) {
			return;
		}

		clearHighlight();
		highlightedSection = sectionToHighlight;
		highlightedSection.classList.add("is-targeted");
		highlightTimerId = window.setTimeout(clearHighlight, 1600);
	};

	const getTargetElement = (targetId) => document.getElementById(targetId);

	const getScrollBehavior = () => (prefersReducedMotion.matches ? "auto" : "smooth");

	const scrollToTarget = (targetId) => {
		if (targetId === "admin-top") {
			clearHighlight();
			window.scrollTo({ top: 0, behavior: getScrollBehavior() });
			return;
		}

		const target = getTargetElement(targetId);

		if (!target) {
			return;
		}

		target.scrollIntoView({ behavior: getScrollBehavior(), block: "start" });
		highlightTarget(target);
	};

	const syncFromHash = () => {
		const hash = decodeURIComponent(window.location.hash.replace(/^#/, ""));

		if (!hash) {
			setActiveLink("admin-top");
			return;
		}

		const target = getTargetElement(hash);

		if (!target) {
			setActiveLink("admin-top");
			return;
		}

		setActiveLink(hash);
		highlightTarget(target);
	};

	links.forEach((link) => {
		link.addEventListener("click", (event) => {
			if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
				return;
			}

			const targetId = link.dataset.scrollTarget;

			if (!targetId) {
				return;
			}

			event.preventDefault();
			setActiveLink(targetId);
			scrollToTarget(targetId);

			const href = link.getAttribute("href");

			if (href && window.history.replaceState) {
				window.history.replaceState(null, "", href);
			}
		});
	});

	window.addEventListener("hashchange", syncFromHash);
	window.addEventListener("load", syncFromHash);
	syncFromHash();
});
