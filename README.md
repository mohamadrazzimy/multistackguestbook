# One Guestbook, Seven Stacks

The same email-and-comment guestbook in seven Docker stacks. Email addresses are saved privately; the public list includes comments and timestamps only. Each stack has its own persistent volume and independent entries.

## Tutorial

- [Complete tutorial](docs/tutorial.md)
- [Standalone HTML tutorial](docs/index.html) — download and open in a browser.

The source files are already in place; you can skip the tutorial’s file-creation and copy steps. Build each stack from its own directory using the tutorial commands, or start all seven with the root Compose file:

```powershell
docker compose up -d --build
```

| Stack | URL |
| --- | --- |
| PHP + Apache | http://localhost:8081 |
| PHP + Nginx | http://localhost:8082 |
| Plain Java | http://localhost:8083 |
| Spring Boot | http://localhost:8084 |
| ASP.NET Core | http://localhost:8085 |
| Node.js / Express | http://localhost:8086 |
| Python / Flask | http://localhost:8087 |

## Verify

With PowerShell 7, run `./scripts/verify.ps1` after all seven services are ready. It checks the home pages, creates a comment in each guestbook, confirms the public API omits emails, and checks invalid-email rejection.

JavaScript, Python, JSON and XML syntax checks were run during preparation. Docker is unavailable in the preparation environment, so full image builds and runtime tests still need to be run.

## Stop and retain entries

```powershell
docker compose down
```

Only use `docker compose down -v` if you intend to permanently delete all saved emails and comments. Root Compose uses its own project volumes; do not mix it with the tutorial’s individual Docker run commands on the same ports.

## Scope

This is a local educational example, bound to loopback. The JSON-lines stores use process or file locks and support one application instance per stack. Keep Gunicorn at one worker. Before public deployment, add a transactional database, HTTPS, request limits, spam prevention, moderation and an email retention/deletion policy. Do not expose the data files publicly.
