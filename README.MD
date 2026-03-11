SpendMind
SpendMind is an offline Android application that converts bank transaction SMS alerts into a personal financial intelligence dashboard.
Instead of manually logging expenses, SpendMind automatically detects transaction messages, extracts financial data, and generates spending analytics, budgets, and savings insights — all completely on-device.


The goal is simple:
Turn raw transaction alerts into clear insights about how money is actually spent.
The Problem
Every digital payment generates a bank SMS notification.
But these messages are just alerts — they don’t help users understand:
where their money goes
how often they spend
which categories dominate spending
whether they are exceeding budgets
Most finance tracking apps require manual entry, which users quickly abandon.

SpendMind solves this by automatically converting bank SMS alerts into structured financial data and behavioral insights.

Key Features
Automatic Transaction Detection
Reads bank transaction SMS alerts and automatically creates structured transactions.
Smart Transaction Parsing
Extracts:
amount
debit / credit type
merchant
category
reference number
account identifier
Spending Analytics

Provides insights such as:
category spending breakdown
monthly spending trends
projected monthly spending
behavioral spending patterns
Budget Tracking
Users can define monthly budgets for categories and get alerts when spending exceeds limits.
Savings Goals
Track financial targets and progress toward goals.
Privacy First
All data is stored locally on the device.
No servers
No bank APIs
No data uploads

Architecture
SpendMind is designed as a data pipeline that converts financial signals into analytics.
Bank SMS
   ↓
SMS Receiver
   ↓
Parser Engine
   ↓
SQLite Database
   ↓
Analytics Engine
   ↓
Web Dashboard (WebView)
Tech Stack
Android Layer
Java
Android SDK
BroadcastReceiver for SMS detection
Data Layer
SQLite database
Custom DatabaseManager
Analytics Layer
Custom analytics engine for financial insights
UI Layer
WebView-based dashboard
HTML / CSS / JavaScript
Chart.js for visualization

Core Components
MainActivity
Handles permissions, WebView initialization, and SMS receivers.
ParserEngine
Extracts financial data from SMS messages using pattern matching.
DatabaseManager
SQLite database layer for transactions, budgets, categories, merchant rules, and savings goals.
AnalyticsEngine
Generates financial insights such as:
totals
category breakdown
spending trends
budget analysis
spending projections
WebAppInterface
Bridge between Android backend and Web UI.
Screens

SpendMind includes multiple views:
Dashboard
Income
Expenses
Savings
Category charts
Spending trends
Transactions
Transaction list
Filters by time period
Edit / manual transactions
Budgets
Category budgets
Progress tracking
Over-limit alerts
Savings
Financial goal tracking
Analytics
Spending patterns
Trend analysis
Settings
Categories
Merchant rules
Budgets
Savings goals

Why This Project Exists
Banks already send users all their financial activity through SMS.
The problem is that this information is unstructured and difficult to interpret.
SpendMind explores how these signals can be transformed into meaningful financial insights about spending behavior.
Understanding financial patterns helps users make better financial decisions.

Installation
Download the APK from the Releases section and install it on an Android device.
Due to Google Play policies regarding SMS permissions, this project is distributed as an open-source APK rather than through the Play Store.
Project Status
Active personal project exploring financial data analysis from SMS-based signals.

Future improvements may include:
duplicate transaction detection
subscription detection
export options
advanced spending predictions

License
MIT License

Author
Ranjith Kumar Dasari.
Electrical & Electronics Engineering
 Software Engineer
