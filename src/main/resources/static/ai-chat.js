(() => {
    const aiButton = document.getElementById("angelAIButton");
    const aiChat = document.getElementById("aiChat");
    const aiCloseButton = document.getElementById("aiCloseButton");
    const aiMessages = document.getElementById("aiMessages");
    const aiInput = document.getElementById("aiInput");
    const aiSendButton = document.getElementById("aiSendButton");
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    if (!aiButton || !aiChat || !aiMessages || !aiInput || !aiSendButton) {
        return;
    }

    let loading = false;

    const setOpen = (open) => {
        aiChat.classList.toggle("is-open", open);
        aiButton.setAttribute("aria-expanded", String(open));
        aiChat.setAttribute("aria-hidden", String(!open));
        if (open) {
            aiInput.focus();
        }
    };

    const appendMessage = (role, text, extraClass = "") => {
        const message = document.createElement("div");
        message.className = `ai-message ai-message-${role}${extraClass ? ` ${extraClass}` : ""}`;
        message.textContent = text;
        aiMessages.appendChild(message);
        aiMessages.scrollTop = aiMessages.scrollHeight;
        return message;
    };

    const setLoading = (nextLoading) => {
        loading = nextLoading;
        aiInput.disabled = nextLoading;
        aiSendButton.disabled = nextLoading;
        aiButton.classList.toggle("is-busy", nextLoading);
    };

    const sendAI = async () => {
        const question = aiInput.value.trim();
        if (!question || loading) {
            return;
        }

        setOpen(true);
        appendMessage("user", question);
        aiInput.value = "";
        setLoading(true);
        const thinkingMessage = appendMessage("bot", "관련 페이지를 찾는 중이에요...", "is-thinking");

        try {
            const headers = {
                "Content-Type": "application/json",
                "Accept": "application/json"
            };

            if (csrfToken && csrfHeader) {
                headers[csrfHeader] = csrfToken;
            }

            const response = await fetch("/ai/chat", {
                method: "POST",
                headers,
                credentials: "same-origin",
                body: JSON.stringify({ question })
            });

            const responseText = await response.text();
            let payload = {};

            if (responseText) {
                try {
                    payload = JSON.parse(responseText);
                } catch (error) {
                    payload = {};
                }
            }

            thinkingMessage.remove();

            if (!response.ok) {
                throw new Error(payload.answer || "검색 결과를 가져오지 못했습니다.");
            }

            if (payload.action === "redirect" && payload.redirectUrl) {
                window.location.href = payload.redirectUrl;
                return;
            }

            if (payload.action === "results" && payload.resultPageUrl) {
                window.location.href = payload.resultPageUrl;
                return;
            }

            appendMessage("bot", payload.answer || "검색 결과가 비어 있습니다.");
        } catch (error) {
            thinkingMessage.remove();
            appendMessage("bot", error.message || "문제가 발생했습니다. 잠시 후 다시 시도해주세요.");
        } finally {
            setLoading(false);
            aiInput.focus();
        }
    };

    aiButton.addEventListener("click", () => {
        setOpen(!aiChat.classList.contains("is-open"));
    });

    aiCloseButton?.addEventListener("click", () => {
        setOpen(false);
    });

    aiSendButton.addEventListener("click", sendAI);

    aiInput.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            sendAI();
        }
    });
})();
