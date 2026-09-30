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

## Build and run locally

Run these from the repository root, on the Jenkins server or any machine with Docker installed:

```bash
docker build -t zestabhijeet/pwned-passwords-demo:1.0 .
docker run -d -p 8080:8080 --name pwned-demo zestabhijeet/pwned-passwords-demo:1.0
docker logs -f pwned-demo            # wait for "Server startup in [...] milliseconds"
```
Open `http://localhost:8080/`. Submitting `password123` shows it is breached, and a strong random password is accepted.
The container needs outbound internet access: the validator calls `api.pwnedpasswords.com`.

On the Jenkins EC2 server, port 8080 is already used by Jenkins, so map another port, e.g. `-p 8081:8080`, and allow it in the Security Group.

## Push to Docker Hub

```bash
docker login -u zestabhijeet             # enter a Docker Hub access token, not your password
docker push zestabhijeet/pwned-passwords-demo:1.0
docker tag  zestabhijeet/pwned-passwords-demo:1.0 zestabhijeet/pwned-passwords-demo:latest
docker push zestabhijeet/pwned-passwords-demo:latest
```
Create the access token at Docker Hub → Account settings → Personal access tokens (Read & Write).

Anyone can then run it with:
```bash
docker run -d -p 8080:8080 zestabhijeet/pwned-passwords-demo:latest
```

## Build only the WAR (without Docker)

```bash
mvn -B install -DskipTests                  # library JAR -> local Maven repo
mvn -B -f webapp/pom.xml package            # -> webapp/target/pwned-passwords-demo.war
```
