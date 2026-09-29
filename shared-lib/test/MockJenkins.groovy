// Minimal Jenkins pipeline simulator for testing vars/*.groovy without Jenkins.
// Each var file is compiled as a Script whose unknown method calls are recorded
// as pipeline steps; closures passed to wrapper steps are executed.

import org.codehaus.groovy.control.CompilerConfiguration

class MockJenkins {
    List<String> calls = []
    Map currentBuild
    List<String> logLines = []
    String varsDir
    Map<String, Script> loaded = [:]
    boolean skipSteps = false
    String qualityGateStatus = 'OK'
    Map params = [:]

    MockJenkins(String varsDir) {
        this.varsDir = varsDir
        def self = this
        currentBuild = [result: null, currentResult: 'SUCCESS', rawBuild: [getLog: { int n -> self.logLines.takeRight(n) }]]
    }

    Script load(String name) {
        if (loaded[name]) return loaded[name]
        def cc = new CompilerConfiguration()
        cc.scriptBaseClass = StepScript.name
        def shell = new GroovyShell(this.class.classLoader, new Binding(), cc)
        def s = shell.parse(new File(varsDir, "${name}.groovy"))
        ((StepScript) s).setJenkins(this)
        loaded[name] = s
        s
    }

    boolean isVar(String name) { new File(varsDir, "${name}.groovy").exists() }

    def step(String name, Object[] args) {
        Closure body = (args && args[-1] instanceof Closure) ? args[-1] : null
        def plain = body ? args[0..<(args.length - 1)] : (args as List)

        // Declarative structure
        switch (name) {
            case 'pipeline': case 'stages': case 'post': case 'always': case 'script': case 'success':
                return body?.call()
            case 'failure':
                calls << 'post:failure'; return null
            case 'options': case 'agent': case 'tools': case 'triggers': case 'parameters':
                calls << "${name}(${plain.join(', ')})".toString(); body?.call(); return null
            case 'stage':
                skipSteps = false
                calls << "stage:${plain[0]}".toString()
                body?.call(); skipSteps = false; return null
            case 'when':
                body.call()
                if (skipSteps) calls << 'skipped'
                return null
            case 'expression':
                skipSteps = !body.call(); return null
            case 'steps':
                if (!skipSteps) body?.call()
                return null
        }

        if (isVar(name)) {
            return load(name).invokeMethod('call', args)
        }

        String rendered = "${name}(${plain.collect { it instanceof Map ? it.toString() : it }.join(', ')})"
        calls << rendered
        switch (name) {
            case 'error': throw new RuntimeException("error: ${plain[0]}")
            case 'tool': return "/tools/${plain[0].name}"
            case 'waitForQualityGate': return [status: qualityGateStatus]
            case 'sh': logLines << "+ ${plain[0]}".toString(); return 0
            case 'echo': logLines << plain[0].toString(); return null
        }
        return body ? body.call() : name
    }
}

abstract class StepScript extends Script {
    MockJenkins jenkins
    def methodMissing(String name, args) { jenkins.step(name, args as Object[]) }
    def propertyMissing(String name) {
        if (name == 'currentBuild') return jenkins.currentBuild
        if (name == 'scm') return 'SCM'
        if (name == 'params') return jenkins.params
        return name   // e.g. `any` in `agent any`
    }
}
