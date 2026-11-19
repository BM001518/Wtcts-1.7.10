plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Working copy is not a git clone, so git-based versioning is disabled (see gradle.properties).
// Provide an explicit version here.
//
// Format: <major>.<minor>.<revision>. Upstream appended -rc<GTNH build> (rc1 naming GTNH 2.9.0-RC-1)
// and raised the leading numbers every round of changes; this fork keeps the leading numbers but drops
// the suffix, so a version names one exact build: 1.0.63-rc1 (upstream) -> 1.1.0.
//
// 1.1.0 is this fork's first release: 1.0.63-rc1 (the upstream commit this fork starts from) plus the
// whole bug-fix pass listed in CHANGELOG.md.
extra["modVersion"] = "1.1.0"

tasks.withType<JavaCompile>().configureEach {
    options.annotationProcessorPath = configurations.annotationProcessor.get()
}

// The magnet card's trouble-shooting trace: the click, the packet, the server-side flip and the
// per-tick pickup decision. Off by default in a release, on for every runClient/runServer so a test
// session always leaves the whole chain in the log.
tasks.withType<JavaExec>().configureEach {
    systemProperty("wtct.debug.magnet", "true")
    systemProperty("wtct.debug.tab", "true")
}
