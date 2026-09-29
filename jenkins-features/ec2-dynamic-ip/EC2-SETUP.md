# Jenkins on EC2 — Setup Walkthrough & Troubleshooting

Step-by-step setup of the `ec2-dynamic-ip` scripts on the Jenkins EC2 server, and fixes for the errors hit along the way.
See [`INSTALL.md`](INSTALL.md) for the install commands and [`../WEBHOOK-TESTING.md`](../WEBHOOK-TESTING.md) for testing the webhook afterwards.

## Setup walkthrough

1. **Connect to the Jenkins server.** Run this **from your Mac** (key file `ec2-07.pem`):
   ```bash
   chmod 400 ~/Downloads/ec2-07.pem
   ssh -i ~/Downloads/ec2-07.pem ubuntu@<current-public-ip>
   ```
   The prompt changes to `ubuntu@ip-172-31-…` when you are on the server. **Do not run `ssh` again from there.** The `.pem` file lives on your Mac, not on the server.
   Alternatively, use the EC2 console → **Connect → EC2 Instance Connect**, which needs no key.

2. **Become root and get the scripts:**
   ```bash
   sudo su -
   apt-get update && apt-get install -y git jq curl
   git clone https://github.com/zestabhijeet/SonarQubeCoverageJava.git
   cd SonarQubeCoverageJava/jenkins-features/ec2-dynamic-ip
   ```

3. **Save the GitHub token.** Use a prompt rather than nano, because pasting into nano over SSH can silently save nothing:
   ```bash
   mkdir -p /etc/jenkins-webhook
   read -rsp "Paste GitHub token, then press Enter: " T && printf '%s' "$T" > /etc/jenkins-webhook/github-token && unset T && echo
   chmod 600 /etc/jenkins-webhook/github-token
   ```
   Use a fine-grained token limited to this repository with only **Webhooks: Read and write**. Never commit it or paste it into chat or tickets.

4. **Check the token before installing:**
   ```bash
   TOKEN=$(tr -d '[:space:]' < /etc/jenkins-webhook/github-token)
   echo "Token length: ${#TOKEN}"          # ~93 for github_pat_…, 40 for ghp_…
   curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" \
     https://api.github.com/repos/zestabhijeet/SonarQubeCoverageJava/hooks   # expect HTTP 200
   ```

5. **Install both scripts.** Follow steps 2–3 of [`INSTALL.md`](INSTALL.md), then check them:
   ```bash
   systemctl start update-github-webhook.service
   journalctl -u update-github-webhook.service --no-pager | grep -E "Updated|Created" | tail -1
   systemctl restart jenkins && sleep 30
   journalctl -u jenkins --no-pager | grep set-jenkins-url
   ```
   Expected: `Created webhook -> …` on the first run and `Updated webhook <id> -> …` afterwards, then `set-jenkins-url: Jenkins URL is now http://<ip>:8080/`.

6. **Keep exactly one Jenkins webhook in GitHub.** If a webhook had been added by hand before the script's first run, GitHub now lists two with the same URL, and every push is delivered twice. Delete one (click it → **Delete webhook**). From then on the script updates the remaining one.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Permission denied (publickey)` | `ssh` was run on the server itself, or with the wrong user/key | Run `ssh` from your Mac; user is `ubuntu` (Ubuntu) or `ec2-user` (Amazon Linux) |
| `Warning: Identity file … not accessible` | Wrong path to `ec2-07.pem` | `find ~ -name "ec2-07.pem"` on your Mac and use that path |
| `update-github-webhook.service` fails with **status=22** | GitHub rejected the API call (curl HTTP error) | Run the token check in step 4 |
| `Token length: 0` or `1` | Token was not saved (nano paste failed) | Save it again with the `read -rsp` command in step 3 |
| `HTTP 401 Bad credentials` | Token wrong, revoked or expired | Create a new token and save it again |
| `HTTP 403 Resource not accessible` | Token lacks the webhook permission | Token → Repository permissions → **Webhooks: Read and write** |
| `HTTP 404 Not Found` | Token not granted this repository | Token → Repository access → add **SonarQubeCoverageJava** |
| Two identical webhooks in GitHub | One manual, one created by the script | Delete one; the script then updates the other |
| `journalctl … tail -1` shows only systemd's "Finished …" line | The script's own line is one line earlier | Use `grep -E "Updated|Created"` as in step 5 |
| `ERR_NGROK_8012` / 502 via ngrok | Tunnel pointed at a port where Jenkins was not running | Not needed on EC2; use the public IP directly |
