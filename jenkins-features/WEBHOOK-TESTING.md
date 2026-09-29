# Testing the GitHub Webhook

Scenarios to show that the GitHub → Jenkins webhook works, from a basic push to recovery from an outage and an EC2 IP change.
Setup is described in [`README.md`](README.md) (Step 6) and [`ec2-dynamic-ip/EC2-SETUP.md`](ec2-dynamic-ip/EC2-SETUP.md).

Run these from a **git clone** of the repository on your Mac (for example `~/Downloads/Devops/SonarQubeCoverageJava`). A folder unzipped from an archive gives `fatal: not a git repository`. Check with `git status` first.

| # | Scenario | Commands | Expected result |
|---|---|---|---|
| 1 | **Push to master triggers a build** | `git checkout master && git pull`<br>`echo "# webhook test $(date)" >> README.md`<br>`git commit -am "Webhook test: push to master" && git push` | Build starts within seconds; console shows *Started by GitHub push by zestabhijeet*; delivery shows `200` |
| 2 | **A push to another branch does not build** | `git checkout dev && git pull`<br>`git commit --allow-empty -m "Webhook test: dev" && git push`<br>`git checkout master` | Delivery `200`, but **no** build, because the job watches `*/master` only |
| 3 | **A failing test is caught automatically** | `sed -i '' 's/assertEquals(/assertNotEquals(/' src/test/java/de/martinspielmann/wicket/pwnedpasswordsvalidator/StatusTest.java`<br>`git commit -am "Webhook test: failing test" && git push`<br>then `git revert --no-edit HEAD && git push` | First build **fails** in *Build & Unit Test*; the revert push builds **green** again |
| 4 | **Noisy build turns UNSTABLE** | Set `filterlogs('WARNING', 1)` in `jenkins-features/Jenkinsfile`, commit, push; then set it back to `50` | Build ends **UNSTABLE** (yellow) at *Log Filter* |
| 5 | **Secrets stay masked in webhook builds** | Open the console of any push-triggered build | *Credential Masking Demo*: token `****`, dummy secrets `********` |
| 6 | **Jenkins down, then redelivery** | On the server: `systemctl stop jenkins`; push a commit; `systemctl start jenkins`; GitHub → webhook → Recent Deliveries → failed delivery → **Redeliver** | Red ✖ while down; after **Redeliver**, `200` and the missed build runs |
| 7 | **The IP changes, the webhook follows** | EC2 console → **Stop**, then **Start** the instance; wait ~1 min; push a commit | Webhook and Jenkins URL show the new IP; the build starts with no manual change |
| 8 | **Pull request builds** *(advanced)* | Create a **Multibranch Pipeline** job (GitHub branch source, Script Path `jenkins-features/Jenkinsfile`) and tick **Pull requests** in the webhook events | Opening a PR builds it; GitHub shows ✔/✖ on the PR |

**Where to look**

| Location | Shows |
|---|---|
| GitHub → Settings → Webhooks → **Recent Deliveries** | Whether GitHub sent the event, response code, request/response bodies, **Redeliver** |
| Jenkins job → build → **Console Output** | *Started by GitHub push* and every stage |
| Manage Jenkins → **System Log** | Webhook receipts when a delivery arrived but no build started |

Scenarios 1, 3, 6 and 7 together make a complete demo: normal trigger, failure detection, recovery and a changing IP.
