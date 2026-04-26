GigShield: AI-Powered Parametric Insurance for Gig Workers
---


##  Problem Overview

India’s gig economy runs on delivery partners who earn on a daily or weekly basis.  
However, their income is highly vulnerable to external disruptions such as:

- Heavy rainfall  
- Severe pollution   
- Sudden curfews or lockdowns   

During such events, they simply **cannot work**, which directly leads to **loss of income**.

Currently, there is **no structured financial protection system** for such scenarios.

---

##  Target Persona

We focus on:

**Food Delivery Partners (Zomato/Swiggy-type workers)**

Why this persona?

- Highly dependent on outdoor mobility  
- Earnings fluctuate daily  
- Most impacted by weather and local disruptions  

---

## Proposed Solution

We propose an **AI-powered parametric insurance platform** that:

- Offers **weekly micro-insurance plans**
- Automatically detects disruption events
- Triggers **instant payouts without manual claims**

> No forms. No waiting. No manual verification.

---

## Some Real-World Scenarios

### Scenario 1: Heavy Rain
- Rainfall exceeds threshold  
- Delivery demand drops or becomes unsafe  
- System detects event → payout triggered  

---

### Scenario 2: Public Event / Traffic Restrictions

- A large public event (e.g., political rally, marathon, festival) causes major road closures  
- Delivery routes are blocked or heavily congested  
- Orders drop or become impossible to complete on time  

---

### Scenario 3: Curfew / Lockdown
- Movement restricted  
- Worker unable to access delivery zones  
- Partial, capped payout triggered  

---

## Application Workflow


## Weekly Premium Model

- Premium Range: ₹15 – ₹30 per week  
- Based on:
  - Location risk  
  - Historical disruption patterns  
  - Predicted future conditions  

### Pricing Logic
- High-risk areas → higher premium  
- Low-risk areas → lower premium  

---

## Coverage Strategy

- Covers **30–40% of expected weekly income**  
- Includes **strict payout caps per week**  

This ensures:
- Affordability for workers  
- Financial sustainability for the platform  

---

## Parametric Triggers

Payouts are triggered based on measurable conditions:

- Rainfall above defined threshold   
- Public Event / Traffic Restrictions
- Curfew or restricted access 

> These eliminate manual claims and reduce fraud significantly.

---

## AI/ML Integration

### 1. Risk-Based Pricing
- Inputs: location, weather history, disruption frequency  
- Output: risk score → determines premium  

### 2. Predictive Risk Modeling
- Identifies high-risk zones  
- Adjusts pricing dynamically  

### 3. Fraud Detection
- GPS mismatch detection  
- Duplicate claim prevention  
- Behavioral anomaly analysis  

###  Integration Approach
- ML models run as a **Python microservice**  
- Backend communicates via **REST APIs**

---
##  System Architecture (Simplified)

- **UI Service**  
  Handles user interaction, dashboards, and policy views (Web interface)

- **Backend Core Service (Node.js + Express)**  
  Manages authentication, policy management, claims processing, event handling, and payment logic

- **AI/ML Service (Python)**  
  Performs risk scoring, premium calculation, and fraud detection via REST APIs

---

## Platform Choice: Web Interface

We chose to build a **web-based platform with a mobile-first design approach**.

### Why Web?

- **Faster Development & Deployment:**  
  A web interface allows rapid prototyping and easier deployment within the given timeline.

- **Cross-Device Accessibility:**  
  Users can access the platform from any device (mobile, tablet, desktop) without requiring installation.

- **Simplified Updates:**  
  Changes and improvements can be deployed instantly without requiring users to update an app.

- **Hackathon Practicality:**  
  A web platform is easier to demonstrate, test, and iterate quickly during development phases.

### Mobile Consideration

Although the platform is web-based, the UI is designed with a **mobile-first approach**, ensuring usability for delivery workers who primarily use smartphones.

> In future iterations, this can be extended into a dedicated mobile application for enhanced user experience.

---
##  Core APIs (Indicative)

- `POST /register`  
- `POST /login`  
- `POST /buy-policy`  
- `GET /risk-score`  
- `POST /trigger-event`  
- `POST /claim`  
- `GET /dashboard`  

---

##  Integration Layer

- Weather APIs (rainfall data)  
- AQI APIs (pollution levels)  
- Curfew/traffic signals (mocked)  
- Payment gateway (Razorpay test mode / mock service)  

---

##  Database Design (Basic Entities)

- Users  
- Policies  
- Claims  
- Events  
- Risk Profiles  

---

##  Tech Stack

- **Backend:** Node.js and Express.js
- **Frontend:** React    
- **Database:** MySQL, MongoDB, Redis
- **Authentication:** JWT  
- **API Communication:** REST APIs  
- **AI/ML:** Python (Scikit-learn)  
- **ML Integration:** REST-based microservice  
- **External APIs:** Weather, AQI  
- **Payments:** Razorpay (Test Mode) / Mock  
- **Deployment:** Docker (optional)  

---

## Development Plan

### Phase 1: Ideation & Design
- Define workflows  
- Finalize triggers and pricing logic  
- Design architecture  

### Phase 2: Core Development
- User authentication  
- Policy management  
- Premium calculation  
- Claim automation  

### Phase 3: Optimization
- Fraud detection system  
- Instant payout simulation  
- Analytics dashboard  

---

##  Risk Handling Strategy

For large-scale disruptions (e.g., lockdowns):

- Treated as **high-severity events**  
- Only **partial payouts** are provided  
- Strict payout caps enforced  
- Future scope: platform co-sharing  

---

##  Dashboard Features

###  Worker View
- Active policy status  
- Earnings protected  
- Claim history  

###  Admin View
- Claim analytics  
- Risk insights  
- Fraud alerts  

---

##  Impact

- Provides financial protection to gig workers  
- Reduces income uncertainty  
- Improves workforce stability  
- Creates a scalable insurance model  

---

##  Scalability & Future Scope

- Expand to:
  - Grocery delivery  
  - E-commerce delivery  

- Future enhancements:
  - Advanced ML models  
  - Real-time integrations  
  - Platform partnerships  
  - Reinsurance layer  

---

##  Final Thought

This solution combines **AI-driven risk modeling**, **parametric triggers**, and **automated payouts** to create a practical financial safety net for gig workers.

> When gig workers are protected, the entire ecosystem becomes more resilient.
