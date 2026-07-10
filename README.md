# Arclume

**Illuminate your career path.**

## Vision
Arclume is an AI-powered career platform designed to help users discover internships and jobs based on their skills, interests, education, projects, experience, and preferences.

## Current Status: Phase 0.1 (Foundation Stage)
Currently, Arclume is in the foundational setup phase. The project scaffold includes a modular monorepo containing a Spring Boot backend and a Next.js frontend. 

**Note**: Authentication, PostgreSQL integration, job matching, resume processing, AI integration, dashboards, and job data are **not implemented yet** and are planned for future phases.

## Technology Stack
- **Frontend**: Next.js 16.2.x, React, TypeScript, Tailwind CSS
- **Backend**: Java 21, Spring Boot 4.1.0, Maven

## Repository Structure
```text
arclume/
├── backend/          # Spring Boot REST API
├── frontend/         # Next.js web application
├── docs/             # Architectural documentation
├── .editorconfig     # Editor configuration
└── README.md         # Project documentation
```

## Prerequisites
- **Java 21**
- **Node.js** >= 20.9
- **npm** >= 10.x
- **Docker** and **Docker Compose**

## Setup & Running Locally

### 1. Environment Configuration
Copy the environment example file to configure local development values:
```powershell
Copy-Item .env.example .env
```
*Note: Backend environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) are loaded automatically by Spring Boot.*

### 2. Start PostgreSQL Database
We use Docker Compose to run a local PostgreSQL instance.

Start the database:
```powershell
docker compose up -d postgres
```
Inspect container health and logs:
```powershell
docker compose ps
docker compose logs postgres
```

Stop the database (without losing data):
```powershell
docker compose down
```

> [!CAUTION]
> **Destructive Reset**: To intentionally reset the development database and permanently remove local data, run:
> ```powershell
> docker compose down -v
> ```

### 3. Backend Setup
1. Navigate to the `backend/` directory:
   ```bash
   cd backend
   ```
2. Run the application using the Maven wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```
   *On Windows, use `.\mvnw.cmd spring-boot:run`*

The backend will start on `http://localhost:8080`.
**Flyway Migration Ownership**: Flyway automatically manages database schema versions.

**Endpoints**:
- API Health Check: `GET http://localhost:8080/api/v1/health`
- Actuator Health: `GET http://localhost:8080/actuator/health`

### 4. Frontend Setup
1. Navigate to the `frontend/` directory:
   ```bash
   cd frontend
   ```
2. Install dependencies (if not already installed):
   ```bash
   npm install
   ```
3. Start the development server:
   ```bash
   npm run dev
   ```

The frontend will be accessible at `http://localhost:3000`.

## Planned Roadmap
- **Phase 1**: Core Data Modeling & PostgreSQL Integration
- **Phase 2**: Authentication & Authorization (Spring Security)
- **Phase 3**: Job Data Integration (External APIs)
- **Phase 4**: Resume Processing & AI Analysis
- **Phase 5**: Job Matching & Dashboards