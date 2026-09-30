# Docker — Pwned Passwords Validator demo on Tomcat

The Maven build in the repo root produces a **library JAR** (`wicket-pwnedpasswords-validator-*.jar`): validator classes only, with no web pages and no `main`, so it cannot run on its own.
`webapp/` adds a small demo web application that uses the validator and is packaged as a **WAR**. The `Dockerfile` builds both and runs the WAR on **Tomcat 9**.

```
Dockerfile          multi-stage: Maven build -> Tomcat 9 runtime (ROOT.war)
.dockerignore       keeps build context small
webapp/             demo web app (packaging: war)
  pom.xml
  src/main/java/.../demo/DemoApplication.java   Wicket application
  src/main/java/.../demo/HomePage.java          password form + PwnedPasswordsValidator
  src/main/resources/.../demo/HomePage.html     page markup
  src/main/webapp/WEB-INF/web.xml               WicketFilter -> DemoApplication
```

> Tomcat **9** is required: Wicket 8 uses `javax.servlet`. Tomcat 10+ moved to `jakarta.servlet` and cannot run this WAR.

## How the image is built

| Stage | Base image | What happens |
|---|---|---|
| 1 — `build` | `maven:3.9-eclipse-temurin-17` | `mvn install` builds the validator JAR, then `mvn -f webapp/pom.xml package` builds `pwned-passwords-demo.war` |
| 2 — runtime | `tomcat:9.0-jdk17-temurin` | Tomcat's sample apps are removed and the WAR is deployed as `ROOT.war`, served at `/` on port 8080 |

Only the Tomcat stage ends up in the final image, so Maven and the source code are not shipped.

## Build and run locally

Run these from the **repository root**, the folder that contains `Dockerfile`, on the Jenkins server or any machine with Docker installed:

```bash
cd ~/Downloads/Devops/SonarQubeCoverageJava     # your clone of the repo
git pull
ls Dockerfile webapp                            # both must be listed

docker build -t zestabhijeet/pwned-passwords-demo:1.0 .
docker run -d -p 8080:8080 --name pwned-demo zestabhijeet/pwned-passwords-demo:1.0
docker logs -f pwned-demo                       # Ctrl+C to stop following
```
The first build takes a few minutes while Maven downloads dependencies; later builds reuse the cache.

The container needs outbound internet access: the validator calls `api.pwnedpasswords.com`.

On the Jenkins EC2 server, port 8080 is already used by Jenkins, so map another port, e.g. `-p 8081:8080`, and allow it in the Security Group.

### What a healthy start looks like

```
INFO [main] ... Starting Servlet engine: [Apache Tomcat/9.0.x]
INFO [main] ... Deploying web application archive [/usr/local/tomcat/webapps/ROOT.war]
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
INFO [main] ... Deployment of web application archive [/usr/local/tomcat/webapps/ROOT.war] has finished in [400] ms
INFO [main] ... Starting ProtocolHandler ["http-nio-8080"]
INFO [main] ... Server startup in [446] milliseconds
```
The `SLF4J` lines are harmless: Wicket's own log messages are just switched off, and the app works normally.

### Test the page

Open **http://localhost:8080/**:

| Enter | Expected result |
|---|---|
| `password123` | Error: the password has appeared in data breaches |
| A long random password | *Good news: this password was not found in any known data breach.* |

## Push to Docker Hub

Create an access token first: Docker Hub → Account settings → Personal access tokens (Read & Write). Use it as the password when logging in.

**Intel/AMD machine, or the EC2 server:**
```bash
docker login -u zestabhijeet
docker push zestabhijeet/pwned-passwords-demo:1.0
docker tag  zestabhijeet/pwned-passwords-demo:1.0 zestabhijeet/pwned-passwords-demo:latest
docker push zestabhijeet/pwned-passwords-demo:latest
```

**Apple Silicon Mac (M1/M2/M3):** a plain `docker build` produces an ARM-only image that Intel servers such as EC2 cannot run. Build for both platforms and push in one step:
```bash
docker login -u zestabhijeet
docker buildx build --platform linux/amd64,linux/arm64 \
  -t zestabhijeet/pwned-passwords-demo:1.0 \
  -t zestabhijeet/pwned-passwords-demo:latest \
  --push .
```
Check at https://hub.docker.com/r/zestabhijeet/pwned-passwords-demo: tags `1.0` and `latest`, with both `linux/amd64` and `linux/arm64` listed.

Anyone can then run it with:
```bash
docker run -d -p 8080:8080 zestabhijeet/pwned-passwords-demo:latest
```

## Useful commands

```bash
docker ps --filter name=pwned-demo           # is it running?
docker logs --tail 50 pwned-demo             # recent log lines
docker stop pwned-demo && docker start pwned-demo
docker rm -f pwned-demo                      # remove the container
docker image ls zestabhijeet/pwned-passwords-demo
```

## Troubleshooting

| Error | Cause | Fix |
|---|---|---|
| `failed to read dockerfile: open Dockerfile: no such file or directory` | `docker build` was run outside the repo folder | `cd ~/Downloads/Devops/SonarQubeCoverageJava` (the folder with `Dockerfile`), then build again |
| `cd: no such file or directory` | Wrong path to the clone | Find it with `find ~ -maxdepth 4 -type d -name SonarQubeCoverageJava` |
| `Unable to find image 'zestabhijeet/pwned-passwords-demo:1.0' locally` followed by a pull error | The build failed or was skipped, so the image does not exist | Fix the build error, rebuild, then `docker run` |
| `Conflict. The container name "/pwned-demo" is already in use` | A container with that name exists from an earlier run | `docker rm -f pwned-demo`, then run again, or just open http://localhost:8080/ if it is already running |
| `Bind for 0.0.0.0:8080 failed: port is already allocated` | Another container or Jenkins is using port 8080 | Remove the old container, or map another port: `-p 8082:8080` |
| Page does not load on EC2 | Security Group does not allow the mapped port | EC2 → Security Group → Inbound rules → allow TCP on the host port (e.g. 8081) |
| Image runs on Mac but fails on EC2 with `exec format error` | ARM image built on Apple Silicon | Rebuild with `docker buildx build --platform linux/amd64,linux/arm64 ... --push` |
| 404 or Tomcat default page | WAR not deployed as ROOT | Check the log shows `Deploying web application archive [.../ROOT.war]`; rebuild with `--no-cache` |
| Container starts, but a password check fails | No outbound internet from the container | Check network or proxy; the app must reach `api.pwnedpasswords.com` |

## Build only the WAR (without Docker)

```bash
mvn -B install -DskipTests                  # library JAR -> local Maven repo
mvn -B -f webapp/pom.xml package            # -> webapp/target/pwned-passwords-demo.war
```
