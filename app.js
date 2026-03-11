(function () {
    var chartInstances = {};
    var categoriesCache = null;
    var currentEditingTx = null;
    var currentEditingBudget = null;
    var currentEditingGoal = null;
    var currentSettingsView = null;

    function selectTab(tabId) {
        var sections = document.querySelectorAll(".tab-section");
        sections.forEach(function (section) {
            section.classList.toggle("active", section.id === "tab-" + tabId);
        });

        var buttons = document.querySelectorAll(".tab-button");
        buttons.forEach(function (btn) {
            btn.classList.toggle("active", btn.getAttribute("data-tab") === tabId);
        });

        if (tabId === "transactions") loadTransactions();
        else if (tabId === "budgets") loadBudgets();
        else if (tabId === "analytics") loadAnalytics();
        else if (tabId === "settings") loadSettings();
    }

    function initTabs() {
        document.querySelectorAll(".tab-button").forEach(function (button) {
            button.addEventListener("click", function () {
                var tabId = button.getAttribute("data-tab");
                selectTab(tabId);
                if (tabId === "savings") {
                    loadSavings();
                }
            });
        });
    }

    // Global hook that native code can call when data changes (e.g. new SMS, history rescan)
    window.SpendMindOnDataChanged = function (reason) {
        try {
            loadDashboard();
            loadTransactions();
            loadBudgets();
            loadSavings();
            loadAnalytics();
        } catch (e) {
            // ignore to avoid breaking the JS bridge
        }
    };

    function renderKPIs(data) {
        var container = document.getElementById("kpi-container");
        if (!container) return;

        var income = data.totalIncome || 0;
        var expenses = data.totalExpenses || 0;
        var savings = income - expenses;

        container.innerHTML =
            '<div class="kpi-card">' +
            '<div class="kpi-icon">↑</div>' +
            '<div class="kpi-label">Income</div>' +
            '<div class="kpi-value">₹' + Number(income).toLocaleString('en-IN') + "</div>" +
            "</div>" +
            '<div class="kpi-card">' +
            '<div class="kpi-icon">↓</div>' +
            '<div class="kpi-label">Expenses</div>' +
            '<div class="kpi-value">₹' + Number(expenses).toLocaleString('en-IN') + "</div>" +
            "</div>" +
            '<div class="kpi-card">' +
            '<div class="kpi-icon">◎</div>' +
            '<div class="kpi-label">Savings</div>' +
            '<div class="kpi-value">₹' + Number(savings).toLocaleString('en-IN') + "</div>" +
            "</div>";
    }

    function destroyChart(id) {
        if (chartInstances[id]) {
            chartInstances[id].destroy();
            chartInstances[id] = null;
        }
    }

    function renderDashboardCharts(data) {
        var ctxPie = document.getElementById("categoryPieChart");
        var ctxLine = document.getElementById("spendingLineChart");

        var labels = [];
        var values = [];
        if (data.categoryBreakdown) {
            Object.keys(data.categoryBreakdown).forEach(function (key) {
                labels.push(key);
                values.push(data.categoryBreakdown[key]);
            });
        }
        if (labels.length === 0) {
            labels = ["No data"];
            values = [0];
        }
        destroyChart("categoryPie");
        chartInstances["categoryPie"] = renderCategoryPieChart(ctxPie, labels, values);

        var trendLabels = [];
        var trendValues = [];
        if (data.spendingTrend && data.spendingTrend.length) {
            data.spendingTrend.forEach(function (point) {
                trendLabels.push(point.label);
                trendValues.push(point.value);
            });
        }
        destroyChart("spendingLine");
        chartInstances["spendingLine"] = renderSpendingLineChart(ctxLine, trendLabels, trendValues);
    }

    function renderRecentTransactions(data) {
        var container = document.getElementById("recent-transactions");
        if (!container) return;

        var items = data.recentTransactions || [];
        if (!items.length) {
            container.innerHTML = "<p>No recent transactions.</p>";
            return;
        }

        container.innerHTML = "";
        items.forEach(function (tx) {
            var row = document.createElement("div");
            row.className = "transaction-item";

            var main = document.createElement("div");
            main.className = "transaction-main";

            var merchant = document.createElement("div");
            merchant.className = "transaction-merchant";
            merchant.textContent = (tx.merchant || "") + " • " + (tx.category || "Uncategorized");

            var meta = document.createElement("div");
            meta.className = "transaction-meta";
            meta.textContent = tx.date;

            main.appendChild(merchant);
            main.appendChild(meta);

            var amount = document.createElement("div");
            amount.className =
                "transaction-amount " + (tx.transactionType === "credit" ? "credit" : "debit");
            amount.textContent = "₹" + Number(tx.amount || 0).toFixed(0);

            row.appendChild(main);
            row.appendChild(amount);
            container.appendChild(row);
        });
    }

    function safeParse(json, fallback) {
        try {
            return JSON.parse(json);
        } catch (e) {
            return fallback;
        }
    }

    function loadDashboard() {
        var filterSelect = document.getElementById("periodFilter");
        var filter = filterSelect ? filterSelect.value : "monthly";
        var payload = JSON.stringify({ filter: filter });

        if (window.SpendMind && typeof window.SpendMind.getDashboardData === "function") {
            var json = window.SpendMind.getDashboardData(payload);
            var data = safeParse(json, {});
            renderKPIs(data);
            renderDashboardCharts(data);
            renderRecentTransactions(data);
            var insightsList = document.getElementById("insights-list");
            if (insightsList && data.insights && data.insights.length) {
                insightsList.innerHTML = "";
                data.insights.forEach(function (text) {
                    var li = document.createElement("li");
                    li.textContent = text;
                    insightsList.appendChild(li);
                });
            }
        }
    }

    function loadTransactions() {
        var filterEl = document.getElementById("transactionFilter");
        var filter = filterEl ? filterEl.value : "monthly";
        var payload = JSON.stringify({ filter: filter });
        var container = document.getElementById("transactions-list");
        if (!container) return;

        if (!window.SpendMind || typeof window.SpendMind.getTransactions !== "function") {
            container.innerHTML = "<p>Unable to load transactions.</p>";
            return;
        }

        var json = window.SpendMind.getTransactions(payload);
        var list = safeParse(json, []);
        container.innerHTML = "";

        if (!list.length) {
            container.innerHTML = "<p>No transactions in this period.</p>";
            return;
        }

        list.forEach(function (tx) {
            var row = document.createElement("div");
            row.className = "transaction-item";
            row.setAttribute("data-id", tx.id);

            var main = document.createElement("div");
            main.className = "transaction-main";
            var merchant = document.createElement("div");
            merchant.className = "transaction-merchant";
            var catName = tx.category || "Uncategorized";
            merchant.innerHTML =
                (tx.merchant || "") +
                ' <span class="cat-badge">' +
                catName +
                "</span>";
            var meta = document.createElement("div");
            meta.className = "transaction-meta";
            meta.textContent = tx.date;
            main.appendChild(merchant);
            main.appendChild(meta);

            var amount = document.createElement("div");
            amount.className = "transaction-amount " + (tx.transactionType === "credit" ? "credit" : "debit");
            amount.textContent = "₹" + Number(tx.amount || 0).toFixed(0);

            var editBtn = document.createElement("button");
            editBtn.className = "secondary-btn";
            editBtn.textContent = "Edit";
            editBtn.style.marginLeft = "8px";
            editBtn.onclick = function () {
                openTransactionModal(tx);
            };

            row.appendChild(main);
            row.appendChild(amount);
            row.appendChild(editBtn);
            container.appendChild(row);
        });
    }

    function fetchCategories() {
        if (categoriesCache) {
            return categoriesCache;
        }
        if (!window.SpendMind || typeof window.SpendMind.getCategories !== "function") {
            categoriesCache = [];
            return categoriesCache;
        }
        var json = window.SpendMind.getCategories();
        categoriesCache = safeParse(json, []);
        return categoriesCache;
    }

    function populateCategoryOptions(selectEl, selectedCategory) {
        if (!selectEl) return;
        var list = fetchCategories();
        selectEl.innerHTML = "";

        var uncategorizedOption = document.createElement("option");
        uncategorizedOption.value = "";
        uncategorizedOption.textContent = "Uncategorized";
        selectEl.appendChild(uncategorizedOption);

        list.forEach(function (c) {
            if (!c || !c.name) return;
            var opt = document.createElement("option");
            opt.value = c.name;
            opt.textContent = c.name;
            selectEl.appendChild(opt);
        });

        if (selectedCategory) {
            selectEl.value = selectedCategory;
        } else {
            selectEl.value = "";
        }
    }

    function todayDateString() {
        var today = new Date();
        var y = today.getFullYear();
        var m = String(today.getMonth() + 1).padStart(2, "0");
        var d = String(today.getDate()).padStart(2, "0");
        return y + "-" + m + "-" + d;
    }

    function openTransactionModal(tx) {
        currentEditingTx = tx || null;
        var modal = document.getElementById("transactionModal");
        if (!modal) return;

        var titleEl = document.getElementById("transactionModalTitle");
        var amountInput = document.getElementById("txAmountInput");
        var typeSelect = document.getElementById("txTypeInput");
        var merchantInput = document.getElementById("txMerchantInput");
        var categorySelect = document.getElementById("txCategoryInput");
        var dateInput = document.getElementById("txDateInput");

        var isEdit = !!(tx && tx.id);
        if (titleEl) {
            titleEl.textContent = isEdit ? "Edit Transaction" : "Add Transaction";
        }

        if (amountInput) {
            amountInput.value = tx && tx.amount != null ? tx.amount : "";
        }
        if (typeSelect) {
            typeSelect.value = (tx && tx.transactionType) || "debit";
        }
        if (merchantInput) {
            merchantInput.value = (tx && tx.merchant) || "";
        }
        if (dateInput) {
            dateInput.value = (tx && tx.date) || todayDateString();
        }
        if (categorySelect) {
            populateCategoryOptions(categorySelect, tx && tx.category ? tx.category : "");
        }

        modal.classList.add("open");
    }

    function closeTransactionModal() {
        var modal = document.getElementById("transactionModal");
        if (modal) {
            modal.classList.remove("open");
        }
        currentEditingTx = null;
    }

    function handleTransactionModalSave() {
        var amountInput = document.getElementById("txAmountInput");
        var typeSelect = document.getElementById("txTypeInput");
        var merchantInput = document.getElementById("txMerchantInput");
        var categorySelect = document.getElementById("txCategoryInput");
        var dateInput = document.getElementById("txDateInput");

        if (!amountInput || !typeSelect || !merchantInput || !categorySelect || !dateInput) {
            return;
        }

        var amount = parseFloat(amountInput.value);
        if (isNaN(amount)) {
            amount = 0;
        }
        var txType = typeSelect.value || "debit";
        var merchant = merchantInput.value || "";
        var category = categorySelect.value || "";
        var date = dateInput.value || todayDateString();

        var payload = {
            amount: amount,
            transactionType: txType,
            merchant: merchant,
            category: category,
            date: date
        };

        if (currentEditingTx && currentEditingTx.id) {
            payload.id = currentEditingTx.id;
        }

        if (window.SpendMind && typeof window.SpendMind.saveTransaction === "function") {
            window.SpendMind.saveTransaction(JSON.stringify(payload));
            closeTransactionModal();
            loadTransactions();
            loadDashboard();
        }
    }

    function loadBudgets() {
        var container = document.getElementById("budgets-list");
        if (!container) return;

        if (!window.SpendMind || typeof window.SpendMind.getBudgets !== "function") {
            container.innerHTML = "<p>Unable to load budgets.</p>";
            return;
        }

        var json = window.SpendMind.getBudgets();
        var list = safeParse(json, []);
        container.innerHTML = "";

        if (!list.length) {
            container.innerHTML = "<p>No budgets set for this month. Use Settings to add budgets.</p>";
            return;
        }

        list.forEach(function (b) {
            var card = document.createElement("div");
            card.className = "budget-card panel";
            var pct = b.monthlyLimit > 0 ? Math.min(100, (b.spent / b.monthlyLimit) * 100) : 0;
            var exceeded = b.monthlyLimit > 0 && b.spent > b.monthlyLimit;

            var header = "<h3>" + (b.category || "") + "</h3>";
            var summary = "<p>Spent ₹" + Number(b.spent).toFixed(0) + " / ₹" + Number(b.monthlyLimit).toFixed(0) + "</p>";
            var barColor = exceeded ? "#ef4444" : "";

            var status = exceeded
                ? '<p style="margin-top:4px;font-size:12px;color:#fca5a5;">Over budget this month</p>'
                : "";

            card.innerHTML =
                header +
                summary +
                '<div class="progress-bar"><div class="progress-bar-fill" style="width:' + pct + '%;' +
                (barColor ? 'background:' + barColor + ';' : '') +
                '"></div></div>' +
                status;
            container.appendChild(card);
        });
    }

    function loadAnalytics() {
        var filterSelect = document.getElementById("periodFilter");
        var filter = filterSelect ? filterSelect.value : "monthly";
        var payload = JSON.stringify({ filter: filter });
        if (!window.SpendMind || typeof window.SpendMind.getDashboardData !== "function") return;

        var json = window.SpendMind.getDashboardData(payload);
        var data = safeParse(json, {});

        var ctxBar = document.getElementById("monthlyBarChart");
        var trendLabels = [];
        var trendValues = [];
        if (data.spendingTrend && data.spendingTrend.length) {
            data.spendingTrend.forEach(function (point) {
                trendLabels.push(point.label);
                trendValues.push(point.value);
            });
        }
        destroyChart("monthlyBar");
        chartInstances["monthlyBar"] = renderMonthlyBarChart(ctxBar, trendLabels, trendValues);

        var budgetCtx = document.getElementById("budgetBarChart");
        if (budgetCtx && window.SpendMind && typeof window.SpendMind.getBudgets === "function") {
            var budgetsJson = window.SpendMind.getBudgets();
            var budgets = safeParse(budgetsJson, []);
            var labels = [];
            var spentData = [];
            var limitData = [];
            var spentColors = [];
            budgets.forEach(function (b) {
                var category = b.category || "Uncategorized";
                var spent = b.spent || 0;
                var limit = b.monthlyLimit || 0;
                labels.push(category);
                spentData.push(spent);
                limitData.push(limit);
                if (limit > 0 && spent > limit) {
                    spentColors.push("#ef4444"); // red if exceeded
                } else {
                    spentColors.push("#f97316"); // default orange
                }
            });
            if (!labels.length) {
                labels.push("No budgets");
                spentData.push(0);
                limitData.push(0);
                spentColors.push("#f97316");
            }
            destroyChart("budgetBar");
            chartInstances["budgetBar"] = renderBudgetBarChart(budgetCtx, labels, spentData, limitData, spentColors);
        }

        var insightsList = document.getElementById("insights-list");
        if (insightsList) {
            insightsList.innerHTML = "";
            if (data.insights && data.insights.length) {
                data.insights.forEach(function (text) {
                    var li = document.createElement("li");
                    li.textContent = text;
                    insightsList.appendChild(li);
                });
            } else {
                insightsList.innerHTML = "<li>No insights yet. Add more transactions.</li>";
            }
        }
    }

    function loadSavings() {
        var container = document.getElementById("savings-list");
        if (!container) return;

        if (!window.SpendMind || typeof window.SpendMind.getSavingsGoals !== "function") {
            container.innerHTML = "<p>Unable to load savings goals.</p>";
            return;
        }

        var json = window.SpendMind.getSavingsGoals();
        var list = safeParse(json, []);
        container.innerHTML = "";

        // Savings tab: progress bars only (no Edit/Delete/Add)
        renderSavingsProgressOnly(container, list);
    }

    function renderSavingsProgressOnly(container, list) {
        if (!list || !list.length) {
            var p = document.createElement("p");
            p.textContent = "No savings goals yet. Add one in Settings → Savings Goals.";
            container.appendChild(p);
            return;
        }
        list.forEach(function (g) {
            var card = document.createElement("div");
            card.className = "panel";
            var pct = g.targetAmount > 0 ? Math.min(100, (g.savedAmount / g.targetAmount) * 100) : 0;
            card.innerHTML =
                "<h3>" + (g.goalName || "") + "</h3>" +
                "<p>Target ₹" + Number(g.targetAmount || 0).toFixed(0) + " · Saved ₹" + Number(g.savedAmount || 0).toFixed(0) + "</p>" +
                '<div class="progress-bar"><div class="progress-bar-fill" style="width:' + pct + '%"></div></div>';
            container.appendChild(card);
        });
    }

    function loadSettings() {
        var hub = document.getElementById("settings-hub");
        var catContainer = document.getElementById("settings-categories");
        var merchantContainer = document.getElementById("settings-merchants");
        var budgetsContainer = document.getElementById("settings-budgets");
        var savingsContainer = document.getElementById("settings-savings");

        if (hub) {
            hub.innerHTML = "";

            var cards = document.createElement("div");
            cards.className = "settings-grid";

            function addHubCard(svgPath, label, description, view) {
                var card = document.createElement("div");
                card.className = "settings-card";

                var iconEl = document.createElement("div");
                iconEl.className = "settings-card-icon";
                iconEl.innerHTML = '<svg viewBox="0 0 24 24">' + svgPath + '</svg>';

                var h = document.createElement("h3");
                h.textContent = label;
                var p = document.createElement("p");
                p.textContent = description;

                card.appendChild(iconEl);
                card.appendChild(h);
                card.appendChild(p);
                card.onclick = function () {
                    showSettingsView(view);
                };
                cards.appendChild(card);
            }

            addHubCard('<path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12zm-6.49-5.84c.41-.73 1.18-1.16 1.63-1.8.48-.68.21-1.94-1.14-1.94-.88 0-1.32.67-1.5 1.23l-1.37-.57C11.51 5.96 12.52 5 13.99 5c1.23 0 2.08.56 2.51 1.26.37.6.58 1.73.01 2.57-.63.93-1.23 1.21-1.56 1.81-.13.24-.18.4-.18 1.18h-1.52c.01-.41-.06-1.08.26-1.66zm-.56 3.79c0-.59.47-1.04 1.05-1.04.59 0 1.04.45 1.04 1.04 0 .58-.44 1.05-1.04 1.05-.58 0-1.05-.47-1.05-1.05z"/>', "Categories", "Manage spending categories", "categories");
            addHubCard('<path d="M20 4H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z"/>', "Merchant Rules", "Map SMS to merchants", "merchants");
            addHubCard('<path d="M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"/>', "Budgets", "Set monthly limits", "budgets");
            addHubCard('<path d="M19.83 7.5l-2.27-2.27c.07-.42.18-.81.32-1.15.08-.18.12-.37.12-.58 0-.83-.67-1.5-1.5-1.5-1.26 0-2.2.83-2.64 1.84C12.86 3.34 11.77 3 10.5 3c-3.86 0-7 3.14-7 7 0 .84.14 1.61.42 2.33L2 14.25V17h2.75l.58-.58c.94.36 1.96.58 3.17.58 1.8 0 3.47-.57 4.85-1.55.24.03.46.05.65.05 2.14 0 3.97-1.14 5.18-2.82l1.32-.66V7.5h-.67zM16 11c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z"/>', "Savings Goals", "Define & track goals", "savings");

            // Optional manual refresh button to rescan SMS / reload data
            var refreshBtn = document.createElement("button");
            refreshBtn.className = "secondary-btn";
            refreshBtn.style.marginTop = "12px";
            refreshBtn.textContent = "Refresh data from SMS";
            refreshBtn.onclick = function () {
                if (window.SpendMind && typeof window.SpendMind.runSmsHistoryImport === "function") {
                    window.SpendMind.runSmsHistoryImport();
                }
            };

            hub.appendChild(cards);
            hub.appendChild(refreshBtn);
        }

        if (catContainer) renderSettingsCategories(catContainer);
        if (merchantContainer) renderSettingsMerchantRules(merchantContainer);
        if (budgetsContainer) renderSettingsBudgets(budgetsContainer);
        if (savingsContainer) renderSettingsSavings(savingsContainer);

        // Default to hub view when first opening Settings
        showSettingsView(currentSettingsView || "hub");
    }

    function showSettingsView(view) {
        currentSettingsView = view;
        var hub = document.getElementById("settings-hub");
        var catContainer = document.getElementById("settings-categories");
        var merchantContainer = document.getElementById("settings-merchants");
        var budgetsContainer = document.getElementById("settings-budgets");
        var savingsContainer = document.getElementById("settings-savings");

        var map = {
            hub: hub,
            categories: catContainer,
            merchants: merchantContainer,
            budgets: budgetsContainer,
            savings: savingsContainer
        };

        Object.keys(map).forEach(function (key) {
            var node = map[key];
            if (!node) return;
            node.style.display = key === view ? "block" : "none";
        });
    }

    function renderSettingsCategories(container) {
        container.innerHTML = "";
        var header = document.createElement("div");
        header.className = "toolbar";
        var title = document.createElement("h3");
        title.textContent = "Categories";
        header.appendChild(title);
        container.appendChild(header);
        if (!window.SpendMind || typeof window.SpendMind.getCategories !== "function") {
            container.innerHTML += "<p>Unable to load categories.</p>";
            return;
        }
        var catJson = window.SpendMind.getCategories();
        var cats = safeParse(catJson, []);
        if (cats.length) {
            var ul = document.createElement("ul");
            cats.forEach(function (c) {
                var li = document.createElement("li");
                var nameSpan = document.createElement("span");
                nameSpan.textContent = c.name || c.id;
                li.appendChild(nameSpan);

                var renameBtn = document.createElement("button");
                renameBtn.className = "secondary-btn";
                renameBtn.style.marginLeft = "8px";
                renameBtn.textContent = "Rename";
                renameBtn.onclick = function () {
                    var nextName = prompt("New name for category", c.name || "");
                    if (!nextName) return;
                    window.SpendMind.updateSettings(JSON.stringify({
                        categories: [{
                            id: c.id,
                            name: nextName,
                            icon: c.icon || "",
                            color: c.color || ""
                        }]
                    }));
                    categoriesCache = null;
                    loadSettings();
                    loadTransactions();
                    loadDashboard();
                    loadBudgets();
                };
                li.appendChild(renameBtn);

                if (c.name !== "Uncategorized") {
                    var deleteBtn = document.createElement("button");
                    deleteBtn.className = "secondary-btn";
                    deleteBtn.style.marginLeft = "6px";
                    deleteBtn.textContent = "Delete";
                    deleteBtn.onclick = function () {
                        if (!confirm("Delete category \"" + (c.name || "") + "\"?")) return;
                        if (window.SpendMind && typeof window.SpendMind.deleteCategory === "function") {
                            window.SpendMind.deleteCategory(c.id);
                            categoriesCache = null;
                            loadSettings();
                            loadTransactions();
                            loadDashboard();
                            loadBudgets();
                        }
                    };
                    li.appendChild(deleteBtn);
                }

                ul.appendChild(li);
            });
            container.appendChild(ul);
        } else {
            var p = document.createElement("p");
            p.textContent = "No categories yet. Add one below.";
            container.appendChild(p);
        }
        var addCatBtn = document.createElement("button");
        addCatBtn.className = "primary-btn";
        addCatBtn.style.marginTop = "8px";
        addCatBtn.textContent = "Add category";
        addCatBtn.onclick = function () {
            var name = prompt("Category name");
            if (name) {
                window.SpendMind.updateSettings(JSON.stringify({
                    categories: [{ name: name, icon: "", color: "" }]
                }));
                categoriesCache = null;
                loadSettings();
            }
        };
        container.appendChild(addCatBtn);
    }

    function renderSettingsMerchantRules(container) {
        container.innerHTML = "";
        var header = document.createElement("div");
        header.className = "toolbar";
        var title = document.createElement("h3");
        title.textContent = "Merchant rules";
        var back = document.createElement("button");
        back.className = "secondary-btn";
        back.textContent = "← Back";
        back.onclick = function () {
            showSettingsView("hub");
        };
        header.appendChild(title);
        header.appendChild(back);
        container.appendChild(header);
        if (!window.SpendMind || typeof window.SpendMind.getMerchantRules !== "function") {
            container.innerHTML += "<p>Unable to load merchant rules.</p>";
            return;
        }
        var ruleJson = window.SpendMind.getMerchantRules();
        var rules = safeParse(ruleJson, []);
        if (rules.length) {
            var ul = document.createElement("ul");
            rules.forEach(function (r) {
                var li = document.createElement("li");
                var textSpan = document.createElement("span");
                textSpan.textContent = (r.keyword || "") + " → " + (r.merchantName || "") + " (" + (r.category || "") + ")";
                li.appendChild(textSpan);
                if (r.id) {
                    var deleteBtn = document.createElement("button");
                    deleteBtn.className = "secondary-btn";
                    deleteBtn.style.marginLeft = "8px";
                    deleteBtn.textContent = "Delete";
                    deleteBtn.onclick = function () {
                        if (!confirm("Delete this merchant rule?")) return;
                        if (window.SpendMind && typeof window.SpendMind.deleteMerchantRule === "function") {
                            window.SpendMind.deleteMerchantRule(Number(r.id));
                            loadSettings();
                        }
                    };
                    li.appendChild(deleteBtn);
                }
                ul.appendChild(li);
            });
            container.appendChild(ul);
        } else {
            var p = document.createElement("p");
            p.textContent = "No merchant rules yet. Add one below.";
            container.appendChild(p);
        }
        var addRuleBtn = document.createElement("button");
        addRuleBtn.className = "primary-btn";
        addRuleBtn.style.marginTop = "8px";
        addRuleBtn.textContent = "Add merchant rule";
        addRuleBtn.onclick = function () {
            var keyword = prompt("Keyword (e.g. redbus)");
            if (!keyword) return;
            var merchantName = prompt("Merchant display name");
            if (!merchantName) return;
            var category = prompt("Category", "Travel");
            if (!category) return;
            window.SpendMind.updateSettings(JSON.stringify({
                merchantRules: [{ keyword: keyword, merchantName: merchantName, category: category }]
            }));
            loadSettings();
        };
        container.appendChild(addRuleBtn);
    }

    function renderSettingsBudgets(container) {
        container.innerHTML = "";
        var header = document.createElement("div");
        header.className = "toolbar";
        var title = document.createElement("h3");
        title.textContent = "Budgets";
        var back = document.createElement("button");
        back.className = "secondary-btn";
        back.textContent = "← Back";
        back.onclick = function () {
            showSettingsView("hub");
        };
        header.appendChild(title);
        header.appendChild(back);
        container.appendChild(header);
        if (!window.SpendMind || typeof window.SpendMind.getBudgets !== "function") {
            container.innerHTML += "<p>Unable to load budgets.</p>";
            return;
        }
        var budgetsJson = window.SpendMind.getBudgets();
        var budgets = safeParse(budgetsJson, []);

        var listWrap = document.createElement("div");
        if (!budgets.length) {
            var empty = document.createElement("p");
            empty.textContent = "No budgets yet. Use the form below to add one.";
            listWrap.appendChild(empty);
        } else {
            var ul = document.createElement("ul");
            budgets.forEach(function (b) {
                var li = document.createElement("li");
                li.textContent =
                    (b.category || "Uncategorized") +
                    " – Limit ₹" + Number(b.monthlyLimit || 0).toFixed(0) +
                    " (Spent ₹" + Number(b.spent || 0).toFixed(0) + ")";
                var editBtn = document.createElement("button");
                editBtn.className = "secondary-btn";
                editBtn.style.marginLeft = "8px";
                editBtn.textContent = "Edit";
                editBtn.onclick = function () {
                    currentEditingBudget = b;
                    populateBudgetForm(container, budgets);
                };
                li.appendChild(editBtn);
                ul.appendChild(li);
            });
            listWrap.appendChild(ul);
        }
        container.appendChild(listWrap);
        populateBudgetForm(container, budgets);
    }

    function populateBudgetForm(container, budgets) {
        var existingForm = document.getElementById("budget-form");
        if (existingForm) {
            existingForm.remove();
        }

        var form = document.createElement("div");
        form.id = "budget-form";
        form.className = "panel";

        var heading = document.createElement("h3");
        heading.textContent = currentEditingBudget ? "Edit budget" : "Add budget";
        form.appendChild(heading);

        function makeField(labelText, inputEl) {
            var wrap = document.createElement("div");
            wrap.className = "modal-field";
            var lab = document.createElement("label");
            lab.textContent = labelText;
            wrap.appendChild(lab);
            wrap.appendChild(inputEl);
            return wrap;
        }

        var categorySelect = document.createElement("select");
        var cats = fetchCategories();
        var uncOpt = document.createElement("option");
        uncOpt.value = "";
        uncOpt.textContent = "Uncategorized";
        categorySelect.appendChild(uncOpt);
        cats.forEach(function (c) {
            if (!c || !c.name) return;
            var opt = document.createElement("option");
            opt.value = c.name;
            opt.textContent = c.name;
            categorySelect.appendChild(opt);
        });
        categorySelect.value = currentEditingBudget && currentEditingBudget.category ? currentEditingBudget.category : "";
        form.appendChild(makeField("Category", categorySelect));

        var limitInput = document.createElement("input");
        limitInput.type = "number";
        limitInput.min = "0";
        limitInput.step = "0.01";
        limitInput.value = currentEditingBudget && currentEditingBudget.monthlyLimit != null ? currentEditingBudget.monthlyLimit : "";
        form.appendChild(makeField("Monthly limit", limitInput));

        var monthInput = document.createElement("input");
        monthInput.type = "number";
        monthInput.min = "1";
        monthInput.max = "12";
        var now = new Date();
        monthInput.value = currentEditingBudget && currentEditingBudget.month ? currentEditingBudget.month : (now.getMonth() + 1);
        form.appendChild(makeField("Month (1-12)", monthInput));

        var yearInput = document.createElement("input");
        yearInput.type = "number";
        yearInput.min = "2000";
        yearInput.max = "2100";
        yearInput.value = currentEditingBudget && currentEditingBudget.year ? currentEditingBudget.year : now.getFullYear();
        form.appendChild(makeField("Year", yearInput));

        var actions = document.createElement("div");
        actions.className = "modal-actions";
        var clearBtn = document.createElement("button");
        clearBtn.type = "button";
        clearBtn.className = "secondary-btn";
        clearBtn.textContent = "Clear";
        clearBtn.onclick = function () {
            currentEditingBudget = null;
            populateBudgetForm(container, budgets);
        };
        var saveBtn = document.createElement("button");
        saveBtn.type = "button";
        saveBtn.className = "primary-btn";
        saveBtn.textContent = currentEditingBudget ? "Update budget" : "Save budget";
        saveBtn.onclick = function () {
            var category = categorySelect.value || "";
            var monthlyLimit = parseFloat(limitInput.value);
            if (isNaN(monthlyLimit)) monthlyLimit = 0;
            var month = parseInt(monthInput.value, 10);
            if (!month || month < 1 || month > 12) month = now.getMonth() + 1;
            var year = parseInt(yearInput.value, 10);
            if (!year) year = now.getFullYear();

            var payload = {
                budgets: [{
                    id: currentEditingBudget && currentEditingBudget.id ? currentEditingBudget.id : null,
                    category: category || "Uncategorized",
                    monthlyLimit: monthlyLimit,
                    spent: currentEditingBudget && currentEditingBudget.spent != null ? currentEditingBudget.spent : 0,
                    month: month,
                    year: year
                }]
            };

            if (window.SpendMind && typeof window.SpendMind.updateSettings === "function") {
                window.SpendMind.updateSettings(JSON.stringify(payload));
                currentEditingBudget = null;
                loadSettings();
                loadBudgets();
                loadAnalytics();
            }
        };
        actions.appendChild(clearBtn);
        actions.appendChild(saveBtn);
        form.appendChild(actions);

        container.appendChild(form);
    }

    function renderSettingsSavings(container) {
        container.innerHTML = "";
        var header = document.createElement("div");
        header.className = "toolbar";
        var title = document.createElement("h3");
        title.textContent = "Savings goals";
        var back = document.createElement("button");
        back.className = "secondary-btn";
        back.textContent = "← Back";
        back.onclick = function () {
            showSettingsView("hub");
        };
        header.appendChild(title);
        header.appendChild(back);
        container.appendChild(header);

        if (!window.SpendMind || typeof window.SpendMind.getSavingsGoals !== "function") {
            container.innerHTML += "<p>Unable to load savings goals.</p>";
            return;
        }

        var json = window.SpendMind.getSavingsGoals();
        var list = safeParse(json, []);

        renderSavingsManager(container, list);
    }

    function renderSavingsManager(container, list) {
        var existingManager = document.getElementById("savings-manager");
        if (existingManager) {
            existingManager.remove();
        }

        var manager = document.createElement("div");
        manager.id = "savings-manager";
        manager.className = "panel";

        var hasGoals = list && list.length;
        if (!hasGoals) {
            var empty = document.createElement("p");
            empty.textContent = "No savings goals yet. Add your first goal below.";
            manager.appendChild(empty);
        } else {
            var ul = document.createElement("ul");
            list.forEach(function (g) {
                var li = document.createElement("li");
                li.textContent =
                    (g.goalName || "") +
                    " – Target ₹" + Number(g.targetAmount || 0).toFixed(0) +
                    " · Saved ₹" + Number(g.savedAmount || 0).toFixed(0);

                var editBtn = document.createElement("button");
                editBtn.className = "secondary-btn";
                editBtn.style.marginLeft = "8px";
                editBtn.textContent = "Edit";
                editBtn.onclick = function () {
                    currentEditingGoal = g;
                    renderSavingsForm(container, list);
                };
                li.appendChild(editBtn);

                if (window.SpendMind && typeof window.SpendMind.deleteSavingsGoal === "function") {
                    var deleteBtn = document.createElement("button");
                    deleteBtn.className = "secondary-btn";
                    deleteBtn.style.marginLeft = "6px";
                    deleteBtn.textContent = "Delete";
                    deleteBtn.onclick = function () {
                        if (!confirm("Delete goal \"" + (g.goalName || "") + "\"?")) return;
                        window.SpendMind.deleteSavingsGoal(g.id);
                        if (typeof loadSavings === "function") {
                            loadSavings();
                        }
                        loadSettings();
                    };
                    li.appendChild(deleteBtn);
                }

                ul.appendChild(li);
            });
            manager.appendChild(ul);
        }

        var addBtn = document.createElement("button");
        addBtn.className = "primary-btn";
        addBtn.style.marginTop = "8px";
        addBtn.textContent = "Add savings goal";
        addBtn.onclick = function () {
            currentEditingGoal = null;
            renderSavingsForm(container, list || []);
        };
        manager.appendChild(addBtn);

        container.appendChild(manager);
    }

    function renderSavingsForm(container, list) {
        var existingForm = document.getElementById("savings-form");
        if (existingForm) {
            existingForm.remove();
        }

        var form = document.createElement("div");
        form.id = "savings-form";
        form.className = "panel";

        var heading = document.createElement("h3");
        heading.textContent = currentEditingGoal && currentEditingGoal.id ? "Edit goal" : "Add goal";
        form.appendChild(heading);

        function makeField(labelText, inputEl) {
            var wrap = document.createElement("div");
            wrap.className = "modal-field";
            var lab = document.createElement("label");
            lab.textContent = labelText;
            wrap.appendChild(lab);
            wrap.appendChild(inputEl);
            return wrap;
        }

        var nameInput = document.createElement("input");
        nameInput.type = "text";
        nameInput.placeholder = "Goal name (e.g. Emergency fund)";
        nameInput.value = currentEditingGoal && currentEditingGoal.goalName ? currentEditingGoal.goalName : "";
        form.appendChild(makeField("Goal name", nameInput));

        var targetInput = document.createElement("input");
        targetInput.type = "number";
        targetInput.min = "0";
        targetInput.step = "0.01";
        targetInput.value = currentEditingGoal && currentEditingGoal.targetAmount != null ? currentEditingGoal.targetAmount : "";
        form.appendChild(makeField("Target amount", targetInput));

        var savedInput = document.createElement("input");
        savedInput.type = "number";
        savedInput.min = "0";
        savedInput.step = "0.01";
        savedInput.value = currentEditingGoal && currentEditingGoal.savedAmount != null ? currentEditingGoal.savedAmount : "";
        form.appendChild(makeField("Already saved", savedInput));

        var deadlineInput = document.createElement("input");
        deadlineInput.type = "date";
        deadlineInput.value = currentEditingGoal && currentEditingGoal.deadline ? currentEditingGoal.deadline : "";
        form.appendChild(makeField("Deadline", deadlineInput));

        var actions = document.createElement("div");
        actions.className = "modal-actions";

        var cancelBtn = document.createElement("button");
        cancelBtn.type = "button";
        cancelBtn.className = "secondary-btn";
        cancelBtn.textContent = "Cancel";
        cancelBtn.onclick = function () {
            currentEditingGoal = null;
            // Re-render manager view without the form
            if (container.id === "savings-list") {
                loadSavings();
            } else {
                loadSettings();
            }
        };

        var saveBtn = document.createElement("button");
        saveBtn.type = "button";
        saveBtn.className = "primary-btn";
        saveBtn.textContent = currentEditingGoal && currentEditingGoal.id ? "Update goal" : "Save goal";
        saveBtn.onclick = function () {
            var goalName = nameInput.value || "";
            var targetAmount = parseFloat(targetInput.value);
            if (isNaN(targetAmount)) targetAmount = 0;
            var savedAmount = parseFloat(savedInput.value);
            if (isNaN(savedAmount)) savedAmount = 0;
            var deadline = deadlineInput.value || "";

            var goalPayload = {
                id: currentEditingGoal && currentEditingGoal.id ? currentEditingGoal.id : null,
                goalName: goalName,
                targetAmount: targetAmount,
                savedAmount: savedAmount,
                deadline: deadline
            };

            if (window.SpendMind && typeof window.SpendMind.updateSettings === "function") {
                window.SpendMind.updateSettings(JSON.stringify({
                    savingsGoals: [goalPayload]
                }));
                currentEditingGoal = null;
                loadSavings();
                loadSettings();
            }
        };

        actions.appendChild(cancelBtn);
        actions.appendChild(saveBtn);
        form.appendChild(actions);

        container.appendChild(form);
    }

    document.addEventListener("DOMContentLoaded", function () {
        initTabs();
        loadDashboard();

        var periodFilter = document.getElementById("periodFilter");
        if (periodFilter) {
            periodFilter.addEventListener("change", loadDashboard);
        }

        var transactionFilter = document.getElementById("transactionFilter");
        if (transactionFilter) {
            transactionFilter.addEventListener("change", loadTransactions);
        }

        var addTransactionBtn = document.getElementById("addTransactionBtn");
        if (addTransactionBtn) {
            addTransactionBtn.addEventListener("click", function () {
                openTransactionModal(null);
            });
        }

        var modalCancel = document.getElementById("transactionModalCancel");
        if (modalCancel) {
            modalCancel.addEventListener("click", function () {
                closeTransactionModal();
            });
        }

        var modalSave = document.getElementById("transactionModalSave");
        if (modalSave) {
            modalSave.addEventListener("click", function () {
                handleTransactionModalSave();
            });
        }

        var modalBackdrop = document.querySelector("#transactionModal .modal-backdrop");
        if (modalBackdrop) {
            modalBackdrop.addEventListener("click", function () {
                closeTransactionModal();
            });
        }
    });
})();
