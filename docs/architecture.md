# Arclume Architecture

## Current Architecture (Phase 0.1)

The current architecture is a foundational modular monorepo containing a separated frontend and backend communicating over HTTP.

```text
User browser
     |
     v
Next.js frontend
     |
     | REST over HTTP / JSON
     v
Spring Boot API
```

## Component Boundaries & Ownership

To maintain a clean separation of concerns, the following rules govern what each component owns:

### Next.js (Frontend)
- **Pages**: Routing and user-facing URLs.
- **Layouts**: Global and page-level structural components.
- **Forms and visual interactions**: All user interface states and styling.
- **Presentation state**: Client-side state management for the UI.
- **Calling Spring Boot APIs**: Fetching and mutating data via REST endpoints.

### Spring Boot (Backend)
- **REST API contracts**: Defining the shape and versioning of the JSON API (`/api/v1/...`).
- **Validation**: Enforcing business rules and data integrity on all incoming requests.
- **Business logic**: Core application rules.
- **Authentication and authorization** (in later phases).
- **Database access** (in later phases).
- **External AI and job-provider integrations** (in later phases).

## Future Phases
*Note: The following components will be introduced only in later phases:*
- **PostgreSQL**: Relational database for core data modeling.
- **Spring Security**: For authentication and role-based authorization.
- **External Job APIs**: Fetching job market data.
- **AI Providers**: For resume processing and job matching.
- **Caching**: Performance improvements for data retrieval.
- **Background Processing**: Asynchronous tasks and message queues.
