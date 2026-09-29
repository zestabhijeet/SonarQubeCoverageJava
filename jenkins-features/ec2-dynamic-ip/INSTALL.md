# Auto-update GitHub webhook + Jenkins URL when the EC2 public IP changes

| File | Goes to | Runs |
|---|---|---|
| `update-github-webhook.sh` | `/usr/local/bin/` | At every boot (systemd) — PATCHes the GitHub webhook to `http://<new-ip>:8080/github-webhook/` |
| `update-github-webhook.service` | `/etc/systemd/system/` | Starts the script after networking is up |
| `set-jenkins-url.groovy` | `$JENKINS_HOME/init.groovy.d/` | At every Jenkins start — sets *Jenkins URL* to `http://<new-ip>:8080/` |

Settings are variables at the top of the script (override via environment): `GITHUB_REPO`, `JENKINS_PORT`, `TOKEN_FILE`.

## 1. Create a GitHub token
GitHub → Settings → Developer settings → **Fine-grained tokens** → Generate new token
- Repository access: *Only select repositories* → `SonarQubeCoverageJava`
- Permissions → Repository → **Webhooks: Read and write**
- Copy the token.

## 2. Install on the EC2 instance (SSH in)
```bash
# tools
sudo apt-get install -y jq curl        # Ubuntu
# sudo dnf install -y jq                # Amazon Linux

# token (readable by root only) — typed at a hidden prompt, so it never lands in shell history
sudo mkdir -p /etc/jenkins-webhook
read -rsp "Paste GitHub token, then press Enter: " T && printf '%s' "$T" | sudo tee /etc/jenkins-webhook/github-token > /dev/null && unset T && echo
sudo chmod 600 /etc/jenkins-webhook/github-token
# check: length should be ~93 (github_pat_…) or 40 (ghp_…), and GitHub should answer HTTP 200
sudo sh -c 'T=$(tr -d "[:space:]" < /etc/jenkins-webhook/github-token); echo "Token length: ${#T}"; curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $T" https://api.github.com/repos/zestabhijeet/SonarQubeCoverageJava/hooks'

# webhook updater
sudo cp update-github-webhook.sh /usr/local/bin/
sudo chmod +x /usr/local/bin/update-github-webhook.sh
sudo cp update-github-webhook.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable update-github-webhook.service

# Jenkins URL updater
sudo mkdir -p /var/lib/jenkins/init.groovy.d
sudo cp set-jenkins-url.groovy /var/lib/jenkins/init.groovy.d/
sudo chown -R jenkins:jenkins /var/lib/jenkins/init.groovy.d
```

## 3. Test now (without rebooting)
```bash
sudo systemctl start update-github-webhook.service
sudo journalctl -u update-github-webhook.service --no-pager | tail -3
#   -> Updated webhook 123456 -> http://<current-ip>:8080/github-webhook/

sudo systemctl restart jenkins
sudo journalctl -u jenkins --no-pager | grep set-jenkins-url
#   -> set-jenkins-url: Jenkins URL is now http://<current-ip>:8080/
```
GitHub → repo → Settings → Webhooks should show the new URL, with a green ping.

## 4. Real test
Stop and start the instance from the EC2 console, wait ~1 minute, then push a commit — the build should start without you touching the webhook.

## Notes
- Only the webhook whose URL ends in `/github-webhook/` is changed; other webhooks are left alone. If none exists it is created.
- Instance metadata must be reachable (default on EC2). IMDSv2 is used.
- The Security Group must still allow inbound TCP 8080.
- The IP you type in the browser also changes — see it in the EC2 console, or in the webhook settings.
