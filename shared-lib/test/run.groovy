// Run: groovy -cp test test/run.groovy <vars dir> <jenkinsfiles dir>
import org.codehaus.groovy.control.CompilerConfiguration

def varsDir = args[0]
def jfDir = args[1]
int failures = 0
def check = { String label, boolean ok, detail = '' ->
    println((ok ? 'PASS ' : 'FAIL ') + label + (ok ? '' : "  -> ${detail}"))
    if (!ok) failures++
}
def fresh = { new MockJenkins(varsDir) }

// ---- build.groovy
[Clean: 'sh(mvn clean)', Compile: 'sh(mvn clean compile)', Test: 'sh(mvn clean test)', Install: 'sh(mvn clean install)'].each { action, expected ->
    def j = fresh(); j.load('build').call(action)
    check("build('${action}') runs ${expected}", j.calls == [expected], j.calls)
}
def jb = fresh(); jb.load('build').call('Deploy')
check("build('Deploy') takes no action", jb.calls.isEmpty(), jb.calls)

// ---- repo.groovy
def url = 'https://github.com/zestabhijeet/SonarQubeCoverageJava.git'
def jr = fresh(); jr.load('repo').call(url)
check('repo(url) runs git url', jr.calls == ["git(${url})"], jr.calls)

// ---- mybuild.groovy
def jm = fresh(); jm.load('mybuild').call(url, 'Build')
check("mybuild(url,'Build') = git + mvn clean package", jm.calls == ["git(${url})", 'sh(mvn clean package)'], jm.calls)
def jm2 = fresh(); jm2.load('mybuild').call(url, 'Other')
check("mybuild(url,'Other') = git only", jm2.calls == ["git(${url})"], jm2.calls)

// ---- filterlogs.groovy : UNSTABLE when count > occurrence - 1
[[4, null], [5, 'UNSTABLE'], [6, 'UNSTABLE']].each { n, expected ->
    def j = fresh()
    j.logLines = (1..n).collect { "[WARNING] warning ${it}" } + ['[INFO] BUILD SUCCESS']
    j.load('filterlogs').call('WARNING', 5)
    check("filterlogs('WARNING',5) with ${n} warnings -> ${expected ?: 'unchanged'}", j.currentBuild.result == expected, j.currentBuild.result)
}
def jl = fresh()
jl.logLines = (1..10050).collect { it <= 50 ? 'WARNING old' : 'INFO' }
jl.load('filterlogs').call('WARNING', 1)
check('filterlogs only reads the last 10000 lines', jl.currentBuild.result == null, jl.currentBuild.result)

// ---- consumer Jenkinsfiles (dry run through the library)
def runJenkinsfile = { File f ->
    def j = fresh()
    def src = f.text.replaceAll(/(?m)^@Library\(.*\)\s*_\s*$/, '')
    def cc = new CompilerConfiguration(); cc.scriptBaseClass = StepScript.name
    def s = new GroovyShell(this.class.classLoader, new Binding(), cc).parse(src)
    ((StepScript) s).setJenkins(j)
    s.run()
    j.calls.findAll { !it.startsWith('tools') && !it.startsWith('agent') && !it.startsWith('jdk') && !it.startsWith('maven') }
}
check('PipelineA', runJenkinsfile(new File(jfDir, 'PipelineA.Jenkinsfile')) == ['stage:Build', 'sh(mvn clean install)', 'stage:Filter Logs'])
check('PipelineB', runJenkinsfile(new File(jfDir, 'PipelineB.Jenkinsfile')) == ['stage:Checkout', "git(${url})", 'stage:Test', 'sh(mvn clean test)'])
check('PipelineC', runJenkinsfile(new File(jfDir, 'PipelineC.Jenkinsfile')) == ['stage:Checkout and Build', "git(${url})", 'sh(mvn clean package)'])

println "\n${failures == 0 ? 'ALL TESTS PASSED' : failures + ' FAILURE(S)'}"
System.exit(failures ? 1 : 0)
