# mMarkoweButy + LM Studio Bionic

This repository is prepared to be opened as a local Bionic coding project.

## What Bionic should use

Use the repository root as the working directory. Bionic's official coding workflow is:
1. Create a Project.
2. Enable `Allow coding`.
3. Choose the root folder of this Git repository.
4. Create the project.

Bionic can then search the repository, edit files, use Git, and run shell commands.

## Local synchronization

Clone the repository locally:

```powershell
git clone https://github.com/maindosne/mMarkoweButy.git C:\mMarkoweButy
cd C:\mMarkoweButy
git status
git pull
```

If the folder already exists:

```powershell
cd C:\mMarkoweButy
git pull
```

Open `C:\mMarkoweButy` as the Bionic working directory.

## Safe first task for Bionic

Before making changes, ask Bionic:

> Inspect the entire mMarkoweButy repository. Do not modify files. Identify the web storefront, Supabase functions/migrations, Vercel routing, Android admin app, GitHub Actions build, and the commands that can safely validate each part. Report unknowns instead of guessing.

## Git synchronization model

GitHub is the shared source of code between this ChatGPT workspace and the local Bionic workspace.

Typical flow:

```text
GitHub main
   ↓ git pull
C:\mMarkoweButy
   ↓
Bionic
   ↓ edits/tests
Git commit + push
   ↓
GitHub
```

Do not assume the GitHub push deploys the web production. The current `web/README.md` explicitly says the Vercel project does not have Git Integration configured.

## MCP / browser automation

MCP is separate from the repository itself. Do not add fake MCP configuration to this repository. Configure MCP in LM Studio/Bionic only after verifying the current LM Studio documentation and the exact installed Bionic version.

For browser automation, use a trusted Playwright MCP installation and verify it locally before granting it write/interaction permissions.

## Secrets

Never put production credentials in this repository. Bionic may access local environment/configuration only when the user has explicitly provided access. Never print secret values in chat or commit them.

## Current production integration documented by the repository

- Vercel project: `markowe-buty`
- Domains: `mmarkowebuty.pl`, `www.mmarkowebuty.pl`
- Supabase Edge Function backend: `markowe-buty`
- Customer auth: `mmarkowebuty-customer-auth`
- Store asset bundle: `mmarkowebuty-shop-bundle`
- Admin bundle: `mmarkowebuty-admin-bundle`
- Public routes include sitemap, robots, merchant feed, storefront assets and `/api/*` rewrites.

Use `web/README.md` and `vercel.json` as the source of truth for these repository-level deployment details.
