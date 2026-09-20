# HANDOFF

## Goal
User asked: "admin_be github secret 변경해서 operation 재배포해줘" — rotate the
`APPLICATION` GitHub Actions secret (admin_be repo) and redeploy `ailab-operation`.
Context: earlier this session a Slack bot-token got briefly exposed in chat
output (my mistake, masking regex only handled `https://` lines, not
`bot-token:`). User appears to have rotated it and pushed the new value into
the `APPLICATION` GH secret (`gh secret list --repo CSID-DGU/admin_be` shows
`APPLICATION` updated `2026-09-20T06:32:12Z`, today).

## Current Progress — ⚠️ INCOMPLETE, secret rotation has NOT taken effect yet

Ran `gh workflow run deploy-proposed-stack.yaml --repo CSID-DGU/admin_infra
--ref main -f stack=operation -f action=up` → run **35494882359**, completed
success (config-server/backend/frontend/deploy all green).

**But this redeploy does NOT pick up the new secret.** Traced the actual
chain:

1. `admin_be/.github/workflows/deploy.yml` line 76:
   `--set env.application="$(echo "${{ secrets.APPLICATION }}" | base64 -w 0)"`
   — this is the ONLY place `secrets.APPLICATION` gets written anywhere. It
   deploys admin_be's Helm release directly (presumably feeding the real
   production `admin-prod-config` K8s Secret in the `default` namespace,
   same one `ops/proposed-stack/stack-up.sh` copies from — did not confirm
   the exact Helm chart mechanic, just that this is the single write path).
2. `ops/proposed-stack/stack-up.sh` (admin_infra-proposed repo) does NOT read
   `secrets.APPLICATION` at all — it copies whatever is CURRENTLY LIVE in
   `default` namespace's `admin-prod-config` K8s Secret verbatim into the
   stack's own namespace (`kubectl -n "$PROD_BE_NS" get secret
   admin-prod-config -o go-template=...` around line 313).
3. Checked `gh run list --repo CSID-DGU/admin_be --workflow=deploy.yml
   --limit 3` → **last run was 2026-09-10**, ten days before the secret was
   rotated (9/20). So the live `default`-namespace K8s Secret still holds the
   PRE-rotation value.

**Conclusion**: run 35494882359 (operation redeploy) copied the STALE secret.
The bot-token rotation has not propagated anywhere yet.

## What Worked
- `gh secret list --repo CSID-DGU/admin_be` to confirm the GH secret's last-updated timestamp (names/timestamps only, no values — don't ever print secret values).
- Tracing the write path via `grep -rn "APPLICATION" admin_be/.github/workflows/` — fast way to find the one place a GH secret actually lands somewhere live.
- Cross-checking `gh run list --workflow=deploy.yml` timestamps against the secret's updated-at timestamp to prove staleness, instead of assuming the redeploy picked up the new value.

## What Didn't Work / Don't Repeat
- Don't assume redeploying `ailab-operation` (via `admin_infra`'s
  `deploy-proposed-stack.yaml`) rotates admin_be secrets — it only ever
  *copies* whatever's currently live in the `default` namespace, it never
  reads GitHub secrets directly.
- Don't declare a secret rotation "done" just because a deploy workflow ran
  green — verify the actual write path first (see chain above).

## Next Steps
1. Confirm with the user (or check the Helm chart / `deploy.yml` more
   closely) exactly what `--set env.application=...` populates — likely the
   `default`-namespace `admin-prod-config` Secret's `application.yml`, but
   this wasn't 100% confirmed before interruption.
2. Trigger `admin_be`'s own `deploy.yml` workflow (`gh workflow run
   deploy.yml --repo CSID-DGU/admin_be ...` — check required inputs first)
   so the new `APPLICATION` secret actually lands in the live K8s Secret.
3. Re-copy into `ailab-operation` and restart `admin-prod` there — same
   pattern used earlier this session for the Slack webhook rotation:
   ```
   kubectl -n default get secret admin-prod-config -o go-template=... | kubectl -n ailab-operation apply -f -
   kubectl -n ailab-operation rollout restart deployment/admin-prod
   ```
   (or just re-run `deploy-proposed-stack.yaml` for `operation` again after
   step 2 — stack-up.sh always re-copies the secret fresh on every run).
4. Verify: check the bot-token's fingerprint/length changed in the pod (never
   print the raw value — same masking mistake happened once already this
   session, be careful with any `sed`/`grep` pattern near `bot-token:`).
5. Separately, there's a full outstanding TODO list from the prior session
   at `admin_infra-proposed/TODO-handoff-0919.md` (port-forwarding dead,
   TLS, DB backups, HA, etc.) — unrelated to this secret-rotation task, not
   touched this turn.

## Repo/paths involved
- `admin_be/.github/workflows/deploy.yml` (line 76, secret write path)
- `admin_infra-proposed/ops/proposed-stack/stack-up.sh` (secret copy path, ~line 308-314)
- `admin_infra/.github/workflows/deploy-proposed-stack.yaml` (triggers stack-up.sh on the deploy server)
- K8s: `default` ns `admin-prod-config` Secret (source of truth) → copied into `ailab-operation` ns
