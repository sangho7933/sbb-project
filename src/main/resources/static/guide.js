(function () {
	const root = document.getElementById("guidePage");
	if (!root) {
		return;
	}

	const contentRoot = root.querySelector('[data-role="guide-content"]');
	const buttons = Array.from(root.querySelectorAll("[data-guide-step]"));
	const stepBaseUrl = root.dataset.stepBaseUrl || "";
	let currentStep = Number(root.dataset.initialStep || 1);

	function setActiveStep(step) {
		buttons.forEach(function (button) {
			const buttonStep = Number(button.dataset.guideStep);
			const isActive = buttonStep === step;
			button.classList.toggle("is-active", isActive);
			if (isActive) {
				button.setAttribute("aria-current", "page");
			} else {
				button.removeAttribute("aria-current");
			}
		});
	}

	function syncUrl(step) {
		const url = new URL(window.location.href);
		if (step <= 1) {
			url.searchParams.delete("step");
		} else {
			url.searchParams.set("step", String(step));
		}
		window.history.replaceState({ step: step }, "", url);
	}

	async function loadStep(step, updateUrl) {
		if (!stepBaseUrl || !contentRoot) {
			return;
		}

		contentRoot.classList.add("is-loading");

		try {
			const response = await fetch(stepBaseUrl + "/" + step, {
				headers: {
					"X-Requested-With": "XMLHttpRequest"
				}
			});

			if (!response.ok) {
				throw new Error("가이드 내용을 불러오지 못했습니다.");
			}

			const html = await response.text();
			contentRoot.innerHTML = html;
			currentStep = step;
			root.dataset.initialStep = String(step);
			setActiveStep(step);
			if (updateUrl) {
				syncUrl(step);
			}
		} catch (error) {
			contentRoot.innerHTML = "<div class=\"guide-load-error\">가이드 내용을 불러오는 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.</div>";
		} finally {
			contentRoot.classList.remove("is-loading");
		}
	}

	buttons.forEach(function (button) {
		button.addEventListener("click", function (event) {
			const step = Number(button.dataset.guideStep);
			if (!step || step === currentStep) {
				return;
			}

			event.preventDefault();
			loadStep(step, true);
		});
	});

	window.addEventListener("popstate", function () {
		const step = Number(new URL(window.location.href).searchParams.get("step") || 1);
		if (step !== currentStep) {
			loadStep(step, false);
		}
	});

	setActiveStep(currentStep);
})();
