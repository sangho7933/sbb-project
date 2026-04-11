(() => {
    const storageKey = "a2c-faction-theme";
    const defaultTheme = "asmodian";
    const validThemes = ["asmodian", "elyos"];
    const themeState = {
        theme: readTheme()
    };

    const nextThemeMap = {
        asmodian: {
            next: "elyos",
            icon: "😇",
            label: "천족"
        },
        elyos: {
            next: "asmodian",
            icon: "👿",
            label: "마족"
        }
    };

    const toggleButton = document.getElementById("themeToggleButton");
    const iconNode = toggleButton?.querySelector("[data-theme-icon]");
    const labelNode = toggleButton?.querySelector("[data-theme-label]");

    function readTheme() {
        try {
            const savedTheme = localStorage.getItem(storageKey);
            return validThemes.includes(savedTheme) ? savedTheme : defaultTheme;
        } catch (error) {
            return defaultTheme;
        }
    }

    function writeTheme(theme) {
        try {
            localStorage.setItem(storageKey, theme);
        } catch (error) {
        }
    }

    function applyTheme(theme) {
        const safeTheme = validThemes.includes(theme) ? theme : defaultTheme;
        themeState.theme = safeTheme;
        document.documentElement.setAttribute("data-theme", safeTheme);
        document.body.classList.remove(...validThemes);
        document.body.classList.add(safeTheme);
        writeTheme(safeTheme);
        syncToggleButton();
    }

    function syncToggleButton() {
        if (!toggleButton) {
            return;
        }

        const nextTheme = nextThemeMap[themeState.theme];
        if (!nextTheme) {
            return;
        }

        if (iconNode) {
            iconNode.textContent = nextTheme.icon;
        }

        if (labelNode) {
            labelNode.textContent = nextTheme.label;
        }

        const switchLabel = nextTheme.label + " 테마로 전환";
        toggleButton.setAttribute("aria-label", switchLabel);
        toggleButton.setAttribute("title", switchLabel);
    }

    if (document.body) {
        applyTheme(themeState.theme);
    } else {
        document.addEventListener("DOMContentLoaded", () => applyTheme(themeState.theme), { once: true });
    }

    toggleButton?.addEventListener("click", () => {
        const nextTheme = nextThemeMap[themeState.theme]?.next || defaultTheme;
        applyTheme(nextTheme);
    });
})();
