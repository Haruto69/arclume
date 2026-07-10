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

## Setup & Running Locally

### Backend Setup
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

**Endpoints**:
- Health Check: `GET http://localhost:8080/api/v1/health`

### Frontend Setup
1. Navigate to the `frontend/` directory:
   ```bash
   cd frontend
   ```
2. Install dependencies (if not already installed):
   ```bash
   npm install
   ```
3. Copy the environment example file (optional for local defaults):
   ```bash
   cp .env.example .env.local
   ```
4. Start the development server:
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