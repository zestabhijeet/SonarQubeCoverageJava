# Jenkins Shared Library — Reusable Pipeline Functions

Library name: `shared-lib`. Test app: https://github.com/zestabhijeet/SonarQubeCoverageJava

```
vars/
  build.groovy       build(mvnaction)                    Clean | Compile | Test | Install
  repo.groovy        repo(reponame)                      git checkout
  mybuild.groovy     mybuild(reponame, mvnaction)        git checkout + 'Build' -> mvn clean package
  filterlogs.groovy  filterlogs(filter_string, occurrence)  UNSTABLE if count > occurrence - 1
```

## 1. Register the library
Manage Jenkins > System > Global Trusted Pipeline Libraries > Add
- Name: `shared-lib` (must match `@Library('shared-lib')` exactly)
- Default version: `master` (or `main`)
- Retrieval method: Modern SCM > Git > Project Repository: this library's repo URL
- Library Path (optional): the folder holding `vars/`, if the library is inside a bigger repo

## 2. Tools (already used by your existing Jenkinsfile)
Manage Jenkins > Tools: JDK `myjava`, Maven `mymaven`.

## 3. Script approval for filterlogs
`currentBuild.rawBuild` is an internal Jenkins API. Registered as a Global Trusted library it needs no approval.
If it is loaded any other way, approve in Manage Jenkins > In-process Script Approval:
`method org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper getRawBuild`

## 4. Consumer pipelines (in SonarQubeCoverageJava/shared-lib-pipelines/)
| Job | Script Path | Calls |
|---|---|---|
| Pipeline A | `shared-lib-pipelines/PipelineA.Jenkinsfile` | `build('Install')`, `filterlogs('WARNING', 5)` |
| Pipeline B | `shared-lib-pipelines/PipelineB.Jenkinsfile` | `repo(url)`, `build('Test')` |
| Pipeline C | `shared-lib-pipelines/PipelineC.Jenkinsfile` | `mybuild(url, 'Build')` |

Create each as New Item > Pipeline > Pipeline script from SCM > Git (SonarQubeCoverageJava, branch `*/master`) > Script Path as above.
Pipeline A has no checkout step; it builds the code Jenkins checked out to read the Jenkinsfile.

## Tests
`test/` runs every function and the three Jenkinsfiles against a mocked Jenkins (no Jenkins needed):
```
groovyc -d build test/MockJenkins.groovy
groovy -cp build:commons-lang-2.6.jar test/run.groovy vars ../shared-lib-pipelines
```
