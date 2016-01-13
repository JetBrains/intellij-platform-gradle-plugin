package org.jetbrains.intellij

class DebugIdeaTask extends RunIdeaTask {

    public static String NAME = "debugIdea"

    @Override
    List<String> getJvmArgs() {
        def result = super.getJvmArgs()
        result += '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005';
        return result
    }
}
